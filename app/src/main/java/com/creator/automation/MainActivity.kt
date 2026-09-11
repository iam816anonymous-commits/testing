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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
                    MainAgentApp(context = this)
                }
            }
        }
    }
}

@Composable
fun MainAgentApp(context: Context) {
    var selectedMainTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Main Navigation Bar
        TabRow(selectedTabIndex = selectedMainTab) {
            Tab(
                selected = selectedMainTab == 0,
                onClick = { selectedMainTab = 0 },
                text = { Text("Agent Command", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedMainTab == 1,
                onClick = { selectedMainTab = 1 },
                text = { Text("Task History", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedMainTab == 2,
                onClick = { selectedMainTab = 2 },
                text = { Text("Capabilities", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedMainTab == 3,
                onClick = { selectedMainTab = 3 },
                text = { Text("Diagnostics", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
        }

        when (selectedMainTab) {
            0 -> AgentCommandScreen(context = context)
            1 -> UserTaskHistoryScreen()
            2 -> UserCapabilitiesScreen(context = context)
            3 -> DiagnosticControlCenter(context = context)
        }
    }
}

@Composable
fun AgentCommandScreen(context: Context) {
    val coroutineScope = rememberCoroutineScope()

    val agentState by AgentCore.agentState.collectAsState()
    val overlayState by AutomationOverlayState.currentState.collectAsState()

    var userCommandText by remember { mutableStateOf("") }
    var currentTaskGoal by remember { mutableStateOf<String?>(null) }
    var lastStepResult by remember { mutableStateOf<AgentStepResult?>(null) }
    var isExecuting by remember { mutableStateOf(false) }
    var isDetailsExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val suggestions = listOf(
        "Open Chrome and search for new Telugu movies",
        "Open YouTube",
        "Go back",
        "Open Settings",
        "Read what is currently on the screen",
        "Turn on the flashlight"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.Top
    ) {
        // App Title
        Text(
            text = "CREATOR AUTOMATION",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "What can I do for you?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Command Box
        OutlinedTextField(
            value = userCommandText,
            onValueChange = { userCommandText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Type a task...") },
            placeholder = { Text("e.g. Open Chrome and search for new Telugu movies") },
            shape = RoundedCornerShape(12.dp),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Command Action Row (Mic & Send Button)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    // Voice input preparation - fills text field with voice placeholder
                    userCommandText = "Open Chrome and search for new Telugu movies"
                },
                modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            ) {
                Text("🎤", fontSize = 18.sp)
            }

            Button(
                onClick = {
                    if (userCommandText.isNotBlank()) {
                        val task = userCommandText.trim()
                        currentTaskGoal = task
                        isExecuting = true
                        coroutineScope.launch {
                            val runtimeManager = AgentRuntimeManager(context)
                            val res = runtimeManager.startOrResumeTaskSession(task)
                            lastStepResult = res
                            isExecuting = false
                        }
                    }
                },
                enabled = userCommandText.isNotBlank() && !isExecuting,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isExecuting) "Executing..." else "Send", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Suggestion Chips
        Text("Suggestions:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
        Spacer(modifier = Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            suggestions.chunked(2).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { chipText ->
                        SuggestionChip(
                            onClick = {
                                userCommandText = chipText
                            },
                            label = { Text(chipText, fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Execution Conversation Card
        if (currentTaskGoal != null) {
            Text("CONVERSATIONAL EXECUTION", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(6.dp))

            // User Message Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("USER", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF1565C0))
                    Text(currentTaskGoal!!, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            // Agent Message Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("AGENT", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF2E7D32))
                    Text("I'll $currentTaskGoal.", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Compact Live Agent Status Box
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE7F6))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Task: ${currentTaskGoal}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(agentState.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF512DA8))
                    }
                    Text("App: ${overlayState.packageName ?: "System"}", fontSize = 11.sp)
                    Text("Current Action: ${overlayState.actionState.name} ${overlayState.targetText ?: ""}", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Expandable "What I'm doing" Section
            Card(
                modifier = Modifier.fillMaxWidth().clickable { isDetailsExpanded = !isDetailsExpanded },
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("What I'm doing", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFE65100))
                        Text(if (isDetailsExpanded) "▲ Hide" else "▼ Expand", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    }

                    if (isDetailsExpanded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Task Intent: ${currentTaskGoal ?: "None"}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text("Active App: ${overlayState.packageName ?: "System"}", fontSize = 10.sp)
                        Text("Selected Target: ${overlayState.targetText ?: overlayState.targetViewId ?: "Auto-resolved node"}", fontSize = 10.sp)
                        Text("Mechanism: ${overlayState.actionState.name}", fontSize = 10.sp)
                        if (lastStepResult != null) {
                            val res = lastStepResult!!
                            Text("Dispatch Result: ${res.actionResult?.status?.name ?: "COMPLETED"}", fontSize = 10.sp)
                            Text("Verification: ${res.verificationStatus.name}", fontSize = 10.sp)
                            if (!res.decisionReason.isNullOrBlank()) {
                                Text("Pipeline Reason: ${res.decisionReason}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (res.nextState == AgentState.COMPLETED) Color(0xFF2E7D32) else Color(0xFFC62828))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Result Cards
            if (lastStepResult != null) {
                val stepRes = lastStepResult!!
                if (stepRes.nextState == AgentState.COMPLETED) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("✓ TASK COMPLETED", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF2E7D32))
                            Text(stepRes.decisionReason ?: "Task completed successfully.", fontSize = 11.sp)
                        }
                    }
                } else if (stepRes.nextState == AgentState.PAUSED || stepRes.nextState == AgentState.FAILED) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("✕ COULDN'T COMPLETE TASK (${stepRes.nextState.name})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFC62828))
                            Text(stepRes.decisionReason ?: stepRes.actionResult?.reason?.name ?: "Unable to verify task completion.", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserTaskHistoryScreen() {
    val logs by AgentRuntimeManager.runtimeLogs.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("TASK HISTORY", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        if (logs.isEmpty()) {
            Text("No prior task history recorded.", fontSize = 11.sp, color = Color.Gray)
        } else {
            logs.take(20).forEach { log ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(log, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun UserCapabilitiesScreen(context: Context) {
    val scanner = remember { DeviceCapabilityScanner(context) }
    var profile by remember { mutableStateOf(scanner.scanDeviceProfile()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("CAPABILITIES & PERMISSIONS CONTROL CENTER", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        Text("${profile.manufacturer} ${profile.model} (API ${profile.apiLevel})", fontSize = 11.sp, color = Color.Gray)
        Text("User-controlled agent capability policy. Toggling OFF prevents the agent from utilizing the capability.", fontSize = 10.sp, color = Color.DarkGray)
        Spacer(modifier = Modifier.height(12.dp))

        profile.capabilityMappings.forEach { cap ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = if (cap.isUsable) Color(0xFFF1F8E9) else Color(0xFFFFF3E0))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(cap.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Switch(
                            checked = cap.isUserEnabled,
                            onCheckedChange = { isChecked ->
                                CapabilityPreferenceStore.setUserEnabled(context, cap.name, isChecked)
                                profile = scanner.scanDeviceProfile()
                            }
                        )
                    }
                    Text("Hardware: ${cap.hardwareState} | Permission: ${cap.permissionState}", fontSize = 10.sp)
                    Text("Service/Operation: ${cap.operationState} | Verification: ${cap.verificationState}", fontSize = 10.sp)

                    val usableText = when {
                        !cap.isUserEnabled -> "USER_DISABLED (Agent blocked from using this capability)"
                        cap.isUsable -> "USABLE (Active & Allowed)"
                        cap.permissionState == PhysicalCapabilityState.BLOCKED -> "PERMISSION_REQUIRED (Grant permission to activate)"
                        else -> "SERVICE_UNAVAILABLE"
                    }
                    val usableColor = when {
                        !cap.isUserEnabled -> Color(0xFFE65100)
                        cap.isUsable -> Color(0xFF2E7D32)
                        else -> Color(0xFFC62828)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Status: $usableText", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = usableColor)
                }
            }
        }
    }
}

@Composable
fun DiagnosticControlCenter(context: Context) {
    val coroutineScope = rememberCoroutineScope()

    val isAccessibilityEnabled by AutomationAccessibilityService.isServiceEnabled.collectAsState()
    val isScreenAuthorized by ScreenObservationProvider.isAuthorized.collectAsState()
    val isCameraRunning by CameraObservationProvider.isCameraRunning.collectAsState()
    val agentState by AgentCore.agentState.collectAsState()
    val overlayState by AutomationOverlayState.currentState.collectAsState()
    val runtimeLogs by AgentRuntimeManager.runtimeLogs.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf("Ready") }

    val scanner = remember { DeviceCapabilityScanner(context) }
    var deviceProfile by remember { mutableStateOf(scanner.scanDeviceProfile()) }

    val testRunner = remember { PhysicalTestRunner(context) }
    var testResults by remember { mutableStateOf<List<TestResult>>(emptyList()) }
    var isTestRunning by remember { mutableStateOf(false) }
    var testProgressText by remember { mutableStateOf("No tests executed yet") }

    val reportGenerator = remember { ReportGenerator(context) }
    var exportedReportFiles by remember { mutableStateOf<Map<String, File>>(emptyMap()) }

    val scrollState = rememberScrollState()

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "JARVIS DIAGNOSTIC & CONTROL CENTER",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Physical Target: ${deviceProfile.manufacturer} ${deviceProfile.model} (API ${deviceProfile.apiLevel})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Diagnostic Overlay State Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EAF6))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE OVERLAY DEBUGGER",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color(0xFF1A237E)
                    )
                    Text(
                        text = overlayState.actionState.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = Color(0xFF283593)
                    )
                }
                if (overlayState.targetBounds != null) {
                    val b = overlayState.targetBounds!!
                    Text(
                        text = "Bounds: [${b.left}, ${b.top}][${b.right}, ${b.bottom}] | Cursor: (${overlayState.cursorX}, ${overlayState.cursorY})",
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Navigation Tabs (Overview, Tests, Capabilities, Sensors, Actuators, Permissions, Failures, Evidence, Logs)
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
                        val failedIds = HashSet<String>()
                        testResults.forEach { res ->
                            if (res.status == PhysicalTestStatus.FAIL || res.status == PhysicalTestStatus.ERROR) {
                                failedIds.add(res.testId)
                            }
                        }
                        val fullSuite = PhysicalTestRegistry.buildSafeValidationSuite()
                        val suite = mutableListOf<PhysicalTestCase>()
                        fullSuite.forEach { case ->
                            if (failedIds.contains(case.testId)) {
                                suite.add(case)
                            }
                        }
                        if (suite.isNotEmpty()) {
                            withContext(Dispatchers.IO) {
                                val reRunResults = testRunner.executeSuite(suite)
                                val updatedMap = HashMap<String, TestResult>()
                                testResults.forEach { res -> updatedMap[res.testId] = res }
                                reRunResults.forEach { res -> updatedMap[res.testId] = res }
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
