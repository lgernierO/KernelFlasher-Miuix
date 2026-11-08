package com.github.curiousnom.kernelflasher.ui.screens.updates

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.room.Room
import com.github.curiousnom.kernelflasher.common.extensions.ByteArray.toHex
import com.github.curiousnom.kernelflasher.common.types.room.AppDatabase
import com.github.curiousnom.kernelflasher.common.types.room.updates.Update
import com.github.curiousnom.kernelflasher.common.types.room.updates.UpdateSerializer
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.io.path.Path
import kotlin.io.path.name

@ExperimentalSerializationApi
class UpdatesViewModel(
    context: Context,
    @Suppress("unused") private val fileSystemManager: FileSystemManager,
    private val navController: NavController,
    private val _isRefreshing: MutableState<Boolean>
) : ViewModel() {
    companion object {
        const val TAG: String = "KernelFlasher/UpdatesState"
        val lastUpdatedFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }

    private val client = OkHttpClient()
    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "kernel-flasher").build()
    private val updateDao = db.updateDao()
    private val _updates: SnapshotStateList<Update> = mutableStateListOf()

    var currentUpdate: Update? = null
    var changelog: String? = null

    val updates: List<Update>
        get() = _updates
    val isRefreshing: Boolean
        get() = _isRefreshing.value

    init {
        launch {
            val updates = updateDao.getAll()
            withContext(Dispatchers.Main) {
                _updates.addAll(updates)
            }
        }
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
            throw Exception(message)
        }
    }

    fun clearCurrent() {
        currentUpdate = null
        changelog = null
    }

    fun add(url: String, callback: (updateId: Int) -> Unit) {
        launch {
            val request = Request.Builder()
                .url(url)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected response: $response")
                val update: Update = Json.decodeFromString(UpdateSerializer, response.body!!.string())
                update.updateUri = url
                update.lastUpdated = Date()
                val updateId = updateDao.insert(update).toInt()
                val inserted = updateDao.load(updateId)
                withContext (Dispatchers.Main) {
                    _updates.add(inserted)
                    callback.invoke(updateId)
                }
            }
        }
    }

    fun update() {
        launch {
            val request = Request.Builder()
                .url(currentUpdate!!.updateUri!!)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected response: $response")
                val update: Update = Json.decodeFromString(UpdateSerializer, response.body!!.string())
                currentUpdate!!.let {
                    withContext (Dispatchers.Main) {
                        it.kernelName = update.kernelName
                        it.kernelVersion = update.kernelVersion
                        it.kernelLink = update.kernelLink
                        it.kernelChangelogUrl = update.kernelChangelogUrl
                        it.kernelDate = update.kernelDate
                        it.kernelSha1 = update.kernelSha1
                        it.supportLink = update.supportLink
                        it.lastUpdated = Date()
                        withContext(Dispatchers.IO) {
                            updateDao.update(it)
                        }
                    }
                }
            }
        }
    }

    fun downloadChangelog(callback: () -> Unit) {
        launch {
            val request = Request.Builder()
                .url(currentUpdate!!.kernelChangelogUrl)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected response: $response")
                changelog = response.body!!.string()
                withContext (Dispatchers.Main) {
                    callback.invoke()
                }
            }
        }
    }

    private fun insertDownload(context: Context, filename: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues()
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        values.put(MediaStore.MediaColumns.IS_PENDING, 1)
        return resolver.insert(MediaStore.Files.getContentUri("external"), values)
    }

    fun downloadKernel(context: Context) {
        launch {
            val update = currentUpdate ?: throw IOException("No update selected")
            val expectedSha1 = update.kernelSha1
            if (!Regex("[0-9a-fA-F]{40}").matches(expectedSha1)) {
                throw IOException("Invalid kernel SHA-1 in update metadata")
            }
            val remoteUri = Uri.parse(update.kernelLink)
            val filename = Path(remoteUri.path!!).name
            val request = Request.Builder().url(remoteUri.toString()).build()
            val resolver = context.contentResolver
            val localUri = insertDownload(context, filename)
                ?: throw IOException("Unable to create download")
            var completed = false
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected response: $response")
                    val body = response.body ?: throw IOException("Empty download response")
                    body.byteStream().use { inputStream ->
                        val output = resolver.openOutputStream(localUri)
                            ?: throw IOException("Unable to open download")
                        output.use { outputStream ->
                            copyVerifiedKernel(inputStream, outputStream, expectedSha1)
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                if (resolver.update(localUri, values, null, null) == 0) {
                    throw IOException("Unable to publish download")
                }
                completed = true
                log(context, "Saved $filename to Downloads")
            } finally {
                if (!completed) {
                    try {
                        resolver.delete(localUri, null, null)
                    } catch (e: Exception) {
                        Log.e(TAG, "Unable to remove incomplete download", e)
                    }
                }
            }
        }
    }

    fun delete(callback: () -> Unit) {
        launch {
            updateDao.delete(currentUpdate!!)
            withContext (Dispatchers.Main) {
                _updates.remove(currentUpdate!!)
                callback.invoke()
                currentUpdate = null
            }
        }
    }
}

internal suspend fun copyVerifiedKernel(input: InputStream, output: OutputStream, expectedSha1: String) {
    val digest = MessageDigest.getInstance("SHA-1")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = input.read(buffer)
        if (count == -1) break
        digest.update(buffer, 0, count)
        output.write(buffer, 0, count)
    }
    if (!digest.digest().toHex().equals(expectedSha1, ignoreCase = true)) {
        throw IOException("Downloaded kernel SHA-1 does not match update metadata")
    }
}
