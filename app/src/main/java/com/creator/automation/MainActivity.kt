package com.creator.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
    val isAccessibilityEnabled by AutomationAccessibilityService.isServiceEnabled.collectAsState()
    val activePackageName by AutomationAccessibilityService.activePackageName.collectAsState()
    val lastAccessibilityEvent by AutomationAccessibilityService.lastAccessibilityEvent.collectAsState()
    val currentLearningMode by AutomationAccessibilityService.currentLearningMode.collectAsState()
    val isTrainingActive by TrainingSessionManager.isTrainingActive.collectAsState()

    var customUrl by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Ready") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var globalAutonomousEnabled by remember { mutableStateOf(true) }

    val db = remember { AppDatabase.getDatabase(context) }
    val obsDao = db.observationDao()
    val scheduleDao = db.scheduleDao()
    val demoDao = db.demonstrationDao()
    val auditDao = db.actionAuditDao()
    val learnedWfDao = db.learnedWorkflowDao()
    val scheduler = remember { AutomationScheduler(context, scheduleDao) }
    val sessionManager = remember { TrainingSessionManager(learnedWfDao) }

    val observationsFlow = remember { obsDao.getAllObservations() }
    val observations by observationsFlow.collectAsState(initial = emptyList())

    val schedulesFlow = remember { scheduleDao.getAllSchedulesFlow() }
    val schedules by schedulesFlow.collectAsState(initial = emptyList())

    val demonstrationsFlow = remember { demoDao.getAllRecordsFlow() }
    val demonstrations by demonstrationsFlow.collectAsState(initial = emptyList())

    val learnedWorkflowsFlow = remember { learnedWfDao.getAllWorkflowsFlow() }
    val learnedWorkflows by learnedWorkflowsFlow.collectAsState(initial = emptyList())

    val auditRecordsFlow = remember { auditDao.getAllAuditRecordsFlow() }
    val auditRecords by auditRecordsFlow.collectAsState(initial = emptyList())

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
            text = "Creator Automation V0.6",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Navigation Tabs
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Automation") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Learning") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Diagnostics") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Accessibility Service Status Card
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
                        text = "To enable automation & learning, tap below, locate 'Creator Automation' in Accessibility settings, and toggle it ON.",
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
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedTab == 0) {
            // Automation Tab
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

                        // Schedule Controls
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
                                        if (!isAccessibilityEnabled) {
                                            statusText = "BLOCKED: AccessibilityService disabled"
                                            withContext(Dispatchers.IO) {
                                                obsDao.insertObservation(
                                                    AutomationObservation(
                                                        workflowId = workflow.id,
                                                        packageName = workflow.targetPackage ?: "unknown",
                                                        result = ActionResultStatus.BLOCKED.name,
                                                        reason = ExecutionReason.ACCESSIBILITY_DISABLED.name,
                                                        executionTrigger = ExecutionTrigger.MANUAL.name,
                                                        visibleTextSummary = "AccessibilityService disabled",
                                                        errorMessage = "AccessibilityService is disabled."
                                                    )
                                                )
                                            }
                                            return@launch
                                        }

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

            // Quick Launchers Section
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
                    onClick = {
                        openUrl(context, "https://chatgpt.com/")
                        statusText = "Opened ChatGPT"
                    }
                ) {
                    Text("ChatGPT", fontSize = 12.sp)
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        openUrl(context, "https://www.youtube.com/")
                        statusText = "Opened YouTube"
                    }
                ) {
                    Text("YouTube", fontSize = 12.sp)
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        openUrl(context, "https://studio.youtube.com/")
                        statusText = "Opened YouTube Studio"
                    }
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
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
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(text = "Status: $statusText", fontWeight = FontWeight.Bold)

            if (observations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Recent Execution Observations (${observations.size}):",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))

                observations.take(5).forEach { obs ->
                    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(obs.timestamp))
                    Text(
                        text = "[$timeStr] [${obs.executionTrigger}] ${obs.workflowId} -> ${obs.result} (${obs.reason}) - ${obs.visibleTextSummary.take(30)}...",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp
                    )
                }
            }
        } else if (selectedTab == 1) {
            // Learning & Demonstrations Tab
            Text(
                text = "Demonstration Learning Controls",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

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
                            Text("Start Training Session", fontSize = 11.sp)
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

            Text(
                text = "Assembled Learned Multi-Step Workflows (${learnedWorkflows.size}):",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (learnedWorkflows.isEmpty()) {
                Text(
                    text = "No multi-step learned workflows assembled yet. Tap 'Start Training Session', perform actions (e.g. open YouTube Studio -> Continue to Studio), then tap 'Stop & Assemble'.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                learnedWorkflows.forEach { lWf ->
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
                                    Text(lWf.name, fontWeight = FontWeight.Bold)
                                    Text("Status: ${lWf.status} | Confidence: ${lWf.confidence}", style = MaterialTheme.typography.bodySmall)
                                    Text("Successes: ${lWf.successCount} | Failures: ${lWf.failureCount}", style = MaterialTheme.typography.bodySmall)
                                }

                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            withContext(Dispatchers.IO) {
                                                learnedWfDao.deleteWorkflow(lWf.id)
                                                learnedWfDao.deleteStepsForWorkflow(lWf.id)
                                            }
                                            statusText = "Deleted learned workflow '${lWf.name}'"
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

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Learned Transitions (${demonstrations.size}):",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                if (demonstrations.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    demoDao.deleteAllRecords()
                                }
                                statusText = "Cleared all learned demonstration records"
                            }
                        }
                    ) {
                        Text("Clear All", color = Color(0xFFC62828), fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (demonstrations.isEmpty()) {
                Text(
                    text = "No user demonstrations recorded yet. Enable 'Training Mode' and open YouTube Studio to record choice selections (e.g. 'Continue to Studio').",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                demonstrations.forEach { record ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Pkg: ${record.packageName.substringAfterLast(".")}", fontWeight = FontWeight.Bold)
                                Text(
                                    "Confidence: ${record.confidenceLevel}",
                                    fontWeight = FontWeight.Bold,
                                    color = when (record.confidenceLevel) {
                                        "HIGH" -> Color(0xFF2E7D32)
                                        "MEDIUM" -> Color(0xFF1565C0)
                                        else -> Color(0xFFE65100)
                                    }
                                )
                            }
                            Text("Action: ${record.actionType} -> '${record.targetText}'", style = MaterialTheme.typography.bodySmall)
                            Text("Demos: ${record.demonstrationCount} | Successes: ${record.successCount} | Failures: ${record.failureCount}", style = MaterialTheme.typography.bodySmall)
                            if (record.isAmbiguous) {
                                Text("⚠️ Conflicting choices detected for state", color = Color(0xFFC62828), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // Physical Device Diagnostic Tab
            Text(
                text = "Physical Device Diagnostics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

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
                Text("Capture Live UI Snapshot")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Active Package: ${activePackageName ?: "Unknown"}", fontWeight = FontWeight.SemiBold)
                    Text("Last Event: $lastAccessibilityEvent", style = MaterialTheme.typography.bodySmall)
                    Text("Learning Mode: ${currentLearningMode.name}", style = MaterialTheme.typography.bodySmall)
                    Text("Training Session: ${if (isTrainingActive) "ACTIVE" else "INACTIVE"}", style = MaterialTheme.typography.bodySmall)
                    Text("Autonomous Replay: ${if (globalAutonomousEnabled) "ON" else "OFF"}", style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Active Schedules (${schedules.size}):", fontWeight = FontWeight.Bold)
                    schedules.forEach { sched ->
                        val nextRunStr = if (sched.nextExpectedRunTimestamp > 0) {
                            SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault()).format(Date(sched.nextExpectedRunTimestamp))
                        } else "N/A"
                        Text(
                            "• ${sched.workflowId}: Enabled=${sched.enabled}, NextRun=$nextRunStr",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Recent Action Audits (${auditRecords.size}):", fontWeight = FontWeight.Bold)
                    auditRecords.take(3).forEach { audit ->
                        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(audit.timestamp))
                        Text(
                            "[$timeStr] ${audit.actionType} -> Target: '${audit.targetIdentifier ?: "N/A"}' | Success: ${audit.success} | Verification: ${audit.verificationStatus} | Change: ${audit.stateChangeResult}",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    if (currentSnapshot != null) {
                        val snap = currentSnapshot!!
                        val stateSig = StateSignatureGenerator.generateSignature(snap)
                        Text("Snapshot Details:", fontWeight = FontWeight.Bold)
                        Text("State Signature: $stateSig", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("Total Nodes: ${snap.totalNodeCount}")
                        Text("Visible Nodes: ${snap.visibleNodeCount}")
                        Text("Clickable Nodes: ${snap.clickableNodeCount}")
                        Text("Scrollable Nodes: ${snap.scrollableNodeCount}")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Visible Text Sample:\n${snap.visibleTexts.take(8).joinToString("\n• ", prefix = "• ")}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text("Tap 'Capture Live UI Snapshot' to inspect foreground screen state.", style = MaterialTheme.typography.bodySmall)
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
