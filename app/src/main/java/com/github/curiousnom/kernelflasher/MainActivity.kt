package com.github.curiousnom.kernelflasher

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AccelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.github.curiousnom.kernelflasher.ui.navigation.MiuixNavHost
import com.github.curiousnom.kernelflasher.ui.navigation.miuixPage
import com.github.curiousnom.kernelflasher.ui.navigation.rememberFlasherNavigation
import com.github.curiousnom.kernelflasher.ui.screens.RefreshableScreen
import com.github.curiousnom.kernelflasher.ui.screens.backups.BackupsContent
import com.github.curiousnom.kernelflasher.ui.screens.backups.SlotBackupsContent
import com.github.curiousnom.kernelflasher.ui.screens.error.ErrorScreen
import com.github.curiousnom.kernelflasher.ui.screens.main.MainContent
import com.github.curiousnom.kernelflasher.ui.screens.main.MainViewModel
import com.github.curiousnom.kernelflasher.ui.screens.reboot.RebootContent
import com.github.curiousnom.kernelflasher.ui.screens.slot.SlotContent
import com.github.curiousnom.kernelflasher.ui.screens.slot.SlotFlashContent
import com.github.curiousnom.kernelflasher.ui.screens.updates.UpdatesAddContent
import com.github.curiousnom.kernelflasher.ui.screens.updates.UpdatesChangelogContent
import com.github.curiousnom.kernelflasher.ui.screens.updates.UpdatesContent
import com.github.curiousnom.kernelflasher.ui.screens.updates.UpdatesViewContent
import com.github.curiousnom.kernelflasher.ui.theme.KernelFlasherTheme
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.serialization.ExperimentalSerializationApi
import java.io.File


