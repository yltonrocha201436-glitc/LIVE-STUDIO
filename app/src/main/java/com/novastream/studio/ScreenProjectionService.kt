package com.novastream.studio

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat

/**
 * Foreground owner for a user-approved MediaProjection session.
 * The user consent activity is launched before this service is started.
 */
object ProjectionSession {
    @Volatile
    var projection: MediaProjection? = null

    @Volatile
    var onReady: ((MediaProjection) -> Unit)? = null

    @Volatile
    var onFailure: ((String) -> Unit)? = null
}

class ScreenProjectionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = buildNotification()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                ?: Activity.RESULT_CANCELED
            @Suppress("DEPRECATION")
            val resultData: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                intent?.getParcelableExtra(EXTRA_RESULT_DATA)
            }

            if (resultCode != Activity.RESULT_OK || resultData == null) {
                ProjectionSession.onFailure?.invoke("Permissão para compartilhar a tela não foi concedida")
                stopSelf()
                return START_NOT_STICKY
            }

            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = manager.getMediaProjection(resultCode, resultData)
            if (projection == null) {
                ProjectionSession.onFailure?.invoke("O Android não forneceu uma sessão de captura")
                stopSelf()
                return START_NOT_STICKY
            }

            ProjectionSession.projection = projection
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    ProjectionSession.projection = null
                    ProjectionSession.onFailure?.invoke("A captura de tela foi encerrada pelo Android")
                    stopSelf()
                }
            }, android.os.Handler(mainLooper))

            ProjectionSession.onReady?.invoke(projection)
        } catch (error: Exception) {
            ProjectionSession.onFailure?.invoke(
                "Falha ao iniciar captura de tela: ${error.localizedMessage ?: "erro"}"
            )
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ProjectionSession.projection?.let { projection ->
            try { projection.stop() } catch (_: Exception) {}
        }
        ProjectionSession.projection = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Captura de tela do NovaStream",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantém a captura de tela ativa durante a transmissão"
                setShowBadge(false)
            }
            (getSystemService(NotificationManager::class.java))
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("NovaStream Studio")
            .setContentText("Compartilhamento de tela ativo")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_RESULT_DATA = "projection_result_data"
        private const val CHANNEL_ID = "novastream_screen_capture"
        private const val NOTIFICATION_ID = 4301

        fun start(context: Context, resultCode: Int, data: Intent) {
            val serviceIntent = Intent(context, ScreenProjectionService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenProjectionService::class.java))
        }
    }
}
