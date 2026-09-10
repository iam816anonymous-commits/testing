package com.creator.automation

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log

enum class AppResolutionStatus {
    SUCCESS,
    APP_NOT_INSTALLED,
    AMBIGUOUS_APPLICATION,
    INVALID_QUERY
}

data class AppResolutionResult(
    val status: AppResolutionStatus,
    val packageName: String? = null,
    val appLabel: String? = null,
    val launchIntent: Intent? = null,
    val candidatePackages: List<String> = emptyList(),
    val explanation: String
)

class AppResolver(
    private val context: Context
) {

    companion object {
        private const val TAG = "AppResolver"
    }

    /**
     * Resolves an app query (name, label, or package) to an installed package and launch intent
     * dynamically via PackageManager launcher activity discovery without hardcoded package maps.
     */
    fun resolveApplication(query: String): AppResolutionResult {
        if (query.isBlank()) {
            return AppResolutionResult(
                status = AppResolutionStatus.INVALID_QUERY,
                explanation = "Query string is blank"
            )
        }

        val trimmedQuery = query.trim().lowercase()

        // 1. Direct package match check
        if (trimmedQuery.contains(".")) {
            if (isPackageInstalled(trimmedQuery)) {
                val intent = getLaunchIntentForPackage(trimmedQuery)
                val label = getAppLabel(trimmedQuery)
                return AppResolutionResult(
                    status = AppResolutionStatus.SUCCESS,
                    packageName = trimmedQuery,
                    appLabel = label,
                    launchIntent = intent,
                    explanation = "Direct package match '$trimmedQuery' is installed."
                )
            } else {
                return AppResolutionResult(
                    status = AppResolutionStatus.APP_NOT_INSTALLED,
                    packageName = trimmedQuery,
                    explanation = "Package '$trimmedQuery' is NOT installed on this device."
                )
            }
        }

        // 2. Dynamic PackageManager scan across installed launcher apps
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = try {
            pm.queryIntentActivities(mainIntent, 0) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val matchingApps = mutableListOf<Pair<String, String>>() // Pair(packageName, label)

        for (info in resolveInfos) {
            val pkg = info.activityInfo?.packageName ?: continue
            val label = try { info.loadLabel(pm).toString() } catch (e: Exception) { pkg }
            if (label.lowercase().contains(trimmedQuery) || pkg.lowercase().contains(trimmedQuery)) {
                matchingApps.add(Pair(pkg, label))
            }
        }

        if (matchingApps.isEmpty()) {
            Log.w(TAG, "APP_RESOLVE_FAILED: No installed app matching '$query'")
            return AppResolutionResult(
                status = AppResolutionStatus.APP_NOT_INSTALLED,
                explanation = "No installed application matching label '$query' was found on this device."
            )
        }

        if (matchingApps.size > 1) {
            // Check for exact label match
            val exactLabelMatch = matchingApps.filter { it.second.equals(query, ignoreCase = true) }
            if (exactLabelMatch.size == 1) {
                val best = exactLabelMatch.first()
                val intent = getLaunchIntentForPackage(best.first)
                return AppResolutionResult(
                    status = AppResolutionStatus.SUCCESS,
                    packageName = best.first,
                    appLabel = best.second,
                    launchIntent = intent,
                    explanation = "Resolved exact app label '${best.second}' (${best.first})."
                )
            }

            val candidateList = matchingApps.map { "${it.second} (${it.first})" }
            Log.w(TAG, "AMBIGUOUS_APPLICATION: Multiple apps matched '$query': $candidateList")
            return AppResolutionResult(
                status = AppResolutionStatus.AMBIGUOUS_APPLICATION,
                candidatePackages = matchingApps.map { it.first },
                explanation = "Multiple applications (${matchingApps.size}) matched query '$query': $candidateList"
            )
        }

        val singleMatch = matchingApps.first()
        val intent = getLaunchIntentForPackage(singleMatch.first)
        return AppResolutionResult(
            status = AppResolutionStatus.SUCCESS,
            packageName = singleMatch.first,
            appLabel = singleMatch.second,
            launchIntent = intent,
            explanation = "Uniquely resolved app '${singleMatch.second}' (${singleMatch.first})."
        )
    }

    fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getLaunchIntentForPackage(packageName: String): Intent? {
        return context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun getAppLabel(packageName: String): String? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            null
        }
    }
}
