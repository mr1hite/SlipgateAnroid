package com.slipgate.android

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

data class AppEntry(
    val name: String,
    val packageName: String,
    val isSystem: Boolean
)

object AppListManager {

    // Пакеты, которые выбираются автоматически при первом запуске
    val defaultTargetPackages = setOf(
        "com.google.android.youtube",
        "app.revanced.android.youtube",
        "app.rvx.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.discord",
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.opera.browser",
        "com.brave.browser",
        "org.telegram.messenger",
        "org.thunderdog.challegram",
        "tv.twitch.android.app",
        "com.spotify.music"
    )

    fun getInstalledApps(context: Context, selectedPackages: Set<String>): List<AppEntry> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        return apps
            .filter { app ->
                // Показываем только приложения с иконкой запуска или из целевого списка
                pm.getLaunchIntentForPackage(app.packageName) != null ||
                        app.packageName in defaultTargetPackages
            }
            .filter { it.packageName != context.packageName }
            .map { app ->
                AppEntry(
                    name = pm.getApplicationLabel(app).toString(),
                    packageName = app.packageName,
                    isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedWith(
                compareByDescending<AppEntry> { it.packageName in selectedPackages }
                    .thenBy { it.isSystem }
                    .thenBy { it.name.lowercase() }
            )
    }

    fun getSelectedApps(context: Context): Set<String> {
        val prefs = context.getSharedPreferences("slipgate_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getStringSet("selected_apps", null)
        if (saved != null) return saved

        // Если запускаем впервые — сохраняем дефолтный набор
        prefs.edit().putStringSet("selected_apps", defaultTargetPackages).apply()
        return defaultTargetPackages
    }

    fun saveSelectedApps(context: Context, packages: Set<String>) {
        val prefs = context.getSharedPreferences("slipgate_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("selected_apps", packages).apply()
    }
}
