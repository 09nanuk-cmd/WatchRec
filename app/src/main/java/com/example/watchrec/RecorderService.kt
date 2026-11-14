package com.example.watchrec

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.view.KeyEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecorderService : Service() {
    private var session: MediaSession? = null
    private var recorder: MediaRecorder? = null
    private var pfd: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    private var lastToggle = 0L

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("rec", "Registrazione", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, notif("In attesa del tasto dell'orologio"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)

        session = MediaSession(this, "WatchRec").apply {
            setCallback(object : MediaSession.Callback() {
                @Suppress("DEPRECATION")
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val e = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if (e != null && e.action == KeyEvent.ACTION_DOWN && e.keyCode in keys) toggle()
                    return e != null && e.keyCode in keys
                }
                override fun onPlay() = toggle()
                override fun onPause() = toggle()
                override fun onStop() = toggle()
            })
            isActive = true
        }
        setState(false)
    }

    private val keys = setOf(
        KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_STOP
    )

    private fun setState(recording: Boolean) {
        session?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP)
                .setState(if (recording) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, 0, 1f)
                .build()
        )
    }

    private fun toggle() {
        val now = System.currentTimeMillis()
        if (now - lastToggle < 1000) return
        lastToggle = now
        if (recorder == null) startRec() else stopRec()
    }

    private fun startRec() {
        try {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "Rec_$ts.m4a")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/WatchRec")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            pfd = contentResolver.openFileDescriptor(uri!!, "w")
            recorder = MediaRecorder(this).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(pfd!!.fileDescriptor)
                prepare()
                start()
            }
            setState(true)
            update("● Registrazione in corso")
            vibrate(300)
        } catch (e: Exception) {
            recorder?.release(); recorder = null
            update("Errore: ${e.message}")
        }
    }

    private fun stopRec() {
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release(); recorder = null
        pfd?.close(); pfd = null
        uri?.let {
            val v = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            contentResolver.update(it, v, null, null)
        }
        uri = null
        setState(false)
        update("Registrazione salvata. In attesa del tasto")
        vibrate(120); 
    }

    private fun vibrate(ms: Long) {
        getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun notif(text: String): Notification =
        Notification.Builder(this, "rec")
            .setContentTitle("WatchRec").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).build()

    private fun update(text: String) =
        getSystemService(NotificationManager::class.java).notify(1, notif(text))

    override fun onDestroy() {
        if (recorder != null) stopRec()
        session?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
