package com.creator.automation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scheduler = AutomationScheduler(this)
        val coroutineScope = kotlinx.coroutines.MainScope()
        coroutineScope.launch(Dispatchers.IO) {
            scheduler.restoreAllSchedules()
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AgentControlWorkbenchScreen(context = this)
                }
            }
        }
    }
}

@Composable
fun AgentControlWorkbenchScreen(context: Context) {
    val coroutineScope = rememberCoroutineScope()

    val isAccessibilityEnabled by AutomationAccessibilityService.isServiceEnabled.collectAsState()
    val isScreenAuthorized by ScreenObservationProvider.isAuthorized.collectAsState()
    val isCameraRunning by CameraObservationProvider.isCameraRunning.collectAsState()
    val agentState by AgentCore.agentState.collectAsState()
    val activeSession by AgentRuntimeManager.activeSession.collectAsState()
    val overlayState by AutomationOverlayState.currentState.collectAsState()
    val runtimeLogs by AgentRuntimeManager.runtimeLogs.collectAsState()

    var taskInputText by remember { mutableStateOf("Open Chrome and search for Telugu movies") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf("Ready") }

    val runtimeManager = remember { AgentRuntimeManager(context) }
    val scanner = remember { DeviceCapabilityScanner(context) }
    var deviceProfile by remember { mutableStateOf(scanner.scanDeviceProfile()) }

    val testRunner = remember { PhysicalTestRunner(context) }
    var testResults by remember { mutableStateOf<List<TestResult>>(emptyList()) }
    var isTestRunning by remember { mutableStateOf(false) }
    var testProgressText by remember { mutableStateOf("No tests executed yet") }

    val reportGenerator = remember { ReportGenerator(context) }
    var exportedReportFiles by remember { mutableStateOf<Map<String, File>>(emptyMap()) }

    val appResolver = remember { AppResolver(context) }
    var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var selectedAppPackage by remember { mutableStateOf<String?>(null) }
    var isAppDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val apps = appResolver.getInstalledLauncherApps()
            installedApps = apps
            if (apps.isNotEmpty()) {
                selectedAppPackage = apps.first().packageName
            }
        }
    }

    // Permission launcher for Camera & Vibrate permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        deviceProfile = scanner.scanDeviceProfile()
    }

    // Screen capture launcher for MediaProjection
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenObservationProvider.setScreenCaptureAuthorization(
                context = context,
                resultCode = result.resultCode,
                data = result.data!!
            )
            deviceProfile = scanner.scanDeviceProfile()
        }
    }

    // Automatic permission interlock check on startup
    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.VIBRATE,
                android.Manifest.permission.WAKE_LOCK
            )
        )
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "JARVIS AGENT CONTROL WORKBENCH",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Physical Device: ${deviceProfile.manufacturer} ${deviceProfile.model} (API ${deviceProfile.apiLevel})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 1. LIVE EXECUTION STATUS PANEL
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE EXECUTION STATUS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color(0xFF80D8FF)
                    )
                    Surface(
                        color = when (agentState) {
                            AgentState.EXECUTING -> Color(0xFF2E7D32)
                            AgentState.OBSERVING, AgentState.RESOLVING, AgentState.VERIFYING -> Color(0xFF1565C0)
                            AgentState.COMPLETED -> Color(0xFF00C853)
                            AgentState.PAUSED, AgentState.NEEDS_USER_INPUT -> Color(0xFFE65100)
                            AgentState.FAILED, AgentState.CANCELLED -> Color(0xFFC62828)
                            else -> Color(0xFF424242)
                        },
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = agentState.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                val currentTaskDesc = activeSession?.taskDescription ?: "No task currently running"
                Text("Active Task: $currentTaskDesc", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)

                if (overlayState.targetText != null || overlayState.targetBounds != null) {
                    Text(
                        text = "Target: '${overlayState.targetText ?: "UI element"}' ${overlayState.targetBounds?.let { "[${it.left},${it.top}][${it.right},${it.bottom}]" } ?: ""}",
                        fontSize = 10.sp,
                        color = Color(0xFFB0BEC5)
                    )
                }

                if (activeSession?.cancellationReason != null) {
                    Text("Reason: ${activeSession?.cancellationReason}", fontSize = 10.sp, color = Color(0xFFFF8A80))
                } else if (!isAccessibilityEnabled) {
                    Text("Blocker: Accessibility Service is DISABLED in Settings", fontSize = 10.sp, color = Color(0xFFFF8A80))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. NATURAL LANGUAGE COMMAND CENTER
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("AGENT COMMAND CENTER", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF212121))
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = taskInputText,
                    onValueChange = { taskInputText = it },
                    label = { Text("Natural Language Command", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                statusText = "Dispatching command: '$taskInputText'"
                                runtimeManager.startOrResumeTaskSession(taskInputText)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                    ) {
                        Text("RUN TASK", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                statusText = "User pressed STOP"
                                runtimeManager.cancelActiveSession("User clicked STOP button")
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                    ) {
                        Text("STOP", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. QUICK SYSTEM CONTROLS & HARDWARE ACTUATORS
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFECEFF1))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("QUICK CAPABILITY CONTROLS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF37474F))
                Spacer(modifier = Modifier.height(6.dp))

                // Navigation Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = { coroutineScope.launch { runtimeManager.startOrResumeTaskSession("Press Home") } }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp)) {
                        Text("HOME", fontSize = 10.sp)
                    }
                    Button(onClick = { coroutineScope.launch { runtimeManager.startOrResumeTaskSession("Press Back") } }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp)) {
                        Text("BACK", fontSize = 10.sp)
                    }
                    Button(onClick = { coroutineScope.launch { runtimeManager.startOrResumeTaskSession("Open Recents") } }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp)) {
                        Text("RECENTS", fontSize = 10.sp)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Perception Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = { coroutineScope.launch { runtimeManager.startOrResumeTaskSession("Read visible UI") } }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64))) {
                        Text("READ SCREEN", fontSize = 10.sp)
                    }
                    Button(onClick = { coroutineScope.launch { runtimeManager.startOrResumeTaskSession("Scroll down") } }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64))) {
                        Text("SCROLL DOWN", fontSize = 10.sp)
                    }
                    Button(onClick = { coroutineScope.launch { runtimeManager.startOrResumeTaskSession("Scroll up") } }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64))) {
                        Text("SCROLL UP", fontSize = 10.sp)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Hardware Actuators Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = { coroutineScope.launch { runtimeManager.startOrResumeTaskSession("Turn on flashlight") } }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57F17))) {
                        Text("FLASH ON", fontSize = 10.sp)
                    }
                    Button(onClick = { coroutineScope.launch { runtimeManager.startOrResumeTaskSession("Turn off flashlight") } }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))) {
                        Text("FLASH OFF", fontSize = 10.sp)
                    }
                    Button(onClick = { coroutineScope.launch { runtimeManager.startOrResumeTaskSession("Vibrate phone") } }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A148C))) {
                        Text("HAPTIC", fontSize = 10.sp)
                    }
                    Button(onClick = { coroutineScope.launch { runtimeManager.startOrResumeTaskSession("Mute audio") } }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF880E4F))) {
                        Text("MUTE AUDIO", fontSize = 10.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. DYNAMIC APP LAUNCHER
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("INSTALLED APP LAUNCHER", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1B5E20))
                Spacer(modifier = Modifier.height(4.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val appLabel = installedApps.find { it.packageName == selectedAppPackage }?.appLabel ?: "Select Application"
                    Button(onClick = { isAppDropdownExpanded = true }, modifier = Modifier.weight(1f)) {
                        Text("App: $appLabel", fontSize = 11.sp)
                    }

                    DropdownMenu(
                        expanded = isAppDropdownExpanded,
                        onDismissRequest = { isAppDropdownExpanded = false }
                    ) {
                        installedApps.take(15).forEach { app ->
                            DropdownMenuItem(
                                text = { Text("${app.appLabel} (${app.packageName})", fontSize = 11.sp) },
                                onClick = {
                                    selectedAppPackage = app.packageName
                                    isAppDropdownExpanded = false
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val pkg = selectedAppPackage
                            val targetLabel = installedApps.find { it.packageName == pkg }?.appLabel ?: pkg
                            if (targetLabel != null) {
                                coroutineScope.launch {
                                    runtimeManager.startOrResumeTaskSession("Open $targetLabel")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Text("OPEN APP", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // System Services Setup Buttons
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isAccessibilityEnabled) {
                Button(onClick = { openAccessibilitySettings(context) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))) {
                    Text("Enable Accessibility", fontSize = 10.sp)
                }
            }
            if (!isScreenAuthorized) {
                Button(
                    onClick = {
                        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                        if (projectionManager != null) {
                            screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                ) {
                    Text("Grant Screen Share", fontSize = 10.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 5. DEVELOPER / DIAGNOSTIC TABS
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
            val tabTitles = listOf(
                "Overview", "Tests", "Capabilities", "Sensors",
                "Actuators", "Permissions", "Failures", "Evidence", "Logs"
            )
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontSize = 11.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedTab) {
            0 -> OverviewScreen(
                deviceProfile = deviceProfile,
                isAccessibilityEnabled = isAccessibilityEnabled,
                isScreenAuthorized = isScreenAuthorized,
                isCameraRunning = isCameraRunning,
                agentState = agentState,
                testResults = testResults,
                onRefreshProfile = { deviceProfile = scanner.scanDeviceProfile() },
                onOpenAccessibility = { openAccessibilitySettings(context) },
                onRequestScreenShare = {
                    val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                    if (projectionManager != null) {
                        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
                    }
                }
            )

            1 -> TestsScreen(
                isTestRunning = isTestRunning,
                testProgressText = testProgressText,
                testResults = testResults,
                onRunAllSafeTests = {
                    coroutineScope.launch {
                        isTestRunning = true
                        statusText = "Running Safe Physical Test Suite..."
                        withContext(Dispatchers.IO) {
                            val suite = PhysicalTestRegistry.buildSafeValidationSuite()
                            testResults = testRunner.executeSuite(suite) { done, total, curr ->
                                testProgressText = "Executed $done/$total: ${curr.testId} (${curr.status.name})"
                            }
                            exportedReportFiles = reportGenerator.generateAndExportReports(deviceProfile, testResults)
                        }
                        isTestRunning = false
                        statusText = "Test suite completed. ${testResults.count { it.status == PhysicalTestStatus.PASS }}/${testResults.size} PASSED."
                    }
                },
                onRunFailedTests = {
                    coroutineScope.launch {
                        isTestRunning = true
                        val failedIds = testResults.filter { it.status == PhysicalTestStatus.FAIL || it.status == PhysicalTestStatus.ERROR }.map { it.testId }.toSet()
                        val suite = PhysicalTestRegistry.buildSafeValidationSuite().filter { failedIds.contains(it.testId) }
                        if (suite.isNotEmpty()) {
                            withContext(Dispatchers.IO) {
                                val reRunResults = testRunner.executeSuite(suite)
                                val updatedMap = testResults.associateBy { it.testId }.toMutableMap()
                                reRunResults.forEach { updatedMap[it.testId] = it }
                                testResults = updatedMap.values.toList()
                                exportedReportFiles = reportGenerator.generateAndExportReports(deviceProfile, testResults)
                            }
                        }
                        isTestRunning = false
                    }
                }
            )

            2 -> CapabilitiesScreen(capabilities = deviceProfile.capabilityMappings)
            3 -> SensorsScreen(sensors = deviceProfile.sensors)
            4 -> ActuatorsScreen(actuators = deviceProfile.actuators)
            5 -> PermissionsScreen(permissions = deviceProfile.permissions)
            6 -> FailuresScreen(testResults = testResults)
            7 -> EvidenceScreen(exportedFiles = exportedReportFiles)
            8 -> LogsScreen(logs = runtimeLogs)
        }
    }
}

@Composable
fun OverviewScreen(
    deviceProfile: DeviceProfile,
    isAccessibilityEnabled: Boolean,
    isScreenAuthorized: Boolean,
    isCameraRunning: Boolean,
    agentState: AgentState,
    testResults: List<TestResult>,
    onRefreshProfile: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onRequestScreenShare: () -> Unit
) {
    Column {
        Text("PHYSICAL DEVICE METRICS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("RAM: ${deviceProfile.availableMemoryMb} MB Free / ${deviceProfile.totalMemoryMb} MB Total", fontSize = 12.sp)
                Text("Storage: ${deviceProfile.availableStorageMb} MB Free / ${deviceProfile.totalStorageMb} MB Total", fontSize = 12.sp)
                Text("Screen: ${deviceProfile.screenWidthPx}x${deviceProfile.screenHeightPx} px @ ${deviceProfile.screenDensityDpi} dpi", fontSize = 12.sp)
                Text("Battery: ${deviceProfile.batteryLevel}% (${if (deviceProfile.isBatteryCharging) "Charging" else "Discharging"})", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("SYSTEM PERCEPTION STATUS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                StatusRow("Accessibility Automation", if (isAccessibilityEnabled) "AVAILABLE" else "DISABLED", isAccessibilityEnabled)
                StatusRow("MediaProjection Capture", if (isScreenAuthorized) "AVAILABLE" else "NOT_GRANTED", isScreenAuthorized)
                StatusRow("Camera Vision (CameraX)", if (isCameraRunning) "RUNNING" else "INACTIVE", isCameraRunning)
                StatusRow("Agent Loop State", agentState.name, agentState == AgentState.EXECUTING || agentState == AgentState.IDLE)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRefreshProfile, modifier = Modifier.weight(1f)) {
                Text("Re-Scan Profile", fontSize = 11.sp)
            }
            if (!isAccessibilityEnabled) {
                Button(onClick = onOpenAccessibility, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))) {
                    Text("Accessibility Settings", fontSize = 11.sp)
                }
            }
            if (!isScreenAuthorized) {
                Button(onClick = onRequestScreenShare, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))) {
                    Text("Grant Screen", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun StatusRow(label: String, value: String, isOk: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isOk) Color(0xFF2E7D32) else Color(0xFFC62828))
    }
}

@Composable
fun TestsScreen(
    isTestRunning: Boolean,
    testProgressText: String,
    testResults: List<TestResult>,
    onRunAllSafeTests: () -> Unit,
    onRunFailedTests: () -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRunAllSafeTests, enabled = !isTestRunning, modifier = Modifier.weight(1f)) {
                Text("Run Safe Physical Tests", fontSize = 11.sp)
            }
            OutlinedButton(onClick = onRunFailedTests, enabled = !isTestRunning && testResults.any { it.status == PhysicalTestStatus.FAIL }, modifier = Modifier.weight(1f)) {
                Text("Re-Run Failures", fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(testProgressText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
        Spacer(modifier = Modifier.height(8.dp))

        testResults.forEach { res ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${res.testId}: ${res.testName}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(res.status.name, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = when (res.status) {
                            PhysicalTestStatus.PASS -> Color(0xFF2E7D32)
                            PhysicalTestStatus.BLOCKED -> Color(0xFFE65100)
                            else -> Color(0xFFC62828)
                        })
                    }
                    Text("Mechanism: ${res.mechanismUsed} | Candidates: ${res.candidateCount}", fontSize = 10.sp)
                    Text("Actual: ${res.actualOutcome}", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun CapabilitiesScreen(capabilities: List<CapabilityMapping>) {
    Column {
        Text("DISCOVERED CAPABILITY MATRIX", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        capabilities.forEach { cap ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(cap.name, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("Hardware: ${cap.hardwareState} | Permission: ${cap.permissionState}", fontSize = 10.sp)
                    Text("Operation: ${cap.operationState} | Verification: ${cap.verificationState}", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun SensorsScreen(sensors: List<DiscoveredSensor>) {
    Column {
        Text("DISCOVERED PHYSICAL SENSORS (${sensors.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        sensors.forEach { sensor ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("${sensor.name} (${sensor.category.name})", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("Vendor: ${sensor.vendor} | Version: ${sensor.version} | Power: ${sensor.power}mA", fontSize = 10.sp)
                    Text("Resolution: ${sensor.resolution} | MaxRange: ${sensor.maximumRange}", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun ActuatorsScreen(actuators: List<DiscoveredActuator>) {
    Column {
        Text("DISCOVERED DEVICE CONTROLS (${actuators.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        actuators.forEach { act ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(act.name, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(act.availabilityState.name, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = if (act.isPresent) Color(0xFF2E7D32) else Color(0xFFC62828))
                    }
                    Text("Observable: ${act.isObservable} | Controllable: ${act.isControllable} | Verifiable: ${act.isVerifiable}", fontSize = 10.sp)
                    Text("Required Permission: ${act.permissionRequired ?: "None"} | Allowed: ${act.isCurrentlyAllowed}", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun PermissionsScreen(permissions: List<PermissionState>) {
    Column {
        Text("PERMISSION & CAPABILITY INVENTORY", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        permissions.forEach { perm ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(perm.permission.substringAfterLast('.'), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(if (perm.isGranted) "GRANTED" else "DENIED", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = if (perm.isGranted) Color(0xFF2E7D32) else Color(0xFFC62828))
                    }
                    Text("Declared: ${perm.isDeclared} | Runtime Dangerous: ${perm.isRuntimeApplicable} | Usable: ${perm.isUsable}", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun FailuresScreen(testResults: List<TestResult>) {
    val failures = testResults.filter { it.status == PhysicalTestStatus.FAIL || it.status == PhysicalTestStatus.ERROR || it.status == PhysicalTestStatus.BLOCKED }
    Column {
        Text("FAILED / BLOCKED PHYSICAL TESTS (${failures.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        if (failures.isEmpty()) {
            Text("Zero physical test failures recorded.", fontSize = 11.sp, color = Color(0xFF2E7D32))
        } else {
            failures.forEach { fail ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("${fail.testId}: ${fail.testName}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text("Status: ${fail.status} | Failure Reason: ${fail.failureReason ?: "None"}", fontSize = 10.sp, color = Color(0xFFC62828))
                        Text("Evidence: ${fail.evidence}", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun EvidenceScreen(exportedFiles: Map<String, File>) {
    Column {
        Text("EXPORTED PHYSICAL VALIDATION ARTIFACTS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        if (exportedFiles.isEmpty()) {
            Text("No artifacts exported yet. Run tests to generate physical validation reports.", fontSize = 11.sp)
        } else {
            exportedFiles.forEach { (name, file) ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(name, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text("Path: ${file.absolutePath} (${file.length()} bytes)", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun LogsScreen(logs: List<String>) {
    Column {
        Text("AGENT RUNTIME LOGS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        if (logs.isEmpty()) {
            Text("No runtime activity logs available.", fontSize = 11.sp)
        } else {
            logs.take(25).forEach { line ->
                Text(line, fontSize = 10.sp)
            }
        }
    }
}

fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