@ExperimentalSerializationApi
class MainActivity : ComponentActivity() {
    companion object {
        const val TAG: String = "MainActivity"
        init {
            Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER))
        }
    }

    private var rootServiceConnected: Boolean = false
    private var viewModel: MainViewModel? = null
    private lateinit var mainListener: MainListener
    var isAwaitingResult = false

    inner class AidlConnection : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (!rootServiceConnected) {
                val ipc: IFilesystemService = IFilesystemService.Stub.asInterface(service)
                val binder: IBinder = ipc.fileSystemService
                onAidlConnected(FileSystemManager.getRemote(binder))
                rootServiceConnected = true
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            setContent {
                KernelFlasherTheme {
                    ErrorScreen(stringResource(R.string.root_service_disconnected))
                }
            }
        }
    }

    private fun ensureBinaries() {
        val binaries = listOf("lptools_static", "httools_static", "magiskboot")
        val filesToChmod = mutableListOf<String>()

        for (filename in binaries) {
            val binary = File(applicationInfo.nativeLibraryDir, "lib$filename.so")
            val dest = File(filesDir, filename)
            if (!dest.exists() || dest.length() != binary.length() || !dest.canExecute()) {
                binary.inputStream().use { inputStream ->
                    dest.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                dest.setExecutable(true, false)
                filesToChmod.add(dest.absolutePath)
            }
        }

        val scriptDest = File(filesDir, "flash_ak3.sh")
        if (!scriptDest.exists() || scriptDest.length() == 0L || !scriptDest.canExecute()) {
            assets.open("flash_ak3.sh").use { inputStream ->
                scriptDest.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            scriptDest.setExecutable(true, false)
            filesToChmod.add(scriptDest.absolutePath)
        }

        if (filesToChmod.isNotEmpty()) {
            Shell.cmd("chmod 755 " + filesToChmod.joinToString(" ")).exec()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        enableEdgeToEdge()
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val scale = ObjectAnimator.ofPropertyValuesHolder(
                splashScreenView.view,
                PropertyValuesHolder.ofFloat(
                    View.SCALE_X,
                    1f,
                    0f
                ),
                PropertyValuesHolder.ofFloat(
                    View.SCALE_Y,
                    1f,
                    0f
                )
            )
            scale.interpolator = AccelerateInterpolator()
            scale.duration = 250L
            scale.doOnEnd { splashScreenView.remove() }
            scale.start()
        }

        val content: View = findViewById(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    return if (viewModel?.isRefreshing == false || Shell.isAppGrantedRoot() == false) {
                        content.viewTreeObserver.removeOnPreDrawListener(this)
                        true
                    } else {
                        false
                    }
                }
            }
        )

        Shell.getShell()
        if (Shell.isAppGrantedRoot()!!) {
            val intent = Intent(this, FilesystemService::class.java)
            RootService.bind(intent, AidlConnection())
        } else {
            setContent {
                KernelFlasherTheme {
                    ErrorScreen(stringResource(R.string.root_required))
                }
            }
        }
    }

    fun onAidlConnected(fileSystemManager: FileSystemManager) {
        try {
            ensureBinaries()
        } catch (e: Exception) {
            Log.e(TAG, e.message, e)
            setContent {
                KernelFlasherTheme {
                    ErrorScreen(e.message!!)
                }
            }
            return
        }
        setContent {
            val navigation = rememberFlasherNavigation()
            val navigator = navigation.navigator
            val navController = navigation.navController
            viewModel = viewModel {
                val application = checkNotNull(get(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY))
                MainViewModel(application, fileSystemManager, navController)
            }
            val mainViewModel = viewModel!!
            KernelFlasherTheme {
                if (!mainViewModel.hasError) {
                    mainListener = MainListener {
                        mainViewModel.refresh(this)
                    }
                    val slotViewModelA = mainViewModel.slotA
                    val slotViewModelB = mainViewModel.slotB
                    val backupsViewModel = mainViewModel.backups
                    val updatesViewModel = mainViewModel.updates
                    val rebootViewModel = mainViewModel.reboot
                    val slotContent: @Composable (NavBackStackEntry) -> Unit = { backStackEntry ->
                        val slotSuffix = backStackEntry.arguments?.getString("slotSuffix") ?: ""
                        val slotViewModel = if (slotSuffix == "_b") slotViewModelB else slotViewModelA
                        if (slotViewModel!!.wasFlashSuccess != null && isCurrentEntry(navController, backStackEntry)) {
                            slotViewModel.clearFlash(this@MainActivity)
                        }
                        val title = stringResource(if (slotSuffix == "_a") R.string.slot_a else if (slotSuffix == "_b") R.string.slot_b else R.string.slot)
                        RefreshableScreen(mainViewModel, navController, swipeEnabled = true, title = title) {
                            SlotContent(slotViewModel, slotSuffix, navController)
                        }

                    }
                    val slotFlashContent: @Composable (NavBackStackEntry) -> Unit = { backStackEntry ->
                        val slotSuffix = backStackEntry.arguments?.getString("slotSuffix") ?: ""
                        val slotViewModel = if (slotSuffix == "_b") slotViewModelB else slotViewModelA
                        val currentRoute = backStackEntry.destination.route.orEmpty()
                        val title = stringResource(
                            if ("backup" in currentRoute.split('/')) R.string.backup
                            else R.string.flash
                        )
                        RefreshableScreen(mainViewModel, navController, title = title) {
                            SlotFlashContent(slotViewModel!!, slotSuffix, navController, currentRoute)
                        }
                    }
                    val slotBackupsContent: @Composable (NavBackStackEntry) -> Unit = { backStackEntry ->
                        val slotSuffix = backStackEntry.arguments?.getString("slotSuffix") ?: ""
                        val slotViewModel = if (slotSuffix == "_b") slotViewModelB else slotViewModelA
                        val backupId = backStackEntry.arguments?.getString("backupId")
                        if (isCurrentEntry(navController, backStackEntry)) {
                            if (backupId != null) {
                                backupsViewModel.currentBackup = backupId
                            } else {
                                backupsViewModel.clearCurrent()
                            }
                        }
                        RefreshableScreen(mainViewModel, navController, title = stringResource(R.string.backups)) {
                            SlotBackupsContent(
                                slotViewModel!!,
                                backupsViewModel,
                                slotSuffix,
                                navController,
                                backStackEntry.destination.route.orEmpty(),
                                backupId
                            )
                        }
                    }
                    val slotBackupFlashContent: @Composable (NavBackStackEntry) -> Unit = { backStackEntry ->
                        val slotSuffix = backStackEntry.arguments?.getString("slotSuffix") ?: ""
                        val slotViewModel = if (slotSuffix == "_b") slotViewModelB else slotViewModelA
                        val backupId = backStackEntry.arguments?.getString("backupId")
                        if (isCurrentEntry(navController, backStackEntry)) {
                            backupsViewModel.currentBackup = backupId
                        }
                        if (backupsViewModel.backups.containsKey(backupId)) {
                            RefreshableScreen(mainViewModel, navController, title = stringResource(R.string.flash)) {
                                SlotFlashContent(
                                    slotViewModel!!,
                                    slotSuffix,
                                    navController,
                                    backStackEntry.destination.route.orEmpty()
                                )
                            }
                        }

                    }
                    MiuixNavHost(
                        navController = navController,
                        navigator = navigator,
                        startDestination = "main",
                        isBusy = mainViewModel.isRefreshing,
                    ) {
                        miuixPage("main") {
                            RefreshableScreen(
                                mainViewModel,
                                navController,
                                swipeEnabled = true,
                                showBackButton = false
                            ) {
                                MainContent(mainViewModel, navController)
                            }
                        }
                        if (mainViewModel.isAb) {
                            miuixPage("slot{slotSuffix}", content = slotContent)
                            miuixPage("slot{slotSuffix}/flash", content = slotFlashContent)
                            miuixPage("slot{slotSuffix}/flash/ak3", content = slotFlashContent)
                            miuixPage("slot{slotSuffix}/flash/image", content = slotFlashContent)
                            miuixPage("slot{slotSuffix}/flash/image/flash", content = slotFlashContent)
                            miuixPage("slot{slotSuffix}/backup", content = slotFlashContent)
                            miuixPage("slot{slotSuffix}/backup/backup", content = slotFlashContent)
                            miuixPage("slot{slotSuffix}/backups", content = slotBackupsContent)
                            miuixPage("slot{slotSuffix}/backups/{backupId}", content = slotBackupsContent)
                            miuixPage("slot{slotSuffix}/backups/{backupId}/restore", content = slotBackupsContent)
                            miuixPage("slot{slotSuffix}/backups/{backupId}/restore/restore", content = slotBackupsContent)
                            miuixPage("slot{slotSuffix}/backups/{backupId}/flash/ak3", content = slotBackupFlashContent)
                        } else {
                            miuixPage("slot", content = slotContent)
                            miuixPage("slot/flash", content = slotFlashContent)
                            miuixPage("slot/flash/ak3", content = slotFlashContent)
                            miuixPage("slot/flash/image", content = slotFlashContent)
                            miuixPage("slot/flash/image/flash", content = slotFlashContent)
                            miuixPage("slot/backup", content = slotFlashContent)
                            miuixPage("slot/backup/backup", content = slotFlashContent)
                            miuixPage("slot/backups", content = slotBackupsContent)
                            miuixPage("slot/backups/{backupId}", content = slotBackupsContent)
                            miuixPage("slot/backups/{backupId}/restore", content = slotBackupsContent)
                            miuixPage("slot/backups/{backupId}/restore/restore", content = slotBackupsContent)
                            miuixPage("slot/backups/{backupId}/flash/ak3", content = slotBackupFlashContent)
                        }
                        miuixPage("backups") { backStackEntry ->
                            if (isCurrentEntry(navController, backStackEntry)) {
                                backupsViewModel.clearCurrent()
                            }
                            RefreshableScreen(mainViewModel, navController, title = stringResource(R.string.backups)) {
                                BackupsContent(backupsViewModel, navController, backupId = null)
                            }
                        }
                        miuixPage("backups/{backupId}") { backStackEntry ->
                            val backupId = backStackEntry.arguments?.getString("backupId")
                            if (isCurrentEntry(navController, backStackEntry)) {
                                backupsViewModel.currentBackup = backupId
                            }
                            if (backupsViewModel.backups.containsKey(backupId)) {
                                RefreshableScreen(mainViewModel, navController, title = stringResource(R.string.backup)) {
                                    BackupsContent(backupsViewModel, navController, backupId)
                                }
                            }
                        }
                        miuixPage("updates") { backStackEntry ->
                            if (isCurrentEntry(navController, backStackEntry)) {
                                updatesViewModel.clearCurrent()
                            }
                            RefreshableScreen(mainViewModel, navController, title = stringResource(R.string.updates)) {
                                UpdatesContent(updatesViewModel, navController)
                            }
                        }
                        miuixPage("updates/add") {
                            RefreshableScreen(mainViewModel, navController, title = stringResource(R.string.add)) {
                                UpdatesAddContent(updatesViewModel, navController)
                            }
                        }
                        miuixPage("updates/view/{updateId}") { backStackEntry ->
                            val updateId = backStackEntry.arguments?.getString("updateId")!!.toInt()
                            val currentUpdate = updatesViewModel.updates.firstOrNull { it.id == updateId }
                            if (isCurrentEntry(navController, backStackEntry)) {
                                updatesViewModel.currentUpdate = currentUpdate
                            }
                            if (currentUpdate != null) {
                                RefreshableScreen(mainViewModel, navController, title = stringResource(R.string.updates)) {
                                    UpdatesViewContent(updatesViewModel, navController, currentUpdate)
                                }
                            }
                        }
                        miuixPage("updates/view/{updateId}/changelog") { backStackEntry ->
                            val updateId = backStackEntry.arguments?.getString("updateId")!!.toInt()
                            val currentUpdate = updatesViewModel.updates.firstOrNull { it.id == updateId }
                            if (isCurrentEntry(navController, backStackEntry)) {
                                updatesViewModel.currentUpdate = currentUpdate
                            }
                            val changelog = remember(backStackEntry.id) {
                                updatesViewModel.changelog.orEmpty()
                            }
                            if (currentUpdate != null) {
                                RefreshableScreen(mainViewModel, navController, title = stringResource(R.string.changelog)) {
                                    UpdatesChangelogContent(currentUpdate, changelog)
                                }
                            }
                        }
                        miuixPage("reboot") {
                            RefreshableScreen(mainViewModel, navController, title = stringResource(R.string.reboot)) {
                                RebootContent(rebootViewModel)
                            }
                        }
                        miuixPage("error/{error}") { backStackEntry ->
                            val error = backStackEntry.arguments?.getString("error")
                            ErrorScreen(error!!)
                        }
                    }
                } else {
                    ErrorScreen(mainViewModel.error)
                }
            }
        }
    }

    public override fun onResume() {
        super.onResume()
        if (this::mainListener.isInitialized) {
            if (!isAwaitingResult) {
                mainListener.resume()
            }
        }
    }
}

@Composable
private fun isCurrentEntry(navController: NavController, entry: NavBackStackEntry): Boolean {
    val currentEntry by navController.currentBackStackEntryFlow.collectAsState(
        initial = navController.currentBackStackEntry
    )
    // Covered and predictive-back preview entries must not reset the active operation's state.
    return currentEntry?.id == entry.id && navController.currentBackStackEntry?.id == entry.id
}
