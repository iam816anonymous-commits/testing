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

    var customUrl by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Ready") }
    var selectedTab by remember { mutableIntStateOf(0) }

    val db = remember { AppDatabase.getDatabase(context) }
    val dao = db.observationDao()

    val observationsFlow = remember { dao.getAllObservations() }
    val observations by observationsFlow.collectAsState(initial = emptyList())

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
            text = "Creator Automation V0.2",
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
                        text = "To enable automation, tap below, locate 'Creator Automation' in Accessibility settings, and toggle it ON.",
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
                val isEnabled = workflowsState[workflow.id] ?: true
                val latestObs = observations.firstOrNull { it.workflowId == workflow.id }

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
                                    text = "${workflow.steps.size} steps • ${if (isEnabled) "Active" else "Disabled"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { checked ->
                                    workflowsState[workflow.id] = checked
                                }
                            )
                        }

                        if (latestObs != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val dateFormat = SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault())
                            val timeStr = dateFormat.format(Date(latestObs.timestamp))

                            Text(
                                text = "Last Run: $timeStr | Result: ${latestObs.result} (Reason: ${latestObs.reason})",
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
                                        statusText = "Executing '${workflow.name}'..."
                                        if (!isAccessibilityEnabled) {
                                            statusText = "BLOCKED: AccessibilityService disabled"
                                            withContext(Dispatchers.IO) {
                                                dao.insertObservation(
                                                    AutomationObservation(
                                                        workflowId = workflow.id,
                                                        packageName = workflow.targetPackage ?: "unknown",
                                                        result = ActionResultStatus.BLOCKED.name,
                                                        reason = ExecutionReason.ACCESSIBILITY_DISABLED.name,
                                                        visibleTextSummary = "AccessibilityService disabled",
                                                        errorMessage = "AccessibilityService is disabled."
                                                    )
                                                )
                                            }
                                            return@launch
                                        }

                                        val engine = WorkflowEngine(context)
                                        val result = engine.executeWorkflow(workflow)

                                        val textSummary = result.snapshot?.visibleTexts?.take(10)?.joinToString("; ") ?: "No UI text"
                                        val observation = AutomationObservation(
                                            workflowId = workflow.id,
                                            packageName = result.snapshot?.packageName ?: workflow.targetPackage ?: "unknown",
                                            result = result.status.name,
                                            reason = result.reason.name,
                                            visibleTextSummary = textSummary,
                                            screenshotPath = result.screenshotPath,
                                            errorMessage = if (result.status != ActionResultStatus.SUCCESS) result.message else null
                                        )

                                        withContext(Dispatchers.IO) {
                                            dao.insertObservation(observation)
                                        }

                                        statusText = "Finished '${workflow.name}': ${result.status} (${result.reason})"
                                    }
                                },
                                enabled = isEnabled
                            ) {
                                Text("Run Now")
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

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scheduleAutomation(context, "yt_studio_read_only")
                        statusText = "Scheduled YouTube Studio WorkManager Task"
                    }
                ) {
                    Text("Schedule WorkManager")
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
                        text = "[$timeStr] ${obs.workflowId} -> ${obs.result} (${obs.reason}) - ${obs.visibleTextSummary.take(35)}...",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp
                    )
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

                    Spacer(modifier = Modifier.height(8.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))

                    if (currentSnapshot != null) {
                        val snap = currentSnapshot!!
                        Text("Snapshot Details:", fontWeight = FontWeight.Bold)
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

fun scheduleAutomation(context: Context, workflowId: String) {
    val inputData = workDataOf("workflowId" to workflowId)
    val request = OneTimeWorkRequestBuilder<AutomationWorker>()
        .setInputData(inputData)
        .setConstraints(
            Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
        )
        .build()

    WorkManager.getInstance(context).enqueue(request)
}
