package com.fireantzhang.aabinstallhelp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.fireantzhang.aabinstallhelp.ui.AabInstallAppScreen
import com.fireantzhang.aabinstallhelp.ui.AppViewModel
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleOpenIntent(intent)
        setContent {
            AabInstallAppScreen(vm)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPermissions()
    }

    private fun handleOpenIntent(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        lifecycleScope.launch {
            val dest = File(cacheDir, "opened.aab")
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                if (dest.exists() && dest.length() > 0) {
                    vm.addPickedFile(dest.absolutePath)
                }
            } catch (_: Throwable) {
            }
        }
    }
}
