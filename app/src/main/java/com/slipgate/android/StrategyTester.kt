package com.slipgate.android

import android.content.Context
import kotlinx.coroutines.*
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class TestResult(
    val strategy: DpiStrategy,
    val passedCount: Int,
    val totalCount: Int,
    val avgLatencyMs: Long,
    val details: String
)

class StrategyTester(private val context: Context) {

    @Volatile
    var isPaused = false
        private set

    fun togglePause(): Boolean {
        isPaused = !isPaused
        return isPaused
    }

    suspend fun runAllTests(
        strategiesToTest: List<DpiStrategy> = Strategies.presets,
        startIndex: Int = 0,
        onProgress: (String) -> Unit,
        onResult: (TestResult) -> Unit
    ) = withContext(Dispatchers.IO) {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val ciadpiBin = File(nativeDir, "libciadpi.so")

        if (!ciadpiBin.exists()) {
            onProgress("❌ Ошибка: бинарник libciadpi.so не найден в APK!")
            return@withContext
        }

        for (i in startIndex until strategiesToTest.size) {
            while (isPaused && isActive) {
                onProgress("⏸ Пауза на стратегии #${i + 1} (${strategiesToTest[i].name})...")
                delay(400)
            }
            if (!isActive) break

            val strat = strategiesToTest[i]
            onProgress("🔍 Тестируем [${i + 1}/${strategiesToTest.size}]: ${strat.name}...")

            val cmd = mutableListOf(ciadpiBin.absolutePath, "-i", "127.0.0.1", "-p", "1089")
            cmd.addAll(strat.argsList)

            var process: Process? = null
            try {
                process = ProcessBuilder(cmd).redirectErrorStream(true).start()
                delay(350) // Ждём инициализации локального сокета

                var passed = 0
                var totalLatency = 0L
                val detailLogs = mutableListOf<String>()
                val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 1089))

                for ((targetName, urlStr) in Strategies.testTargets) {
                    if (!isActive) break
                    val start = System.currentTimeMillis()
                    val ok = testHttpsOverSocks(urlStr, proxy)
                    val elapsed = System.currentTimeMillis() - start

                    if (ok) {
                        passed++
                        totalLatency += elapsed
                        detailLogs.add("✔ $targetName (${elapsed}ms)")
                    } else {
                        detailLogs.add("✖ $targetName")
                    }
                }

                val avgLat = if (passed > 0) totalLatency / passed else 9999L
                onResult(
                    TestResult(
                        strategy = strat,
                        passedCount = passed,
                        totalCount = Strategies.testTargets.size,
                        avgLatencyMs = avgLat,
                        details = detailLogs.joinToString("  ")
                    )
                )
            } catch (e: Exception) {
                onProgress("⚠️ Сбой на ${strat.name}: ${e.message}")
            } finally {
                process?.destroy()
                delay(150)
            }
        }
        onProgress("✅ Подбор стратегий завершён! Нажми на лучшую стратегию для применения.")
    }

    private fun testHttpsOverSocks(urlString: String, proxy: Proxy): Boolean {
        return try {
            val url = URL(urlString)
            val conn = (url.openConnection(proxy) as HttpsURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android 14; Mobile; rv:128.0)")
            }
            conn.connect()
            val code = conn.responseCode
            conn.disconnect()
            code in 200..499
        } catch (_: Exception) {
            false
        }
    }
}
