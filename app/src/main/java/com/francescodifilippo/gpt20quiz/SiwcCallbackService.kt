package com.francescodifilippo.gpt20quiz

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * The external OAuth browser backgrounds our Activity. A short-lived foreground
 * service keeps the Android process (and its one-use loopback listener) active.
 * Inspired by the independently implemented touchai Android SIWC approach.
 *
 * It does not read, handle, persist, or log authorization credentials.
 */
class SiwcCallbackService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val stopOnDeadline = Runnable { stopSelf() }

    companion object {
        private const val CHANNEL = "gpt20_siwc_signin"
        private const val NOTIFICATION_ID = 2040
        // FGS shortService expires after ~3 minutes. End well before its limit.
        const val AUTH_TIMEOUT_MS = 125_000L

        fun start(context: Context) {
            val intent = Intent(context, SiwcCallbackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SiwcCallbackService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "ChatGPT sign-in",
                    NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reopen = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("GPT 20 Quiz")
            .setContentText("Connessione a ChatGPT in corso — torna all'app dopo l'accesso")
            .setContentIntent(reopen)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        handler.removeCallbacks(stopOnDeadline)
        handler.postDelayed(stopOnDeadline, AUTH_TIMEOUT_MS)
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf(startId)
    }

    override fun onDestroy() {
        handler.removeCallbacks(stopOnDeadline)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
