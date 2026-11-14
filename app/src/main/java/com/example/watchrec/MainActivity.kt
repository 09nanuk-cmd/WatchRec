package com.example.watchrec

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 40) }
        layout.addView(TextView(this).apply {
            text = "WatchRec\nPremi Play/Pausa sul lettore musicale dell'orologio: si apre RecForge II."
            textSize = 16f
        })
        fun btn(label: String, action: () -> Unit) = Button(this).apply {
            text = label; setOnClickListener { action() }; layout.addView(this)
        }
        btn("1) ATTIVA SERVIZIO") { startAll() }
        btn("2) CONCEDI \"MOSTRA SOPRA ALTRE APP\"") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        btn("DISATTIVA SERVIZIO") { stopService(Intent(this, RecorderService::class.java)) }
        btn("AGGIORNA LOG") { refresh() }
        status = TextView(this).apply { textSize = 14f; setPadding(0, 30, 0, 0) }
        layout.addView(status)
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val overlay = Settings.canDrawOverlays(this)
        val last = getSharedPreferences("log", Context.MODE_PRIVATE).getString("last", "") ?: ""
        status.text = "Sopra altre app: ${if (overlay) "OK" else "NON concesso"}\n\nUltimi eventi:\n" +
                (if (last.isBlank()) "(nessun segnale ricevuto dall'orologio)" else last)
    }

    private fun startAll() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        } else startSvc()
    }

    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) { startSvc() }

    private fun startSvc() {
        startForegroundService(Intent(this, RecorderService::class.java))
        refresh()
    }
}
