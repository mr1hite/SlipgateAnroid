package com.slipgate.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
        if (intent.action in validActions) {
            val prefs = context.getSharedPreferences("slipgate_prefs", Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean("auto_start", false)

            if (autoStart && VpnService.prepare(context) == null) {
                val serviceIntent = Intent(context, SlipgateVpnService::class.java).apply {
                    action = SlipgateVpnService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
