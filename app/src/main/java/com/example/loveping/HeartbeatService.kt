// HeartbeatService.kt
package com.example.loveping

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentChange

class HeartbeatService : Service() {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var vibrator: Vibrator

    companion object {
        private const val CHANNEL_ID = "LovePingChannel"
        private const val NOTIFICATION_ID = 1
    }

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                createSilentNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
            )
        }

        // Service masih perlu notification channel untuk berjalan sebagai foreground service
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createSilentNotification())
        listenToHeartbeats()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LovePing Service",
                NotificationManager.IMPORTANCE_MIN // Set ke MIN untuk menghindari visual notification
            ).apply {
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createSilentNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LovePing")
            .setContentText("")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    private fun listenToHeartbeats() {
        val currentUser = auth.currentUser?.uid
        db.collection("heartbeats")
            .whereEqualTo("receiver", currentUser)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener

                snapshots?.documentChanges?.forEach { doc ->
                    if (doc.type == DocumentChange.Type.ADDED) {
                        vibrate()
                    }
                }
            }
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }
    }
}