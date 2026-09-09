package com.creator.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.work.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            MaterialTheme {

                CreatorAutomationScreen(
                    context = this
                )

            }
        }
    }
}

@Composable
fun CreatorAutomationScreen(context: Context) {

    var url by remember {
        mutableStateOf("")
    }

    var status by remember {
        mutableStateOf("Ready")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top
    ) {

        Text(
            text = "Creator Automation",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                openUrl(
                    context,
                    "https://chatgpt.com/"
                )
                status = "Opened ChatGPT"
            }
        ) {
            Text("Open ChatGPT")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                openUrl(
                    context,
                    "https://www.youtube.com/"
                )
                status = "Opened YouTube"
            }
        ) {
            Text("Open YouTube")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                openUrl(
                    context,
                    "https://studio.youtube.com/"
                )
                status = "Opened YouTube Studio"
            }
        ) {
            Text("Open YouTube Studio")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Open Website",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = url,
            onValueChange = {
                url = it
            },
            label = {
                Text("Website URL")
            },
            placeholder = {
                Text("https://example.com")
            },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {

                if (url.isNotBlank()) {

                    var finalUrl = url.trim()

                    if (!finalUrl.startsWith("http://") &&
                        !finalUrl.startsWith("https://")
                    ) {
                        finalUrl = "https://$finalUrl"
                    }

                    openUrl(context, finalUrl)

                    status = "Opened $finalUrl"
                }
            }
        ) {
            Text("Open URL")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Automation",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {

                scheduleAutomation(
                    context = context,
                    url = "https://studio.youtube.com/"
                )

                status = "YouTube Studio automation scheduled"
            }
        ) {
            Text("Schedule YouTube Studio")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Status: $status"
        )
    }
}

fun openUrl(
    context: Context,
    url: String
) {

    val intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(url)
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    context.startActivity(intent)
}

fun scheduleAutomation(
    context: Context,
    url: String
) {

    val inputData = workDataOf(
        "url" to url
    )

    val request =
        OneTimeWorkRequestBuilder<AutomationWorker>()
            .setInputData(inputData)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

    WorkManager
        .getInstance(context)
        .enqueue(request)
}
