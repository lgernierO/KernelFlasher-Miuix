package com.github.curiousnom.kernelflasher.common

import android.content.Context
import com.github.curiousnom.kernelflasher.common.extensions.ByteArray.toHex
import com.github.curiousnom.kernelflasher.common.types.partitions.FstabEntry
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.security.DigestOutputStream
import java.security.MessageDigest

object PartitionUtil {
    val PartitionNames = listOf(
        "boot",
        "dtbo",
        "init_boot",
        "recovery",
        "system_dlkm",
        "vbmeta",
        "vendor_boot",
        "vendor_dlkm",
        "vendor_kernel_boot"
    )

    val AvailablePartitions = mutableListOf<String>()

    private var fileSystemManager: FileSystemManager? = null
    private var bootParent: File? = null
    private val fstabCache = mutableMapOf<String, FstabEntry?>()

    fun init(context: Context, fileSystemManager: FileSystemManager, slotSuffix: String? = null) {
        this.fileSystemManager = fileSystemManager
        bootParent = null
        AvailablePartitions.clear()
        fstabCache.clear()
        val fstabEntry = findPartitionFstabEntry(context, "boot")
        if (fstabEntry != null) {
            bootParent = File(fstabEntry.blkDevice).parentFile
        }
        val activeSlotSuffix = slotSuffix ?: (Shell.cmd("getprop ro.boot.slot_suffix").exec().out.firstOrNull() ?: "")
        for (partitionName in PartitionNames) {
            val blockDevice = findPartitionBlockDevice(context, partitionName, activeSlotSuffix)
            if (blockDevice != null && blockDevice.exists()) {
                AvailablePartitions.add(partitionName)
            }
        }
    }

