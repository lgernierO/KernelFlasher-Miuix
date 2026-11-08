package com.github.curiousnom.kernelflasher.ui.screens.slot

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.github.curiousnom.kernelflasher.common.PartitionUtil
import com.github.curiousnom.kernelflasher.common.extensions.ByteArray.toHex
import com.github.curiousnom.kernelflasher.common.extensions.ExtendedFile.inputStream
import com.github.curiousnom.kernelflasher.common.extensions.ExtendedFile.outputStream
import com.github.curiousnom.kernelflasher.common.types.backups.Backup
import com.github.curiousnom.kernelflasher.common.types.partitions.Partitions
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile

class SlotViewModel(
    context: Context,
    private val fileSystemManager: FileSystemManager,
    private val navController: NavController,
    private val _isRefreshing: MutableState<Boolean>,
    val isActive: Boolean,
    val slotSuffix: String,
    private val boot: File,
    private val initBoot: File?,
    private val _backups: MutableMap<String, Backup>
) : ViewModel() {
    companion object {
        const val TAG: String = "KernelFlasher/SlotState"
    }

    private var _sha1: String? = null
    var kernelVersion: String? = null
    var hasVendorDlkm: Boolean = false
    var isVendorDlkmMapped: Boolean = false
    var isVendorDlkmMounted: Boolean = false
    private val _flashOutput: SnapshotStateList<String> = mutableStateListOf()
    private val _wasFlashSuccess: MutableState<Boolean?> = mutableStateOf(null)
    private val _backupPartitions: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    private var wasSlotReset: Boolean = false
    private var originalSlotSuffix: String? = null
    private var flashUri: Uri? = null
    private var flashFilename: String? = null
    private val hashAlgorithm: String = "SHA-256"
    private var inInit = true
    private var _error: String? = null

    val sha1: String
        get() = _sha1!!
    val flashOutput: List<String>
        get() = _flashOutput
    val uiPrintedOutput: List<String>
        get() = _flashOutput.filter { it.startsWith("ui_print") }.map{ it.substring("ui_print".length + 1) }
    val wasFlashSuccess: Boolean?
        get() = _wasFlashSuccess.value
    val backupPartitions: MutableMap<String, Boolean>
        get() = _backupPartitions
    val isRefreshing: Boolean
        get() = _isRefreshing.value
    val hasError: Boolean
        get() = _error != null
    val error: String
        get() = _error!!

    init {
        refresh(context)
    }

    fun refresh(context: Context) {
        if (!isActive) {
            inInit = true
        }

        val filesDir = context.filesDir
        val magiskboot = File(filesDir, "magiskboot")
        Shell.cmd("cd $filesDir && $magiskboot unpack $boot").exec()
        if (initBoot != null) {
            Shell.cmd("cd $filesDir && $magiskboot unpack $initBoot").exec()
        }

        val ramdisk = File(filesDir, "ramdisk.cpio")
        val kernel = File(filesDir, "kernel")

        var vendorDlkm = PartitionUtil.findPartitionBlockDevice(context, "vendor_dlkm", slotSuffix)
        hasVendorDlkm = vendorDlkm != null
        if (hasVendorDlkm) {
            isVendorDlkmMapped = vendorDlkm?.exists() == true
            if (isVendorDlkmMapped) {
                isVendorDlkmMounted = isPartitionMounted(vendorDlkm!!)
                if (!isVendorDlkmMounted) {
                    vendorDlkm = fileSystemManager.getFile("/dev/block/mapper/vendor_dlkm-verity")
                    isVendorDlkmMounted = isPartitionMounted(vendorDlkm)
                }
            } else {
                isVendorDlkmMounted = false
            }
        }

        if (ramdisk.exists()) {
            when (Shell.cmd("cd $filesDir && $magiskboot cpio ramdisk.cpio test").exec().code) {
                0 -> _sha1 = Shell.cmd("$magiskboot sha1 $boot").exec().out.firstOrNull()
                1 -> _sha1 = Shell.cmd("cd $filesDir && $magiskboot cpio ramdisk.cpio sha1").exec().out.firstOrNull()
                else -> log(context, "Invalid ramdisk in boot.img", shouldThrow = true)
            }
        } else if (kernel.exists()) {
            _sha1 = Shell.cmd("$magiskboot sha1 $boot").exec().out.firstOrNull()
        } else {
            log(context, "Invalid boot.img, no ramdisk or kernel found", shouldThrow = true)
        }
        Shell.cmd("cd $filesDir && $magiskboot cleanup").exec()

        PartitionUtil.AvailablePartitions.forEach { partitionName ->
            _backupPartitions[partitionName] = true
        }

        kernelVersion = null
        inInit = false
    }

    private fun launch(block: suspend () -> Unit): Job {
        if (_isRefreshing.value) return Job().apply { complete() }
        _isRefreshing.value = true
        return viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (exception: CancellationException) {
                throw exception
            } catch (e: Exception) {
                withContext (Dispatchers.Main) {
                    Log.e(TAG, e.message, e)
                    navController.navigate("error/${Uri.encode(e.message ?: "Operation failed")}") {
                        popUpTo("main")
                    }
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    @Suppress("SameParameterValue")
    private fun log(context: Context, message: String, shouldThrow: Boolean = false) {
        Log.d(TAG, message)
        if (!shouldThrow) {
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        } else {
            if (inInit) {
                _error = message
            } else {
                throw Exception(message)
            }
        }
    }

    @Suppress("SameParameterValue")
    private fun uiPrint(message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            _flashOutput.add("ui_print $message")
            _flashOutput.add("      ui_print")
        }
    }

    private fun addMessage(message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            _flashOutput.add(message)
        }
    }

    private fun clearTmp(context: Context) {
        if (flashFilename != null) {
            val zip = File(context.filesDir, flashFilename!!)
            if (zip.exists()) {
                zip.delete()
            }
        }
    }

    @Suppress("FunctionName")
    private fun _clearFlash() {
        _flashOutput.clear()
        _wasFlashSuccess.value = null
    }

    fun clearFlash(context: Context) {
        _clearFlash()
        PartitionUtil.AvailablePartitions.forEach { partitionName ->
            _backupPartitions[partitionName] = true
        }
        launch {
            clearTmp(context)
        }
    }

    @SuppressLint("SdCardPath")
    fun saveLog(context: Context) {
        launch {
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm"))
            val logName = if (navController.currentDestination!!.route!!.contains("ak3")) {
                "ak3"
            } else if (navController.currentDestination!!.route!!.endsWith("/backup")) {
                "backup"
            } else {
                "flash"
            }
            val log = File("/sdcard/Download/$logName-log--$now.log")
            if (navController.currentDestination!!.route!!.contains("ak3")) {
                log.writeText(flashOutput.filter { !it.matches("""progress [\d.]* [\d.]*""".toRegex()) }.joinToString("\n").replace("""ui_print (.*)\n {6}ui_print""".toRegex(), "$1"))
            } else {
                log.writeText(flashOutput.joinToString("\n"))
            }
            if (log.exists()) {
                log(context, "Saved $logName log to $log")
            } else {
                log(context, "Failed to save $log", shouldThrow = true)
            }
        }
    }

    @Suppress("FunctionName", "SameParameterValue")
    private fun _getKernel(context: Context) {
        val filesDir = context.filesDir
        val magiskboot = File(filesDir, "magiskboot")
        Shell.cmd("cd $filesDir && $magiskboot unpack $boot").exec()
        if (initBoot != null) {
            Shell.cmd("cd $filesDir && $magiskboot unpack $initBoot").exec()
        }
        val kernel = File(filesDir, "kernel")
        if (kernel.exists()) {
            val result = Shell.cmd("cd $filesDir && strings kernel | grep -E -m1 'Linux version.*#' | cut -d\\  -f3-").exec().out
            if (result.isNotEmpty()) {
                kernelVersion = result[0].replace("""\(.+\)""".toRegex(), "").replace("""\s+""".toRegex(), " ")
            }
        }
        Shell.cmd("cd $filesDir && $magiskboot cleanup").exec()
    }

    fun getKernel(context: Context) {
        launch {
            _getKernel(context)
        }
    }

    private fun isPartitionMounted(partition: File): Boolean {
        if (!partition.exists()) return false
        return try {
            val resolvedPath = partition.canonicalPath
            val mounts = File("/proc/mounts").readText()
            mounts.contains(resolvedPath) || mounts.contains(partition.path)
        } catch (_: Exception) {
            val dmPath = Shell.cmd("readlink -f $partition").exec().out.firstOrNull() ?: partition.path
            val mounts = Shell.cmd("mount | grep -w $dmPath").exec().out
            mounts.isNotEmpty()
        }
    }

    fun unmountVendorDlkm(context: Context) =
        launch {
            val httools = File(context.filesDir, "httools_static")
            if (!Shell.cmd("$httools umount vendor_dlkm").exec().isSuccess) {
                throw IOException("Unmounting vendor_dlkm failed")
            }
            refresh(context)
        }

    fun mountVendorDlkm(context: Context) {
        launch {
            val httools = File(context.filesDir, "httools_static")
            if (!Shell.cmd("$httools mount vendor_dlkm").exec().isSuccess) {
                throw IOException("Mounting vendor_dlkm failed")
            }
            refresh(context)
        }
    }

    fun unmapVendorDlkm(context: Context) {
        launch {
            val lptools = File(context.filesDir, "lptools_static")
            val mapperDir = "/dev/block/mapper"
            val vendorDlkm = fileSystemManager.getFile(mapperDir, "vendor_dlkm$slotSuffix")
            if (vendorDlkm.exists()) {
                val vendorDlkmVerity = fileSystemManager.getFile(mapperDir, "vendor_dlkm-verity")
                val result = if (vendorDlkmVerity.exists()) {
                    Shell.cmd("$lptools unmap vendor_dlkm-verity").exec()
                } else {
                    Shell.cmd("$lptools unmap vendor_dlkm$slotSuffix").exec()
                }
                if (!result.isSuccess) throw IOException("Unmapping vendor_dlkm failed")
            }
            refresh(context)
        }
    }

    fun mapVendorDlkm(context: Context) {
        launch {
            val lptools = File(context.filesDir, "lptools_static")
            if (!Shell.cmd("$lptools map vendor_dlkm$slotSuffix").exec().isSuccess) {
                throw IOException("Mapping vendor_dlkm failed")
            }
            refresh(context)
        }
    }

    private fun backupPartition(partition: ExtendedFile, destination: ExtendedFile): String? {
        if (partition.exists()) {
            val messageDigest = MessageDigest.getInstance(hashAlgorithm)
            partition.inputStream().use { inputStream ->
                destination.outputStream().use { outputStream ->
                    DigestOutputStream(outputStream, messageDigest).use { digestOutputStream ->
                        inputStream.copyTo(digestOutputStream)
                    }
                }
            }
            return messageDigest.digest().toHex()
        }
        return null
    }

    private fun backupPartitions(context: Context, destination: ExtendedFile): Partitions? {
        val partitions = HashMap<String, String>()
        for (partitionName in PartitionUtil.PartitionNames) {
            if (_backupPartitions[partitionName] == true) {
                val blockDevice = PartitionUtil.findPartitionBlockDevice(context, partitionName, slotSuffix)
                if (blockDevice != null) {
                    addMessage("Saving $partitionName")
                    val hash = backupPartition(blockDevice, destination.getChildFile("$partitionName.img"))
                    if (hash != null) {
                        partitions[partitionName] = hash
                    }
                }
            }
        }
        if (partitions.isNotEmpty()) {
            return Partitions.from(partitions)
        }
        return null
    }

    private fun createBackupDir(context: Context, now: String): ExtendedFile {
        @SuppressLint("SdCardPath")
        val externalDir = fileSystemManager.getFile("/sdcard/KernelFlasher")
        if (!externalDir.exists()) {
            if (!externalDir.mkdir()) {
                log(context, "Failed to create KernelFlasher dir on /sdcard", shouldThrow = true)
            }
        }
        val backupsDir = externalDir.getChildFile("backups")
        if (!backupsDir.exists()) {
            if (!backupsDir.mkdir()) {
                log(context, "Failed to create backups dir", shouldThrow = true)
            }
        }
        val backupDir = backupsDir.getChildFile(now)
        if (backupDir.exists()) {
            log(context, "Backup $now already exists", shouldThrow = true)
        } else {
            if (!backupDir.mkdir()) {
                log(context, "Failed to create backup dir", shouldThrow = true)
            }
        }
        return backupDir
    }

    fun backup(context: Context) {
        launch {
            _clearFlash()
            val currentKernelVersion = if (kernelVersion != null) {
                kernelVersion
            } else if (isActive) {
                System.getProperty("os.version")!!
            } else {
                _getKernel(context)
                kernelVersion
            }
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm"))
            val backupDir = createBackupDir(context, now)
            addMessage("Saving backup $now")
            val hashes = backupPartitions(context, backupDir)
            if (hashes == null) {
                log(context, "No partitions saved", shouldThrow = true)
            }
            val jsonFile = backupDir.getChildFile("backup.json")
            val backup = Backup(now, "raw", currentKernelVersion!!, sha1, null, hashes, hashAlgorithm)
            val indentedJson = Json { prettyPrint = true }
            jsonFile.outputStream().use { it.write(indentedJson.encodeToString(backup).toByteArray(Charsets.UTF_8)) }
            _backups[now] = backup
            addMessage("Backup $now saved")
            _wasFlashSuccess.value = true
        }
    }

    fun backupZip(context: Context, callback: () -> Unit) {
        launch {
            val source = context.contentResolver.openInputStream(flashUri!!)
            if (source != null) {
                _getKernel(context)
                val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm"))
                val backupDir = createBackupDir(context, now)
                val jsonFile = backupDir.getChildFile("backup.json")
                val backup = Backup(now, "ak3", kernelVersion!!, null, flashFilename)
                val indentedJson = Json { prettyPrint = true }
                jsonFile.outputStream().use { it.write(indentedJson.encodeToString(backup).toByteArray(Charsets.UTF_8)) }
                val destination = backupDir.getChildFile(flashFilename!!)
                source.use { inputStream ->
                    destination.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                _backups[now] = backup
                withContext (Dispatchers.Main) {
                    callback.invoke()
                }
            } else {
                log(context, "AK3 zip is missing", shouldThrow = true)
            }
        }
    }

    private fun resetSlot() {
        restoreSlot()
        val slotResult = Shell.cmd("getprop ro.boot.slot_suffix").exec()
        val activeSlotSuffix = slotResult.out.firstOrNull()
        if (!slotResult.isSuccess || activeSlotSuffix !in listOf("_a", "_b")) {
            throw IOException("Unable to determine the active slot")
        }
        originalSlotSuffix = activeSlotSuffix
        val targetSlot = if (slotSuffix.isNotEmpty()) slotSuffix else if (activeSlotSuffix == "_a") "_b" else "_a"
        wasSlotReset = true
        if (!Shell.cmd("resetprop -n ro.boot.slot_suffix $targetSlot || magisk resetprop -n ro.boot.slot_suffix $targetSlot").exec().isSuccess) {
            throw IOException("Failed to select slot $targetSlot")
        }
    }

    private fun restoreSlot() {
        if (!wasSlotReset) return
        val originalSlot = originalSlotSuffix ?: throw IOException("Original slot is unknown")
        if (!Shell.cmd("resetprop -n ro.boot.slot_suffix $originalSlot || magisk resetprop -n ro.boot.slot_suffix $originalSlot").exec().isSuccess) {
            throw IOException("Failed to restore slot $originalSlot")
        }
        wasSlotReset = false
        originalSlotSuffix = null
    }

    @Suppress("FunctionName")
    private suspend fun _checkZip(context: Context, zip: File, callback: (() -> Unit)? = null) {
        if (zip.exists()) {
            try {
                @Suppress("BlockingMethodInNonBlockingContext")
                val zipFile = ZipFile(zip)
                zipFile.use { z ->
                    if (z.getEntry("anykernel.sh") == null) {
                        log(context, "Invalid AK3 zip", shouldThrow = true)
                    }
                    withContext (Dispatchers.Main) {
                        callback?.invoke()
                    }
                }
            } catch (e: Exception) {
                zip.delete()
                throw e
            }
        } else {
            log(context, "Failed to save zip", shouldThrow = true)
        }
    }

    @Suppress("FunctionName")
    private fun _copyFile(context: Context, currentBackup: String, filename: String) {
        flashUri = null
        @SuppressLint("SdCardPath")
        val externalDir = File("/sdcard/KernelFlasher")
        val backupsDir = fileSystemManager.getFile("$externalDir/backups")
        val backupDir = backupsDir.getChildFile(currentBackup)
        if (!backupDir.exists()) {
            log(context, "Backup $currentBackup does not exists", shouldThrow = true)
        }
        if (currentBackup != File(currentBackup).name || currentBackup in listOf("", ".", "..") || filename != File(filename).name || filename in listOf("", ".", "..")) {
            throw IOException("Invalid backup filename")
        }
        val source = backupDir.getChildFile(filename)
        val zip = File.createTempFile("flash-", ".zip", context.filesDir)
        flashFilename = zip.name
        try {
            source.newInputStream().use { inputStream ->
                zip.outputStream().use { outputStream ->
                    if (inputStream.copyTo(outputStream) == 0L) throw IOException("Selected file is empty")
                }
            }
        } catch (exception: Exception) {
            zip.delete()
            throw exception
        }
    }

    @Suppress("FunctionName")
    private fun _copyFile(context: Context, uri: Uri) {
        flashUri = uri
        val displayName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            return@use if (name >= 0) cursor.getString(name) else null
        } ?: "ak3.zip"
        val source = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to read selected file")
        source.use { inputStream ->
            val file = File.createTempFile("flash-", if (displayName.endsWith(".zip", ignoreCase = true)) ".zip" else ".img", context.filesDir)
            flashFilename = file.name
            try {
                file.outputStream().use { outputStream ->
                    if (inputStream.copyTo(outputStream) == 0L) throw IOException("Selected file is empty")
                }
            } catch (exception: Exception) {
                file.delete()
                throw exception
            }
        }
    }

    @Suppress("FunctionName")
    private suspend fun _flashAk3(context: Context) {
        val zip = File(context.filesDir.canonicalPath, flashFilename!!)
        _wasFlashSuccess.value = false
        try {
            _checkZip(context, zip)
            if (!isActive) {
                resetSlot()
            }
            if (zip.exists()) {
                val files = File(context.filesDir.canonicalPath)
                val flashScript = File(files, "flash_ak3.sh")
                val result = Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER).build().newJob().add("F=$files Z=\"$zip\" /system/bin/sh $flashScript").to(flashOutput, flashOutput).exec()
                if (result.isSuccess) {
                    log(context, "Kernel flashed successfully")
                    _wasFlashSuccess.value = true
                } else {
                    log(context, "Failed to flash zip", shouldThrow = false)
                }
            } else {
                log(context, "AK3 zip is missing", shouldThrow = true)
            }
        } finally {
            clearTmp(context)
            uiPrint("")
            if (wasSlotReset) {
                restoreSlot()
            }
        }
    }

    fun flashAk3(context: Context, currentBackup: String, filename: String) {
        launch {
            _clearFlash()
            _copyFile(context, currentBackup, filename)
            _flashAk3(context)
        }
    }

    fun flashAk3(context: Context, uri: Uri) {
        launch {
            _clearFlash()
            _copyFile(context, uri)
            _flashAk3(context)
        }
    }

    fun flashImage(context: Context, uri: Uri, partitionName: String) {
        launch {
            _clearFlash()
            addMessage("Copying image ...")
            _copyFile(context, uri)
            val image = fileSystemManager.getFile(context.filesDir, flashFilename!!)
            _wasFlashSuccess.value = false
            try {
                if (!isActive) {
                    resetSlot()
                }
                if (image.exists()) {
                    addMessage("Copied $flashFilename")
                    _wasFlashSuccess.value = false
                    addMessage("Flashing $flashFilename to $partitionName ...")
                    val blockDevice = PartitionUtil.findPartitionBlockDevice(context, partitionName, slotSuffix)
                    if (blockDevice != null && blockDevice.exists()) {
                        if (PartitionUtil.isPartitionLogical(context, partitionName)) {
                            PartitionUtil.flashLogicalPartition(context, image, blockDevice, partitionName, slotSuffix, hashAlgorithm) { message ->
                                addMessage(message)
                            }
                        } else {
                            PartitionUtil.flashBlockDevice(image, blockDevice, hashAlgorithm)
                        }
                    } else {
                        log(context, "Partition $partitionName was not found", shouldThrow = true)
                    }
                    addMessage("Flashed $flashFilename to $partitionName")
                    addMessage("Cleaning up ...")
                    clearTmp(context)
                    addMessage("Done.")
                    _wasFlashSuccess.value = true
                } else {
                    log(context, "Partition image is missing", shouldThrow = true)
                }
            } finally {
                clearTmp(context)
                addMessage("")
                if (wasSlotReset) {
                    restoreSlot()
                }
            }
        }
    }
}
