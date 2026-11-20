package com.example.focustrace.detection

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.focustrace.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EarphoneTestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())

        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        val hasBtConnectPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val deviceName = if (device != null) {
            if (hasBtConnectPerm) {
                try {
                    device.name ?: "Unknown Device"
                } catch (e: SecurityException) {
                    "Perm Denied"
                }
            } else {
                "No Perm"
            }
        } else {
            "No Device"
        }

        val deviceClass = if (hasBtConnectPerm) {
            try {
                device?.bluetoothClass
            } catch (e: SecurityException) {
                null
            }
        } else {
            null
        }

        val isAudio = deviceClass?.let {
            it.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO ||
                    it.hasService(BluetoothClass.Service.AUDIO)
        } ?: false

        val classHex = deviceClass?.let { "0x" + Integer.toHexString(it.deviceClass) } ?: "unknown"
        val classInfo = "class=$classHex (isAudio=$isAudio)"

        val statusText = when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> "ACL connected"
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> "ACL disconnected"
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> "A2DP connected"
                    BluetoothProfile.STATE_DISCONNECTED -> "A2DP disconnected"
                    BluetoothProfile.STATE_CONNECTING -> "A2DP connecting"
                    BluetoothProfile.STATE_DISCONNECTING -> "A2DP disconnecting"
                    else -> "A2DP state=$state"
                }
            }
            else -> action
        }

        val logLine = "[$timestamp] $statusText | Device=$deviceName | $classInfo | action=$action"

        Log.d(TAG, logLine)

        try {
            val logFile = File(context.filesDir, LOG_FILE_NAME)
            logFile.appendText("$logLine\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write earphone broadcast log file", e)
        }

        val notifTitle = "T9: $statusText"
        val notifMessage = "$deviceName ($classInfo)"
        showNotification(context, notifTitle, notifMessage)
    }

    private fun showNotification(context: Context, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "T9 Earphone Broadcast Test",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for T9 earphone broadcast test notifications"
            }
            notificationManager.createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationId = (System.currentTimeMillis() % 100000).toInt()
        notificationManager.notify(notificationId, notification)
    }

    companion object {
        private const val TAG = "T9_EarphoneBroadcast"
        const val LOG_FILE_NAME = "t9_earphone_log.txt"
        private const val CHANNEL_ID = "t9_earphone_channel"
    }
}
