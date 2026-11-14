package com.example.watchrec

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecorderService : Service() {
    private var session: MediaSession? = null
    private var track: AudioTrack? = null
    @Volatile private var running = true
    private var lastToggle = 0L

    private val keys = setOf(
        KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_STOP,
        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS
    )

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("rec", "WatchRec", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, notif("In ascolto del tasto dell'orologio"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)

        session = MediaSession(this, "WatchRec").apply {
            setCallback(object : MediaSession.Callback() {
                @Suppress("DEPRECATION")
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val e = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if (e != null) {
                        if (e.action == KeyEvent.ACTION_DOWN) trigger("tasto ${KeyEvent.keyCodeToString(e.keyCode)}")
                        return true
                    }
                    return false
                }
                override fun onPlay() = trigger("onPlay")
                override fun onPause() = trigger("onPause")
                override fun onStop() = trigger("onStop")
                override fun onSkipToNext() = trigger("onNext")
                override fun onSkipToPrevious() = trigger("onPrevious")
            })
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or
                            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                    .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                    .build()
            )
            isActive = true
        }
        startSilence()
    }

    // Riproduce silenzio in loop: così Android considera WatchRec "l'app che sta suonando"
    // e gli invia i tasti multimediali dell'orologio anche a schermo spento.
    private fun startSilence() {
        val rate = 8000
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track?.play()
        Thread {
            val silence = ByteArray(minBuf)
            while (running) { track?.write(silence, 0, silence.size) }
        }.start()
    }

    private fun trigger(what: String) {
        val now = System.currentTimeMillis()
        if (now - lastToggle < 1500) return
        lastToggle = now
        log("Segnale ricevuto ($what)")
        wakeScreen()
        launchRecForge()
        getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        try {
            val pm = getSystemService(PowerManager::class.java)
            pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "WatchRec:wake"
            ).acquire(5000)
        } catch (_: Exception) {}
    }

    private fun launchRecForge() {
        val pm = packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        val match = apps.filter { it.loadLabel(pm).toString().contains("recforge", true) }
            .sortedByDescending { it.loadLabel(pm).toString().contains("II", false) }
            .firstOrNull()
        if (match == null) { log("RecForge non trovato tra le app installate"); return }
        try {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(match.activityInfo.packageName, match.activityInfo.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            startActivity(i)
            log("Lanciato ${match.loadLabel(pm)} (${match.activityInfo.packageName})")
        } catch (e: Exception) {
            log("Errore avvio RecForge: ${e.message}")
        }
    }

    private fun log(msg: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val prefs = getSharedPreferences("log", Context.MODE_PRIVATE)
        val old = prefs.getString("last", "") ?: ""
        val lines = (listOf("$t  $msg") + old.split("\n").filter { it.isNotBlank() }).take(6)
        prefs.edit().putString("last", lines.joinToString("\n")).apply()
    }

    private fun notif(text: String): Notification =
        Notification.Builder(this, "rec")
            .setContentTitle("WatchRec attivo").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play).setOngoing(true).build()

    override fun onDestroy() {
        running = false
        track?.stop(); track?.release()
        session?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
