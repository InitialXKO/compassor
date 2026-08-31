package com.growsnova.compassor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.growsnova.compassor.MainActivity
import com.growsnova.compassor.R

class NavigationService : Service() {

    companion object {
        const val CHANNEL_ID = "compassor_navigation_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_OR_UPDATE = "com.growsnova.compassor.action.START_OR_UPDATE"
        const val ACTION_STOP = "com.growsnova.compassor.action.STOP"

        const val EXTRA_TARGET_NAME = "extra_target_name"
        const val EXTRA_DISTANCE = "extra_distance"
        const val EXTRA_BEARING = "extra_bearing"

        fun startOrUpdate(context: Context, targetName: String, distance: Float, bearing: Float) {
            val intent = Intent(context, NavigationService::class.java).apply {
                action = ACTION_START_OR_UPDATE
                putExtra(EXTRA_TARGET_NAME, targetName)
                putExtra(EXTRA_DISTANCE, distance)
                putExtra(EXTRA_BEARING, bearing)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, NavigationService::class.java)
            context.stopService(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
            return START_NOT_STICKY
        }

        val targetName = intent?.getStringExtra(EXTRA_TARGET_NAME) ?: "目的地"
        val distance = intent?.getFloatExtra(EXTRA_DISTANCE, -1f) ?: -1f
        val bearing = intent?.getFloatExtra(EXTRA_BEARING, 0f) ?: 0f

        val notification = buildNotification(targetName, distance, bearing)
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "导航服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "在后台与锁屏显示 Compassor 导航状态"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(targetName: String, distance: Float, bearing: Float): Notification {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val distanceStr = if (distance < 0) {
            "计算中..."
        } else if (distance < 1000) {
            "${distance.toInt()}米"
        } else {
            "%.1f公里".format(distance / 1000f)
        }

        val contentTitle = "导航中: $targetName"
        val contentText = "距离: $distanceStr | 方位: %.0f°".format(bearing)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_waypoint_small)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .build()
    }
}
