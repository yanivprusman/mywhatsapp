package com.automatelinux.mywhatsapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts

// Thin Android launcher — all UI lives in the shared commonMain App() composable.
class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        WantedWatcherService.sync(this)
        askBatteryExemption()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        Alerts.onChange = { WantedWatcherService.sync(this) }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            WantedWatcherService.sync(this)
            askBatteryExemption()
        }
        setContent {
            App(AppConfig(BuildConfig.API_BASE_URL, BuildConfig.API_TOKEN))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent?.getStringExtra(WantedWatcherService.EXTRA_JID)?.let { Launch.openChat.value = it }
    }

    /**
     * Without the battery exemption Doze cuts the watcher's network for hours and
     * alerts arrive late or not at all. Asked once; the answer is Android's to keep.
     */
    @SuppressLint("BatteryLife")
    private fun askBatteryExemption() {
        if (!Alerts.enabled) return
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        val settings = appSettings()
        if (settings.getBoolean("askedBattery", false)) return
        settings.putBoolean("askedBattery", true)
        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
    }
}
