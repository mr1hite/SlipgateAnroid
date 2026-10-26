package com.slipgate.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.File

class SlipgateVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var ciadpiProcess: Process? = null

    companion object {
        const val ACTION_START = "com.slipgate.android.START"
        const val ACTION_STOP = "com.slipgate.android.STOP"

        @Volatile
        var isRunning = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSlipgate()
            return START_NOT_STICKY
        }
        startSlipgate()
        return START_STICKY
    }

    private fun startSlipgate() {
        if (isRunning) return
        createNotificationChannel()

        val prefs = getSharedPreferences("slipgate_prefs", Context.MODE_PRIVATE)
        val selectedId = prefs.getString("selected_strategy", "sg_1") ?: "sg_1"
        val customArgs = prefs.getString("custom_args", "")?.trim() ?: ""
        val whitelistMode = prefs.getBoolean("whitelist_mode", true)
        val selectedApps = AppListManager.getSelectedApps(this)

        val strategy = Strategies.presets.find { it.id == selectedId } ?: Strategies.presets.first()
        val activeArgs = if (customArgs.isNotEmpty()) {
            customArgs.split("\\s+".toRegex())
        } else {
            strategy.argsList
        }

        startForeground(
            1,
            buildNotification("Стратегия: ${strategy.name} • Приложений: ${selectedApps.size}")
        )

        // Запуск нативного ciadpi на 127.0.0.1:1080
        val nativeDir = applicationInfo.nativeLibraryDir
        val ciadpiBin = File(nativeDir, "libciadpi.so")
        val cmd = mutableListOf(ciadpiBin.absolutePath, "-i", "127.0.0.1", "-p", "1080")
        cmd.addAll(activeArgs)

        try {
            ciadpiProcess = ProcessBuilder(cmd).redirectErrorStream(true).start()
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return
        }

        val builder = Builder()
            .setSession("Slipgate DPI")
            .setMtu(1500)
            .addAddress("10.10.10.1", 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")

        if (whitelistMode && selectedApps.isNotEmpty()) {
            for (pkg in selectedApps) {
                if (pkg == packageName) continue
                try {
                    packageManager.getPackageInfo(pkg, 0)
                    builder.addAllowedApplication(pkg)
                } catch (_: PackageManager.NameNotFoundException) {}
            }
        } else {
            try {
                builder.addDisallowedApplication(packageName)
            } catch (_: Exception) {}

            for (pkg in selectedApps) {
                try {
                    packageManager.getPackageInfo(pkg, 0)
                    builder.addDisallowedApplication(pkg)
                } catch (_: PackageManager.NameNotFoundException) {}
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        vpnInterface = builder.establish()
        isRunning = true
    }

    private fun stopSlipgate() {
        isRunning = false
        try {
            ciadpiProcess?.destroy()
            ciadpiProcess = null
            vpnInterface?.close()
            vpnInterface = null
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopSlipgate()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                "slipgate_vpn",
                "Slipgate DPI Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "slipgate_vpn")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Slipgate DPI Bypass")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_secure)
            .setContentIntent(pi)
            .build()
    }
}
