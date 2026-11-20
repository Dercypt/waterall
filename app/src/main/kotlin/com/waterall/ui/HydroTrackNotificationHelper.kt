package com.waterall.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.waterall.R
import com.waterall.domain.calculator.ZoneConsensus
import com.waterall.domain.model.GroundTruthReport

/**
 * Coordinates Android notification channel registration, runtime permission checks,
 * and explicit [PendingIntent] routing back into [MainActivity].
 */
object HydroTrackNotificationHelper {

    const val CHANNEL_ID = "waterall_status_updates"
    const val NOTIFICATION_ID_REPORT = 1001
    private const val REQUEST_CODE_REPORT_TAP = 2001

    /**
     * Ensures the BWD HydroTrack status notification channel is registered with the OS.
     */
    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_description)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Returns true if notification posting is permitted on the current Android OS level.
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Builds an explicit [Intent] targeting [MainActivity] with [targetTab] and [zoneId] extras.
     */
    fun buildMainActivityIntent(
        context: Context,
        targetTab: Tab,
        zoneId: String
    ): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(StatusCommunicationFormatter.EXTRA_TARGET_TAB, targetTab.name)
            putExtra(StatusCommunicationFormatter.EXTRA_ZONE_ID, zoneId)
        }
    }

    /**
     * Posts a local notification summarizing the submitted tap status [report] and updated
     * [consensus], returning true if posted or false if notification permission is not granted.
     */
    fun showReportNotification(
        context: Context,
        report: GroundTruthReport,
        consensus: ZoneConsensus
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        ensureNotificationChannel(context)

        val contentIntent = buildMainActivityIntent(
            context = context,
            targetTab = Tab.CALENDAR,
            zoneId = report.zoneId
        )
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_REPORT_TAP,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = StatusCommunicationFormatter.formatReportNotificationTitle(report)
        val body = StatusCommunicationFormatter.formatReportNotificationBody(report, consensus)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_water)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_REPORT, notification)
        return true
    }
}
