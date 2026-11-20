package com.example.focustrace.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.focustrace.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Reusable permission helper that handles requesting runtime permissions,
 * showing rationale dialogs, handling permanently denied permissions, and opening app settings.
 * Must be created during activity initialization or onCreate (before onStart).
 */
class PermissionHelper(private val activity: ComponentActivity) {

    private var pendingPermissions: List<String> = emptyList()
    private var pendingOnResult: ((Map<String, Boolean>) -> Unit)? = null

    private val launcher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            onPermissionsResult()
        }

    fun request(
        permissions: Array<String>,
        onResult: (Map<String, Boolean>) -> Unit,
    ) {
        request(permissions.toList(), onResult)
    }

    fun request(
        permissions: List<String>,
        onResult: (Map<String, Boolean>) -> Unit,
    ) {
        val applicablePermissions = permissions.filter { permission ->
            !(permission == Manifest.permission.POST_NOTIFICATIONS && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) &&
                    !(permission == Manifest.permission.BLUETOOTH_CONNECT && Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
        }

        val missingPermissions = applicablePermissions.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            val resultMap = permissions.associateWith { perm ->
                isPermissionGranted(perm)
            }
            onResult(resultMap)
            return
        }

        pendingPermissions = permissions
        pendingOnResult = onResult

        val showRationale = missingPermissions.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }

        if (showRationale) {
            showRationaleDialog(missingPermissions) {
                launcher.launch(missingPermissions.toTypedArray())
            }
        } else {
            launcher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun onPermissionsResult() {
        val callback = pendingOnResult
        val requested = pendingPermissions

        pendingOnResult = null
        pendingPermissions = emptyList()

        if (callback == null) return

        val finalResult = requested.associateWith { perm ->
            isPermissionGranted(perm)
        }

        val permanentlyDenied = requested.filter { permission ->
            val isGranted = finalResult[permission] ?: false
            if (!isGranted) {
                if (permission == Manifest.permission.POST_NOTIFICATIONS && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    false
                } else if (permission == Manifest.permission.BLUETOOTH_CONNECT && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    false
                } else {
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
                }
            } else {
                false
            }
        }

        if (permanentlyDenied.isNotEmpty()) {
            showSettingsDialog(permanentlyDenied) {
                callback(finalResult)
            }
        } else {
            callback(finalResult)
        }
    }

    private fun isPermissionGranted(permission: String): Boolean {
        if (permission == Manifest.permission.POST_NOTIFICATIONS && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        if (permission == Manifest.permission.BLUETOOTH_CONNECT && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun finishWithCurrentStatus() {
        val callback = pendingOnResult
        val requested = pendingPermissions
        pendingOnResult = null
        pendingPermissions = emptyList()
        if (callback != null) {
            val result = requested.associateWith { perm -> isPermissionGranted(perm) }
            callback(result)
        }
    }

    private fun showRationaleDialog(missingPermissions: List<String>, onContinue: () -> Unit) {
        val message = getRationaleMessage(missingPermissions)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.permission_rationale_title)
            .setMessage(message)
            .setPositiveButton(R.string.permission_continue) { _, _ ->
                onContinue()
            }
            .setNegativeButton(R.string.permission_cancel) { _, _ ->
                finishWithCurrentStatus()
            }
            .setOnCancelListener {
                finishWithCurrentStatus()
            }
            .show()
    }

    private fun showSettingsDialog(permanentlyDeniedPermissions: List<String>, onDismiss: () -> Unit) {
        val message = getPermanentlyDeniedMessage(permanentlyDeniedPermissions)
        var isHandled = false
        fun handleDismiss() {
            if (!isHandled) {
                isHandled = true
                onDismiss()
            }
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.permission_denied_title)
            .setMessage(message)
            .setPositiveButton(R.string.permission_open_settings) { _, _ ->
                openAppSettings()
                handleDismiss()
            }
            .setNegativeButton(R.string.permission_cancel) { _, _ ->
                handleDismiss()
            }
            .setOnDismissListener {
                handleDismiss()
            }
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        activity.startActivity(intent)
    }

    private fun getRationaleMessage(permissions: List<String>): String {
        val descriptions = permissions.mapNotNull { getPermissionDescription(it) }
        return if (descriptions.isNotEmpty()) {
            descriptions.joinToString("\n\n")
        } else {
            activity.getString(R.string.permission_rationale_default)
        }
    }

    private fun getPermanentlyDeniedMessage(permissions: List<String>): String {
        val descriptions = permissions.mapNotNull { getPermissionDescription(it) }
        val prefix = activity.getString(R.string.permission_denied_message)
        return if (descriptions.isNotEmpty()) {
            "$prefix\n\n" + descriptions.joinToString("\n")
        } else {
            prefix
        }
    }

    private fun getPermissionDescription(permission: String): String? {
        return when (permission) {
            Manifest.permission.POST_NOTIFICATIONS ->
                activity.getString(R.string.permission_notification_rationale)
            Manifest.permission.CAMERA ->
                activity.getString(R.string.permission_camera_rationale)
            Manifest.permission.BLUETOOTH_CONNECT ->
                activity.getString(R.string.permission_bluetooth_rationale)
            else -> null
        }
    }
}
