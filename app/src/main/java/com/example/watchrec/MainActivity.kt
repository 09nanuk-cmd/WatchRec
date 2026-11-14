package com.example.watchrec

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        val info = TextView(this).apply {
            text = "WatchRec\n\nAttiva il servizio, poi dall'orologio apri il lettore musicale e premi Play/Pausa: la registrazione parte e si ferma (anche a schermo spento).\n\nI file sono in Musica/WatchRec."
            textSize = 16f
        }
        val start = Button(this).apply {
            text = "ATTIVA SERVIZIO"
            setOnClickListener { ensurePermsThenStart() }
        }
        val stop = Button(this).apply {
            text = "DISATTIVA SERVIZIO"
            setOnClickListener { stopService(Intent(this@MainActivity, RecorderService::class.java)) }
        }
        layout.addView(info); layout.addView(start); layout.addView(stop)
        setContentView(layout)
    }

    private fun ensurePermsThenStart() {
        val needed = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) startSvc() else requestPermissions(needed.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startSvc()
    }

    private fun startSvc() {
        startForegroundService(Intent(this, RecorderService::class.java))
    }
}