    private fun findPartitionFstabEntry(context: Context, partitionName: String): FstabEntry? {
        if (fstabCache.containsKey(partitionName)) {
            return fstabCache[partitionName]
        }
        val httools = File(context.filesDir, "httools_static")
        val result = Shell.cmd("$httools dump $partitionName").exec().out
        val entry = if (result.isNotEmpty()) {
            try {
                Json.decodeFromString<FstabEntry>(result[0])
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
        fstabCache[partitionName] = entry
        return entry
    }

    fun isPartitionLogical(context: Context, partitionName: String): Boolean {
        return findPartitionFstabEntry(context, partitionName)?.fsMgrFlags?.logical == true
    }

    fun findPartitionBlockDevice(context: Context, partitionName: String, slotSuffix: String): ExtendedFile? {
        var blockDevice: ExtendedFile? = null
        val fstabEntry = findPartitionFstabEntry(context, partitionName)
        if (fstabEntry != null) {
            if (fstabEntry.fsMgrFlags?.logical == true) {
                if (fstabEntry.logicalPartitionName == "$partitionName$slotSuffix") {
                    blockDevice = fileSystemManager!!.getFile(fstabEntry.blkDevice)
                }
            } else {
                blockDevice = fileSystemManager!!.getFile(fstabEntry.blkDevice)
                if (blockDevice.name != "$partitionName$slotSuffix") {
                    blockDevice = fileSystemManager!!.getFile(blockDevice.parentFile, "$partitionName$slotSuffix")
                }
            }
        }
        if (blockDevice == null || !blockDevice.exists()) {
            val siblingDevice = if (bootParent != null) fileSystemManager!!.getFile(bootParent!!, "$partitionName$slotSuffix") else null
            val physicalDevice = fileSystemManager!!.getFile("/dev/block/by-name/$partitionName$slotSuffix")
            val bootdeviceDevice = fileSystemManager!!.getFile("/dev/block/bootdevice/by-name/$partitionName$slotSuffix")
            val logicalDevice = fileSystemManager!!.getFile("/dev/block/mapper/$partitionName$slotSuffix")
            if (siblingDevice?.exists() == true) {
                blockDevice = siblingDevice
            } else if (physicalDevice.exists()) {
                blockDevice = physicalDevice
            } else if (bootdeviceDevice.exists()) {
                blockDevice = bootdeviceDevice
            } else if (logicalDevice.exists()) {
                blockDevice = logicalDevice
            }
        }
        return blockDevice
    }

    @Suppress("unused")
    fun partitionAvb(context: Context, partitionName: String): String {
        val httools = File(context.filesDir, "httools_static")
        val result = Shell.cmd("$httools avb $partitionName").exec().out
        return if (result.isNotEmpty()) result[0] else ""
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    private fun fileSize(file: ExtendedFile): ULong {
        val result = Shell.cmd("wc -c < ${shellQuote(file.path)}").exec()
        val size = result.out.firstOrNull()?.trim()?.toULongOrNull()
        if (!result.isSuccess || size == null || size == 0UL) {
            throw IOException("Failed to read a positive size for ${file.name}")
        }
        return size
    }

    fun flashBlockDevice(image: ExtendedFile, blockDevice: ExtendedFile, hashAlgorithm: String): String {
        val partitionSize = fileSize(blockDevice)
        val imageSize = fileSize(image)
        if (partitionSize < imageSize) {
            throw IOException("Partition ${blockDevice.name} is smaller than image")
        }
        val messageDigest = MessageDigest.getInstance(hashAlgorithm)
        image.newInputStream().use { inputStream ->
            if (partitionSize > imageSize) {
                val blocks = partitionSize / 4096UL
                val remaining = partitionSize % 4096UL
                val target = shellQuote(blockDevice.path)
                if (blocks > 0UL && !Shell.cmd("dd bs=4096 count=$blocks if=/dev/zero of=$target conv=notrunc").exec().isSuccess) {
                    throw IOException("Failed to clear ${blockDevice.name}")
                }
                if (remaining > 0UL && !Shell.cmd("dd bs=1 seek=${blocks * 4096UL} count=$remaining if=/dev/zero of=$target conv=notrunc").exec().isSuccess) {
                    throw IOException("Failed to clear ${blockDevice.name}")
                }
            }
            blockDevice.newOutputStream().use { outputStream ->
                DigestOutputStream(outputStream, messageDigest).use { digestOutputStream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var remainingBytes = imageSize
                    while (remainingBytes > 0UL) {
                        val count = inputStream.read(buffer, 0, minOf(buffer.size.toULong(), remainingBytes).toInt())
                        if (count < 0) throw IOException("Image ${image.name} changed while flashing")
                        digestOutputStream.write(buffer, 0, count)
                        remainingBytes -= count.toULong()
                    }
                    if (inputStream.read() != -1) {
                        throw IOException("Image ${image.name} changed while flashing")
                    }
                }
            }
        }
        return messageDigest.digest().toHex()
    }

    @Suppress("SameParameterValue")
    fun flashLogicalPartition(context: Context, image: ExtendedFile, blockDevice: ExtendedFile, partitionName: String, slotSuffix: String, hashAlgorithm: String, addMessage: (message: String) -> Unit): String {
        val sourceFileSize = fileSize(image)
        MessageDigest.getInstance(hashAlgorithm)
        val lptools = File(context.filesDir, "lptools_static")
        Shell.cmd("$lptools remove ${partitionName}_kf").exec()
        if (Shell.cmd("$lptools create ${partitionName}_kf $sourceFileSize").exec().isSuccess) {
            if (Shell.cmd("$lptools unmap ${partitionName}_kf").exec().isSuccess) {
                if (Shell.cmd("$lptools map ${partitionName}_kf").exec().isSuccess) {
                    val temporaryBlockDevice = fileSystemManager!!.getFile("/dev/block/mapper/${partitionName}_kf")
                    val hash = flashBlockDevice(image, temporaryBlockDevice, hashAlgorithm)
                    if (Shell.cmd("$lptools replace ${partitionName}_kf $partitionName$slotSuffix").exec().isSuccess) {
                        return hash
                    } else {
                        throw IOException("Replacing $partitionName$slotSuffix failed")
                    }
                } else {
                    throw IOException("Remapping ${partitionName}_kf failed")
                }
            } else {
                throw IOException("Unmapping ${partitionName}_kf failed")
            }
        } else {
            addMessage.invoke("Creating ${partitionName}_kf failed. Attempting to resize $partitionName$slotSuffix ...")
            val httools = File(context.filesDir, "httools_static")
            if (Shell.cmd("$httools umount $partitionName").exec().isSuccess) {
                val verityBlockDevice = blockDevice.parentFile!!.getChildFile("${partitionName}-verity")
                if (verityBlockDevice.exists()) {
                    if (!Shell.cmd("$lptools unmap ${partitionName}-verity").exec().isSuccess) {
                        throw IOException("Unmapping ${partitionName}-verity failed")
                    }
                }
                if (Shell.cmd("$lptools unmap $partitionName$slotSuffix").exec().isSuccess) {
                    if (Shell.cmd("$lptools resize $partitionName$slotSuffix $sourceFileSize").exec().isSuccess) {
                        if (Shell.cmd("$lptools map $partitionName$slotSuffix").exec().isSuccess) {
                            val hash = flashBlockDevice(image, blockDevice, hashAlgorithm)
                            if (Shell.cmd("$httools mount $partitionName").exec().isSuccess) {
                                return hash
                            } else {
                                throw IOException("Mounting $partitionName failed")
                            }
                        } else {
                            throw IOException("Remapping $partitionName$slotSuffix failed")
                        }
                    } else {
                        throw IOException("Resizing $partitionName$slotSuffix failed")
                    }
                } else {
                    throw IOException("Unmapping $partitionName$slotSuffix failed")
                }
            } else {
                throw IOException("Unmounting $partitionName failed")
            }
        }
    }
}
