package com.devson.nvplayer.player

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.devson.nvplayer.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaPlaybackService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_TOGGLE_PLAYBACK -> {
                    MPVPlayerEngine.activeInstance?.togglePlayback()
                }
                ACTION_STOP -> {
                    MPVPlayerEngine.activeInstance?.pause()
                    stopSelf()
                }
                ACTION_PREV -> {
                    val broadcastIntent = Intent("com.devson.nvplayer.PIP_PREV").apply {
                        setPackage(packageName)
                    }
                    sendBroadcast(broadcastIntent)
                }
                ACTION_NEXT -> {
                    val broadcastIntent = Intent("com.devson.nvplayer.PIP_NEXT").apply {
                        setPackage(packageName)
                    }
                    sendBroadcast(broadcastIntent)
                }
                ACTION_REWIND -> {
                    val active = MPVPlayerEngine.activeInstance
                    if (active != null) {
                        val current = active.currentPosition.value
                        active.seekTo((current - 10000L).coerceAtLeast(0L), precise = false)
                    }
                }
                ACTION_FORWARD -> {
                    val active = MPVPlayerEngine.activeInstance
                    if (active != null) {
                        val current = active.currentPosition.value
                        val dur = active.duration.value
                        active.seekTo((current + 10000L).coerceAtMost(dur), precise = false)
                    }
                }
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "media_playback_channel"
        const val NOTIFICATION_ID = 8888

        const val ACTION_TOGGLE_PLAYBACK = "com.devson.nvplayer.ACTION_TOGGLE_PLAYBACK"
        const val ACTION_STOP = "com.devson.nvplayer.ACTION_STOP"
        const val ACTION_PREV = "com.devson.nvplayer.ACTION_PREV"
        const val ACTION_NEXT = "com.devson.nvplayer.ACTION_NEXT"
        const val ACTION_REWIND = "com.devson.nvplayer.ACTION_REWIND"
        const val ACTION_FORWARD = "com.devson.nvplayer.ACTION_FORWARD"
        
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("MediaPlaybackService", "Service created")
        createNotificationChannel()

        val filter = IntentFilter().apply {
            addAction(ACTION_TOGGLE_PLAYBACK)
            addAction(ACTION_STOP)
            addAction(ACTION_PREV)
            addAction(ACTION_NEXT)
            addAction(ACTION_REWIND)
            addAction(ACTION_FORWARD)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        // Observe playing and playback state of activeInstance to update notification on title/track change
        serviceScope.launch {
            val engine = MPVPlayerEngine.activeInstance
            if (engine != null) {
                kotlinx.coroutines.flow.combine(engine.isPlaying, engine.playbackState) { isPlaying, state ->
                    isPlaying to state
                }.collect {
                    updateNotification()
                }
            }
        }
    }

    private var videoTitle: String = "Video Playback"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_VIDEO_TITLE)
        if (title != null) {
            videoTitle = title
        }

        // Disable video track in MPV to avoid hardware decoder crashes in background
        MPVPlayerEngine.activeInstance?.setVideoTrackEnabled(false)

        updateNotification()

        return START_NOT_STICKY
    }

    private fun updateNotification() {
        val activeEngine = MPVPlayerEngine.activeInstance
        val isPlaying = activeEngine?.isPlaying?.value ?: false

        // Dynamically get the currently playing file title from MPVLib
        var dynamicTitle = `is`.xyz.mpv.MPVLib.getPropertyString("media-title")
        if (dynamicTitle.isNullOrEmpty()) {
            dynamicTitle = videoTitle
        } else {
            // Strip extension
            val dot = dynamicTitle.lastIndexOf('.')
            if (dot > 0) {
                dynamicTitle = dynamicTitle.substring(0, dot)
            }
        }

        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseText = if (isPlaying) "Pause" else "Play"

        val prevIntent = Intent(ACTION_PREV).apply { setPackage(packageName) }
        val rewindIntent = Intent(ACTION_REWIND).apply { setPackage(packageName) }
        val toggleIntent = Intent(ACTION_TOGGLE_PLAYBACK).apply { setPackage(packageName) }
        val forwardIntent = Intent(ACTION_FORWARD).apply { setPackage(packageName) }
        val nextIntent = Intent(ACTION_NEXT).apply { setPackage(packageName) }
        val stopIntent = Intent(ACTION_STOP).apply { setPackage(packageName) }

        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val prevPendingIntent = PendingIntent.getBroadcast(this, 10, prevIntent, pendingFlags)
        val rewindPendingIntent = PendingIntent.getBroadcast(this, 11, rewindIntent, pendingFlags)
        val togglePendingIntent = PendingIntent.getBroadcast(this, 12, toggleIntent, pendingFlags)
        val forwardPendingIntent = PendingIntent.getBroadcast(this, 13, forwardIntent, pendingFlags)
        val nextPendingIntent = PendingIntent.getBroadcast(this, 14, nextIntent, pendingFlags)
        val stopPendingIntent = PendingIntent.getBroadcast(this, 15, stopIntent, pendingFlags)

        val notificationIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingFlags)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(dynamicTitle)
            .setContentText(if (isPlaying) "Playing in background" else "Paused")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(stopPendingIntent) // Stop service when swiped away
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
            .addAction(android.R.drawable.ic_media_rew, "Rewind", rewindPendingIntent)
            .addAction(playPauseIcon, playPauseText, togglePendingIntent)
            .addAction(android.R.drawable.ic_media_ff, "Fast Forward", forwardPendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 2, 4) // Show Prev, Play/Pause, Next
            )
            .setOngoing(isPlaying)

        val notification = notificationBuilder.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MediaPlaybackService", "Service destroyed")
        // Re-enable video track in MPV for when we return to foreground
        MPVPlayerEngine.activeInstance?.setVideoTrackEnabled(true)
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.e("MediaPlaybackService", "Error unregistering receiver", e)
        }
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Media Playback Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background video playback controls"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}
