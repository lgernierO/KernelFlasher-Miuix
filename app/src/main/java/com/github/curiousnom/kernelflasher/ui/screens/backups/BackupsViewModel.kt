package com.github.curiousnom.kernelflasher.ui.screens.backups

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
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
import com.github.curiousnom.kernelflasher.common.extensions.ExtendedFile.outputStream
import com.github.curiousnom.kernelflasher.common.extensions.ExtendedFile.readText
import com.github.curiousnom.kernelflasher.common.types.backups.Backup
import com.github.curiousnom.kernelflasher.common.types.partitions.Partitions
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

class BackupsViewModel(
    context: Context,
    private val fileSystemManager: FileSystemManager,
    private val navController: NavController,
    private val _isRefreshing: MutableState<Boolean>,
    private val _backups: MutableMap<String, Backup>
) : ViewModel() {
    companion object {
        const val TAG: String = "KernelFlasher/BackupsState"
    }

    private val _restoreOutput: SnapshotStateList<String> = mutableStateListOf()
    var currentBackup: String? = null
        set(value) {
            if (value != field) {
                _backupPartitions.clear()
                if (_backups[value]?.hashes != null) {
                    PartitionUtil.AvailablePartitions.forEach { partitionName ->
                        if (_backups[value]!!.hashes!!.get(partitionName) != null) {
                            _backupPartitions[partitionName] = true
                        }
                    }
                }
                field = value
            }
        }
    var wasRestored: Boolean? = null
    private val _backupPartitions: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    private val hashAlgorithm: String = "SHA-256"
    @Deprecated("Backup migration will be removed in the first stable release")
    private var _needsMigration: MutableState<Boolean> = mutableStateOf(false)

    val restoreOutput: List<String>
        get() = _restoreOutput
    val backupPartitions: MutableMap<String, Boolean>
        get() = _backupPartitions
    val isRefreshing: Boolean
        get() = _isRefreshing.value
    val backups: Map<String, Backup>
        get() = _backups
    @Deprecated("Backup migration will be removed in the first stable release")
    val needsMigration: Boolean
        get() = _needsMigration.value

    init {
        refresh(context)
    }

    fun refresh(context: Context) {
        val oldDir = context.getExternalFilesDir(null)
        val oldBackupsDir = File(oldDir, "backups")
        _needsMigration.value = oldBackupsDir.exists() && oldBackupsDir.listFiles()?.size!! > 0
        @SuppressLint("SdCardPath")
        val externalDir = File("/sdcard/KernelFlasher")
        val backupsDir = fileSystemManager.getFile("$externalDir/backups")
        if (backupsDir.exists()) {
            val children = backupsDir.listFiles()
            if (children != null) {
                for (child in children.sortedByDescending{it.name}) {
                    if (!child.isDirectory) {
                        continue
                    }
                    val jsonFile = child.getChildFile("backup.json")
                    if (jsonFile.exists()) {
                        _backups[child.name] = Json.decodeFromString(jsonFile.readText())
                    }
                }
            }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                withContext(Dispatchers.IO) {
                    block()
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (e: Exception) {
                withContext (Dispatchers.Main) {
                    Log.e(TAG, e.message, e)
                    navController.navigate("error/${Uri.encode(e.message ?: e.javaClass.simpleName)}") {
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
            throw Exception(message)
        }
    }

    fun clearCurrent() {
        currentBackup = null
        clearRestore()
    }

    private fun addMessage(message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            _restoreOutput.add(message)
        }
    }

    @Suppress("FunctionName")
    private fun _clearRestore() {
        _restoreOutput.clear()
        wasRestored = null
    }

    private fun clearRestore() {
        _clearRestore()
        _backupPartitions.clear()
    }

    @Suppress("unused")
    @SuppressLint("SdCardPath")
    fun saveLog(context: Context) {
        launch {
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm"))
            val downloadDir = File("/sdcard/Download")
            Shell.cmd("mkdir -p $downloadDir").exec()
            val log = File(downloadDir, "restore-log--$now.log")
            log.writeText(restoreOutput.joinToString("\n"))
            if (log.exists()) {
                log(context, "Saved restore log to $log")
            } else {
                log(context, "Failed to save $log", shouldThrow = true)
            }
        }
    }

    private suspend fun restorePartitions(context: Context, source: ExtendedFile, slotSuffix: String, backup: Backup): Partitions? {
        val selection = _backupPartitions.toMap()
        val selectedPartitions = PartitionUtil.PartitionNames.filter { partitionName ->
            if (backup.hashes != null || selection.isNotEmpty()) {
                selection[partitionName] == true
            } else {
                source.getChildFile("$partitionName.img").exists()
            }
        }
        val restoreHashAlgorithm = backup.hashAlgorithm?.takeIf { it.isNotBlank() } ?: hashAlgorithm
        val coroutineContext = currentCoroutineContext()
        val preparedPartitions = ArrayList<Triple<String, ExtendedFile, ExtendedFile>>()
        for (partitionName in selectedPartitions) {
            coroutineContext.ensureActive()
            val image = source.getChildFile("$partitionName.img")
            if (!image.exists()) {
                throw IOException("Backup image $partitionName.img was not found")
            }
            val expectedHash = backup.hashes?.let { hashes ->
                hashes.get(partitionName)
                    ?: throw IOException("Backup hash for $partitionName was not found")
            }
            val digest = if (expectedHash != null) MessageDigest.getInstance(restoreHashAlgorithm) else null
            var imageSize = 0L
            image.newInputStream().use { inputStream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = inputStream.read(buffer)
                    if (count < 0) break
                    imageSize += count
                    digest?.update(buffer, 0, count)
                }
            }
            if (imageSize == 0L) {
                throw IOException("Backup image $partitionName.img is empty")
            }
            if (digest != null && !digest.digest().toHex().equals(expectedHash, ignoreCase = true)) {
                throw IOException("Backup hash mismatch for $partitionName")
            }
            val blockDevice = PartitionUtil.findPartitionBlockDevice(context, partitionName, slotSuffix)
            if (blockDevice == null || !blockDevice.exists()) {
                throw IOException("Partition $partitionName was not found")
            }
            preparedPartitions.add(Triple(partitionName, image, blockDevice))
        }
        val partitions = HashMap<String, String>()
        for ((partitionName, image, blockDevice) in preparedPartitions) {
            coroutineContext.ensureActive()
            addMessage("Restoring $partitionName")
            partitions[partitionName] = if (PartitionUtil.isPartitionLogical(context, partitionName)) {
                PartitionUtil.flashLogicalPartition(context, image, blockDevice, partitionName, slotSuffix, restoreHashAlgorithm) { message ->
                    addMessage(message)
                }
            } else {
                PartitionUtil.flashBlockDevice(image, blockDevice, restoreHashAlgorithm)
            }
        }
        if (partitions.isNotEmpty()) {
            return Partitions.from(partitions)
        }
        return null
    }

    fun restore(context: Context, slotSuffix: String) {
        launch {
            _clearRestore()
            wasRestored = false
            val backupId = currentBackup ?: throw IOException("No backup selected")
            val backup = _backups[backupId] ?: throw IOException("Backup $backupId was not found")
            @SuppressLint("SdCardPath")
            val externalDir = File("/sdcard/KernelFlasher")
            val backupsDir = fileSystemManager.getFile("$externalDir/backups")
            val backupDir = backupsDir.getChildFile(backupId)
            if (!backupDir.exists()) {
                log(context, "Backup $backupId does not exists", shouldThrow = true)
                return@launch
            }
            addMessage("Restoring backup $backupId")
            val hashes = restorePartitions(context, backupDir, slotSuffix, backup)
            if (hashes == null) {
                log(context, "No partitions restored", shouldThrow = true)
            }
            addMessage("Backup $backupId restored")
            wasRestored = true
        }
    }

    fun delete(context: Context, callback: () -> Unit) {
        launch {
            @SuppressLint("SdCardPath")
            val externalDir = File("/sdcard/KernelFlasher")
            val backupsDir = fileSystemManager.getFile("$externalDir/backups")
            val backupDir = backupsDir.getChildFile(currentBackup!!)
            if (!backupDir.exists()) {
                log(context, "Backup $currentBackup does not exists", shouldThrow = true)
                return@launch
            }
            backupDir.deleteRecursively()
            _backups.remove(currentBackup!!)
            withContext(Dispatchers.Main) {
                callback.invoke()
            }
        }
    }

    @SuppressLint("SdCardPath")
    @Deprecated("Backup migration will be removed in the first stable release")
    fun migrate(context: Context) {
        launch {
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
            val oldDir = context.getExternalFilesDir(null)
            val oldBackupsDir = File(oldDir, "backups")
            if (oldBackupsDir.exists()) {
                val indentedJson = Json { prettyPrint = true }
                val children = oldBackupsDir.listFiles()
                if (children != null) {
                    for (child in children.sortedByDescending{it.name}) {
                        if (!child.isDirectory) {
                            child.delete()
                            continue
                        }
                        val propFile = File(child, "backup.prop")
                        @Suppress("BlockingMethodInNonBlockingContext")
                        val inputStream = FileInputStream(propFile)
                        val props = Properties()
                        @Suppress("BlockingMethodInNonBlockingContext")
                        props.load(inputStream)

                        val name = child.name
                        val type = props.getProperty("type", "raw")
                        val kernelVersion = props.getProperty("kernel")
                        val bootSha1 = if (type == "raw") props.getProperty("sha1") else null
                        val filename = if (type == "ak3") "ak3.zip" else null
                        propFile.delete()

                        val dest = backupsDir.getChildFile(child.name)
                        Shell.cmd("mv $child $dest").exec()
                        if (!dest.exists()) {
                            throw Error("Too slow")
                        }
                        val jsonFile = dest.getChildFile("backup.json")
                        val backup = Backup(name, type, kernelVersion, bootSha1, filename)
                        jsonFile.outputStream().use { it.write(indentedJson.encodeToString(backup).toByteArray(Charsets.UTF_8)) }
                        _backups[name] = backup
                    }
                }
                oldBackupsDir.delete()
            }
            refresh(context)
        }
    }
}
