package com.slipgate.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var onVpnGranted: (() -> Unit)? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onVpnGranted?.invoke()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("slipgate_prefs", Context.MODE_PRIVATE)
        val tester = StrategyTester(this)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var currentTab by remember { mutableIntStateOf(0) }
                var isRunning by remember { mutableStateOf(SlipgateVpnService.isRunning) }
                var selectedId by remember {
                    mutableStateOf(prefs.getString("selected_strategy", "sg_1") ?: "sg_1")
                }
                var autoStart by remember {
                    mutableStateOf(prefs.getBoolean("auto_start", false))
                }
                var whitelistMode by remember {
                    mutableStateOf(prefs.getBoolean("whitelist_mode", true))
                }
                var customArgs by remember {
                    mutableStateOf(prefs.getString("custom_args", "") ?: "")
                }
                var statusText by remember { mutableStateOf("Готов к запуску") }
                var isTesting by remember { mutableStateOf(false) }
                var isPaused by remember { mutableStateOf(false) }
                val testResults = remember { mutableStateListOf<TestResult>() }

                var selectedApps by remember {
                    mutableStateOf(AppListManager.getSelectedApps(this@MainActivity))
                }
                var installedApps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
                var searchQuery by remember { mutableStateOf("") }

                val scope = rememberCoroutineScope()
                var testJob by remember { mutableStateOf<Job?>(null) }

                LaunchedEffect(currentTab) {
                    if (currentTab == 1 && installedApps.isEmpty()) {
                        installedApps = withContext(Dispatchers.IO) {
                            AppListManager.getInstalledApps(this@MainActivity, selectedApps)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0D0F17))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "SLIPGATE 1.8 ANDROID",
                                color = Color(0xFF8C62FF),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "No-Root DPI Desync & Split Tunnel",
                                color = Color(0xFF8A94A6),
                                fontSize = 12.sp
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Автозапуск", color = Color.White, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = autoStart,
                                onCheckedChange = {
                                    autoStart = it
                                    prefs.edit().putBoolean("auto_start", it).apply()
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (isRunning) {
                                controlVpn(false)
                                isRunning = false
                                statusText = "Обход остановлен"
                            } else {
                                requestAndStartVpn {
                                    isRunning = true
                                    statusText = "Обход активен (${selectedApps.size} прил.)"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) Color(0xFFD32F2F) else Color(0xFF00C853)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = if (isRunning) "ОСТАНОВИТЬ ОБХОД DPI" else "ЗАПУСТИТЬ ОБХОД БЛОКИРОВОК",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TabRow(
                        selectedTabIndex = currentTab,
                        containerColor = Color(0xFF171B26),
                        contentColor = Color(0xFF8C62FF)
                    ) {
                        Tab(
                            selected = currentTab == 0,
                            onClick = { currentTab = 0 },
                            text = { Text("⚡ Стратегии") }
                        )
                        Tab(
                            selected = currentTab == 1,
                            onClick = { currentTab = 1 },
                            text = { Text("📱 Приложения (${selectedApps.size})") }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (currentTab == 0) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF171B26)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = statusText,
                                    color = Color(0xFF00E676),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (!isTesting) {
                                                testResults.clear()
                                                isTesting = true
                                                isPaused = false
                                                testJob = scope.launch {
                                                    tester.runAllTests(
                                                        onProgress = { statusText = it },
                                                        onResult = { res ->
                                                            testResults.add(res)
                                                            testResults.sortWith(
                                                                compareByDescending<TestResult> { it.passedCount }
                                                                    .thenBy { it.avgLatencyMs }
                                                            )
                                                        }
                                                    )
                                                    isTesting = false
                                                }
                                            } else {
                                                testJob?.cancel()
                                                isTesting = false
                                                statusText = "Тестирование отменено"
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF651FFF)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(if (isTesting) "Стоп тест" else "⚡ Тест стратегий")
                                    }

                                    if (isTesting) {
                                        OutlinedButton(
                                            onClick = { isPaused = tester.togglePause() },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(if (isPaused) "▶ Продолжить" else "⏸ Пауза")
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = customArgs,
                            onValueChange = {
                                customArgs = it
                                prefs.edit().putString("custom_args", it).apply()
                            },
                            label = { Text("Свои флаги ciadpi (пусто = использовать пресет)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(Strategies.presets) { strat ->
                                val isSelected = strat.id == selectedId
                                val result = testResults.find { it.strategy.id == strat.id }

                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Color(0xFF23293A) else Color(0xFF151821)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = if (isSelected) 2.dp else 0.dp,
                                            color = if (isSelected) Color(0xFF8C62FF) else Color.Transparent,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            selectedId = strat.id
                                            prefs.edit().putString("selected_strategy", strat.id).apply()
                                            statusText = "Выбрана: ${strat.name}"
                                            if (isRunning) restartVpn()
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = strat.name,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            if (result != null) {
                                                val badgeColor = when {
                                                    result.passedCount == result.totalCount -> Color(0xFF00E676)
                                                    result.passedCount >= 3 -> Color(0xFFFFD600)
                                                    else -> Color(0xFFFF5252)
                                                }
                                                Text(
                                                    text = "${result.passedCount}/${result.totalCount} (${result.avgLatencyMs}ms)",
                                                    color = badgeColor,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = strat.description,
                                            color = Color(0xFF9E9E9E),
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = strat.rawArgs,
                                            color = Color(0xFF7C4DFF),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF171B26)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (whitelistMode) "Режим: Только выбранные" else "Режим: Все, кроме выбранных",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Switch(
                                    checked = whitelistMode,
                                    onCheckedChange = {
                                        whitelistMode = it
                                        prefs.edit().putBoolean("whitelist_mode", it).apply()
                                        if (isRunning) restartVpn()
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Поиск приложения") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val filteredApps = installedApps.filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                                    it.packageName.contains(searchQuery, ignoreCase = true)
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredApps) { app ->
                                val isChecked = app.packageName in selectedApps
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isChecked) Color(0xFF23293A) else Color(0xFF151821)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val updated = selectedApps.toMutableSet()
                                            if (isChecked) updated.remove(app.packageName)
                                            else updated.add(app.packageName)
                                            selectedApps = updated
                                            AppListManager.saveSelectedApps(this@MainActivity, updated)
                                            if (isRunning) restartVpn()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = app.name,
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = app.packageName,
                                                color = Color(0xFF78909C),
                                                fontSize = 11.sp
                                            )
                                        }
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = null
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestAndStartVpn(onStarted: () -> Unit) {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            onVpnGranted = {
                controlVpn(true)
                onStarted()
            }
            vpnPermissionLauncher.launch(intent)
        } else {
            controlVpn(true)
            onStarted()
        }
    }

    private fun restartVpn() {
        controlVpn(false)
        controlVpn(true)
    }

    private fun controlVpn(start: Boolean) {
        val intent = Intent(this, SlipgateVpnService::class.java).apply {
            action = if (start) SlipgateVpnService.ACTION_START else SlipgateVpnService.ACTION_STOP
        }
        if (start && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
