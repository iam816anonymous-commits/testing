package com.creator.automation

import android.Manifest
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restore scheduled WorkManager jobs upon launch
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
                    CreatorAutomationScreen(context = this)
                }
            }
        }
    }
}

@Composable
fun CreatorAutomationScreen(context: Context) {
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val isAccessibilityEnabled by AutomationAccessibilityService.isServiceEnabled.collectAsState()
    val activePackageName by AutomationAccessibilityService.activePackageName.collectAsState()
    val isTrainingActive by TrainingSessionManager.isTrainingActive.collectAsState()

    val agentState by AgentCore.agentState.collectAsState()

    // Persistent Agent Runtime States
    val activeSession by AgentRuntimeManager.activeSession.collectAsState()
    val runtimeLogs by AgentRuntimeManager.runtimeLogs.collectAsState()

    // Screen Perception States
    val isScreenAuthorized by ScreenObservationProvider.isAuthorized.collectAsState()
    val lastVisualSignature by ScreenObservationProvider.lastVisualSignature.collectAsState()
    val lastFrameWidth by ScreenObservationProvider.lastFrameWidth.collectAsState()
    val lastFrameHeight by ScreenObservationProvider.lastFrameHeight.collectAsState()
    val lastVisualChangeState by ScreenObservationProvider.lastVisualChangeState.collectAsState()

    // Camera Perception States
    val isCameraPermissionGranted by CameraObservationProvider.isPermissionGranted.collectAsState()
    val isCameraEnabled by CameraObservationProvider.isCameraEnabled.collectAsState()
    val isCameraRunning by CameraObservationProvider.isCameraRunning.collectAsState()
    val lastCameraSignature by CameraObservationProvider.lastCameraSignature.collectAsState()
    val lastCameraWidth by CameraObservationProvider.lastCameraWidth.collectAsState()
    val lastCameraHeight by CameraObservationProvider.lastCameraHeight.collectAsState()
    val lastCameraChangeState by CameraObservationProvider.lastCameraChangeState.collectAsState()
    val cameraFrameCount by CameraObservationProvider.frameCount.collectAsState()
    val lastAnalysisDurationMs by CameraObservationProvider.lastAnalysisDurationMs.collectAsState()
    val averageAnalysisDurationMs by CameraObservationProvider.averageAnalysisDurationMs.collectAsState()

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenObservationProvider.setScreenCaptureAuthorization(
                context = context,
                resultCode = result.resultCode,
                data = result.data!!
            )
        } else {
            ScreenObservationProvider.stopProjectionSession()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        CameraObservationProvider.updatePermissionStatus(context)
        if (isGranted) {
            CameraObservationProvider.enableCameraPerception(context, lifecycleOwner)
        } else {
            CameraObservationProvider.disableCameraPerception()
        }
    }

    var customUrl by remember { mutableStateOf("") }
    var userTaskInput by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Ready") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var globalAutonomousEnabled by remember { mutableStateOf(true) }

    val db = remember { AppDatabase.getDatabase(context) }
    val obsDao = db.observationDao()
    val scheduleDao = db.scheduleDao()
    val sessionDao = db.agentSessionDao()
    val learnedWfDao = db.learnedWorkflowDao()
    val scheduler = remember { AutomationScheduler(context, scheduleDao) }
    val sessionManager = remember { TrainingSessionManager(learnedWfDao) }
    val runtimeManager = remember { AgentRuntimeManager(context) }
    val capabilityProbe = remember { DeviceCapabilityProbe(context) }

    val sessionsFlow = remember { sessionDao.getAllSessionsFlow() }
    val allSessions by sessionsFlow.collectAsState(initial = emptyList())

    val observationsFlow = remember { obsDao.getAllObservations() }
    val observations by observationsFlow.collectAsState(initial = emptyList())

    val schedulesFlow = remember { scheduleDao.getAllSchedulesFlow() }
    val schedules by schedulesFlow.collectAsState(initial = emptyList())

    val learnedWorkflowsFlow = remember { learnedWfDao.getAllWorkflowsFlow() }
    val learnedWorkflows by learnedWorkflowsFlow.collectAsState(initial = emptyList())

    val workflowsState = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(Unit) {
        DefaultWorkflows.getAllWorkflows().forEach { wf ->
            workflowsState[wf.id] = wf.enabled
        }
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
            text = "Creator Automation V2.0",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Navigation Tabs
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Agent Runtime") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Workflows") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Learning") }
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("Diagnostics") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Structured User Intervention Banner
        if (agentState == AgentState.NEEDS_USER_INPUT) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⚠️ USER INTERVENTION REQUIRED",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE65100)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "The agent paused automation because user action is required (e.g. login, permission, or ambiguous UI). Please complete the action on screen, then tap Resume below.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                runtimeManager.resumeRuntime()
                                statusText = "Resumed post-user intervention"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                    ) {
                        Text("I Have Completed Action — Resume Agent")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Accessibility, Screen & Camera Perception Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isAccessibilityEnabled) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Accessibility Service:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isAccessibilityEnabled) "🟢 ENABLED" else "🔴 DISABLED",
                        fontWeight = FontWeight.Bold,
                        color = if (isAccessibilityEnabled) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }

                if (!isAccessibilityEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "To enable agent automation & learning, tap below, locate 'Creator Automation' in Accessibility settings, and toggle it ON.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { openAccessibilitySettings(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Open Accessibility Settings")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Screen Capture Perception:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isScreenAuthorized) "🟢 AUTHORIZED" else "🟠 NOT_GRANTED",
                        fontWeight = FontWeight.Bold,
                        color = if (isScreenAuthorized) Color(0xFF2E7D32) else Color(0xFFE65100)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                            if (projectionManager != null) {
                                screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                    ) {
                        Text(if (isScreenAuthorized) "Re-grant Screen Access" else "Grant Screen Access", fontSize = 11.sp)
                    }

                    if (isScreenAuthorized) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                ScreenObservationProvider.stopProjectionSession()
                                statusText = "Screen capture session stopped"
                            }
                        ) {
                            Text("Stop Projection", fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Camera Perception (CameraX):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isCameraRunning) "🟢 RUNNING" else if (isCameraPermissionGranted) "🟠 READY" else "🔴 NOT_GRANTED",
                        fontWeight = FontWeight.Bold,
                        color = if (isCameraRunning) Color(0xFF2E7D32) else if (isCameraPermissionGranted) Color(0xFFE65100) else Color(0xFFC62828)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isCameraPermissionGranted) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
                        ) {
                            Text("Grant Camera Access", fontSize = 11.sp)
                        }
                    } else {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (isCameraRunning) {
                                    CameraObservationProvider.disableCameraPerception()
                                    statusText = "Camera perception disabled"
                                } else {
                                    CameraObservationProvider.enableCameraPerception(context, lifecycleOwner)
                                    statusText = "Camera perception enabled"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isCameraRunning) Color(0xFFC62828) else Color(0xFF2E7D32))
                        ) {
                            Text(if (isCameraRunning) "Disable Camera" else "Enable Camera Perception", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedTab == 0) {
            // Agent Runtime Tab
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Persistent Agent Runtime Status", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Agent Loop State: ${agentState.name}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)

                    val currSession = activeSession
                    if (currSession != null) {
                        Text("Session ID: ${currSession.sessionId}", fontSize = 11.sp)
                        Text("Task: ${currSession.taskDescription}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Checkpoint State: ${currSession.currentState} | Recoveries: ${currSession.recoveryAttemptCount}", fontSize = 11.sp)
                        if (currSession.checkpointStateSignature != null) {
                            Text("Checkpoint Sig: ${currSession.checkpointStateSignature}", fontSize = 11.sp)
                        }
                    } else {
                        Text("Active Session: None", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                coroutineScope.launch {
                                    runtimeManager.resumeRuntime()
                                    statusText = "Runtime Resumed"
                                }
                            }
                        ) {
                            Text("Resume", fontSize = 11.sp)
                        }

                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                coroutineScope.launch {
                                    runtimeManager.pauseRuntime()
                                    statusText = "Runtime Paused"
                                }
                            }
                        ) {
                            Text("Pause", fontSize = 11.sp)
                        }

                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                coroutineScope.launch {
                                    runtimeManager.cancelActiveSession("User cancelled via UI")
                                    statusText = "Session Cancelled"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                        ) {
                            Text("Cancel", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                coroutineScope.launch {
                                    val count = runtimeManager.recoverInterruptedSessions()
                                    statusText = "Recovered $count interrupted session(s)"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                        ) {
                            Text("Recover Interrupted", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                coroutineScope.launch {
                                    withContext(Dispatchers.IO) {
                                        sessionDao.clearFinishedSessions()
                                    }
                                    statusText = "Cleared finished sessions"
                                }
                            }
                        ) {
                            Text("Clear Finished", fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Start Persistent Agent Session", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = userTaskInput,
                        onValueChange = { userTaskInput = it },
                        label = { Text("Task Description") },
                        placeholder = { Text("Find analytics for my latest Short") },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (userTaskInput.isNotBlank()) {
                                coroutineScope.launch {
                                    val taskDesc = userTaskInput.trim()
                                    statusText = "Runtime starting session for: '$taskDesc'..."
                                    val stepRes = runtimeManager.startOrResumeTaskSession(
                                        taskDescription = taskDesc,
                                        globalAutonomousEnabled = globalAutonomousEnabled
                                    )
                                    statusText = "Session state: ${stepRes.nextState} (${stepRes.decisionReason})"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Persistent Task Session")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Persistent Runtime Session History (${allSessions.size}):", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))

            if (allSessions.isEmpty()) {
                Text("No persistent agent sessions recorded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                allSessions.take(5).forEach { sess ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Task: ${sess.taskDescription}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("State: ${sess.currentState} | Recoveries: ${sess.recoveryAttemptCount}", fontSize = 11.sp)
                            if (sess.checkpointStateSignature != null) {
                                Text("Sig: ${sess.checkpointStateSignature}", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Recent Agent Runtime Activity Log:", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            if (runtimeLogs.isEmpty()) {
                Text("No runtime logs recorded yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                runtimeLogs.take(10).forEach { logLine ->
                    Text(logLine, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                }
            }

        } else if (selectedTab == 1) {
            // Automation Workflows Tab
            Text(
                text = "Automation Workflows",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            DefaultWorkflows.getAllWorkflows().forEach { workflow ->
                val isWorkflowActive = workflowsState[workflow.id] ?: true
                val schedule = schedules.firstOrNull { it.workflowId == workflow.id }
                val isScheduled = schedule?.enabled == true
                val latestObs = observations.firstOrNull { it.workflowId == workflow.id }

                var requiresCharging by remember(schedule) { mutableStateOf(schedule?.requiresCharging ?: false) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = workflow.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${workflow.steps.size} steps • ${if (isWorkflowActive) "Active" else "Disabled"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = isWorkflowActive,
                                onCheckedChange = { checked ->
                                    workflowsState[workflow.id] = checked
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Automatic Schedule (WorkManager):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isScheduled) "Scheduled (Daily)" else "Not Scheduled",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isScheduled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Charging Only", fontSize = 11.sp)
                                Checkbox(
                                    checked = requiresCharging,
                                    onCheckedChange = { requiresCharging = it },
                                    enabled = isWorkflowActive
                                )
                            }
                        }

                        if (isScheduled && schedule != null && schedule.nextExpectedRunTimestamp > 0) {
                            val nextRunStr = SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault())
                                .format(Date(schedule.nextExpectedRunTimestamp))
                            Text(
                                text = "Next Expected Run: $nextRunStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF1565C0)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    coroutineScope.launch {
                                        val newSchedule = WorkflowSchedule(
                                            workflowId = workflow.id,
                                            enabled = true,
                                            intervalMinutes = 1440L, // Daily
                                            requiresCharging = requiresCharging,
                                            requiresBatteryNotLow = true
                                        )
                                        scheduler.scheduleWorkflow(newSchedule)
                                        statusText = "Scheduled '${workflow.name}' daily in WorkManager"
                                    }
                                },
                                enabled = isWorkflowActive
                            ) {
                                Text(if (isScheduled) "Update Schedule" else "Enable Schedule", fontSize = 11.sp)
                            }

                            if (isScheduled) {
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        coroutineScope.launch {
                                            scheduler.cancelSchedule(workflow.id)
                                            statusText = "Cancelled schedule for '${workflow.name}'"
                                        }
                                    }
                                ) {
                                    Text("Cancel Schedule", fontSize = 11.sp)
                                }
                            }
                        }

                        if (latestObs != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val dateFormat = SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault())
                            val timeStr = dateFormat.format(Date(latestObs.timestamp))

                            Text(
                                text = "Last Run: $timeStr [${latestObs.executionTrigger}] | Result: ${latestObs.result} (${latestObs.reason})",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = when (latestObs.result) {
                                    "SUCCESS" -> Color(0xFF2E7D32)
                                    "BLOCKED" -> Color(0xFFE65100)
                                    else -> Color(0xFFC62828)
                                }
                            )

                            if (!latestObs.errorMessage.isNullOrBlank()) {
                                Text(
                                    text = "Error: ${latestObs.errorMessage}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        statusText = "Executing '${workflow.name}' (Manual)..."
                                        val engine = WorkflowEngine(context)
                                        val result = engine.executeWorkflow(
                                            workflow = workflow,
                                            trigger = ExecutionTrigger.MANUAL,
                                            globalAutonomousEnabled = globalAutonomousEnabled
                                        )

                                        val textSummary = result.snapshot?.visibleTexts?.take(10)?.joinToString("; ") ?: "No UI text"
                                        val observation = AutomationObservation(
                                            workflowId = workflow.id,
                                            packageName = result.snapshot?.packageName ?: workflow.targetPackage ?: "unknown",
                                            result = result.status.name,
                                            reason = result.reason.name,
                                            executionTrigger = ExecutionTrigger.MANUAL.name,
                                            visibleTextSummary = textSummary,
                                            screenshotPath = result.screenshotPath,
                                            errorMessage = if (result.status != ActionResultStatus.SUCCESS) result.message else null
                                        )

                                        withContext(Dispatchers.IO) {
                                            obsDao.insertObservation(observation)
                                        }

                                        statusText = "Finished '${workflow.name}': ${result.status} (${result.reason})"
                                    }
                                },
                                enabled = isWorkflowActive
                            ) {
                                Text("Run Now (Manual)")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Quick Launchers",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { openUrl(context, "https://chatgpt.com/"); statusText = "Opened ChatGPT" }
                ) {
                    Text("ChatGPT", fontSize = 12.sp)
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { openUrl(context, "https://www.youtube.com/"); statusText = "Opened YouTube" }
                ) {
                    Text("YouTube", fontSize = 12.sp)
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { openUrl(context, "https://studio.youtube.com/"); statusText = "Opened YouTube Studio" }
                ) {
                    Text("Studio", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = customUrl,
                onValueChange = { customUrl = it },
                label = { Text("Custom URL") },
                placeholder = { Text("https://example.com") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (customUrl.isNotBlank()) {
                        var finalUrl = customUrl.trim()
                        if (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
                            finalUrl = "https://$finalUrl"
                        }
                        openUrl(context, finalUrl)
                        statusText = "Opened $finalUrl"
                    }
                }
            ) {
                Text("Open URL")
            }

        } else if (selectedTab == 2) {
            // Learning Tab
            Text("Demonstration Learning Controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Autonomous Replay:", fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = globalAutonomousEnabled,
                            onCheckedChange = { globalAutonomousEnabled = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Workflow Training Session: ${if (isTrainingActive) "ACTIVE 🔴" else "INACTIVE"}", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val id = sessionManager.startSession()
                                statusText = "Started training session: $id"
                            },
                            enabled = !isTrainingActive,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Text("Start Session", fontSize = 11.sp)
                        }

                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                coroutineScope.launch {
                                    val assembled = sessionManager.stopSessionAndAssembleWorkflow()
                                    statusText = if (assembled != null) "Assembled '${assembled.name}'" else "Session ended (No steps)"
                                }
                            },
                            enabled = isTrainingActive,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                        ) {
                            Text("Stop & Assemble", fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Assembled Learned Multi-Step Workflows (${learnedWorkflows.size}):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            if (learnedWorkflows.isEmpty()) {
                Text("No multi-step learned workflows assembled yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                learnedWorkflows.forEach { lWf ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(lWf.name, fontWeight = FontWeight.Bold)
                                    Text("Status: ${lWf.status} | Confidence: ${lWf.confidence}", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            withContext(Dispatchers.IO) {
                                                learnedWfDao.deleteWorkflow(lWf.id)
                                                learnedWfDao.deleteStepsForWorkflow(lWf.id)
                                            }
                                            statusText = "Deleted '${lWf.name}'"
                                        }
                                    }
                                ) {
                                    Text("Delete", color = Color(0xFFC62828), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Diagnostics & Device Capabilities Tab
            Text("Physical Device Diagnostics & Capabilities", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            val probeResult = remember { capabilityProbe.probeCapabilities() }

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Device Info: Android ${probeResult.androidVersion} (API ${probeResult.apiLevel})", fontWeight = FontWeight.Bold)
                    Text("ABI: ${probeResult.cpuAbi}", style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Capability Probe Reports:", fontWeight = FontWeight.Bold)
                    probeResult.reports.forEach { rep ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("• ${rep.capability.name}:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text(
                                rep.state.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = when (rep.state) {
                                    CapabilityState.AVAILABLE -> Color(0xFF2E7D32)
                                    CapabilityState.NOT_GRANTED -> Color(0xFFE65100)
                                    else -> Color(0xFFC62828)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE1F5FE))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Live Screen Perception Metrics", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Capture Width: $lastFrameWidth px, Height: $lastFrameHeight px", fontSize = 12.sp)
                    Text("Visual Signature: ${lastVisualSignature ?: "None"}", fontSize = 12.sp)
                    Text("Visual Change State: $lastVisualChangeState", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val provider = ScreenObservationProvider(context)
                                val obs = provider.captureObservation()
                                statusText = "Captured Screen Frame: ${obs.visualSignature}"
                            }
                        },
                        enabled = isScreenAuthorized,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Capture On-Demand Screen Observation")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Live Camera Perception Metrics (CameraX)", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Pipeline State: ${if (isCameraRunning) "ACTIVE 🟢" else "INACTIVE"}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Resolution: $lastCameraWidth x $lastCameraHeight px", fontSize = 12.sp)
                    Text("Visual Signature: ${lastCameraSignature ?: "None"}", fontSize = 12.sp)
                    Text("Visual Change State: $lastCameraChangeState", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Performance: $cameraFrameCount frames | Last: ${lastAnalysisDurationMs}ms | Avg: ${averageAnalysisDurationMs}ms", fontSize = 12.sp)

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val provider = CameraObservationProvider(context)
                                val obs = provider.captureObservation()
                                statusText = "Captured Camera Observation: ${obs.visualSignature}"
                            }
                        },
                        enabled = isCameraRunning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Capture On-Demand Camera Observation")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            var currentSnapshot by remember { mutableStateOf<UiSnapshot?>(null) }

            Button(
                onClick = {
                    val service = AutomationAccessibilityService.instance
                    if (service != null) {
                        val root = service.getRootNode()
                        currentSnapshot = ActionResolver.captureSnapshot(root, activePackageName ?: "")
                    }
                },
                enabled = isAccessibilityEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Capture Live Accessibility UI Snapshot")
            }

            if (currentSnapshot != null) {
                Spacer(modifier = Modifier.height(12.dp))
                val snap = currentSnapshot!!
                val stateSig = StateSignatureGenerator.generateSignature(snap)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Accessibility Snapshot Details:", fontWeight = FontWeight.Bold)
                        Text("State Signature: $stateSig", fontSize = 12.sp)
                        Text("Nodes: Total=${snap.totalNodeCount}, Visible=${snap.visibleNodeCount}, Clickable=${snap.clickableNodeCount}")
                    }
                }
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
