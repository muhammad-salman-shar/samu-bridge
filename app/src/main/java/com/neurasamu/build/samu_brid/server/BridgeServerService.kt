package com.neurasamu.build.samu_brid.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.neurasamu.build.samu_brid.data.LogRepo

class BridgeServerService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        private const val CHANNEL_ID = "samu_bridge_server"
        private const val NOTIF_ID = 1001
        @Volatile var isRunning: Boolean = false; private set
        @Volatile var boundPort: Int = 0; private set
    }

    private var server: BridgeHttpServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopServer()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val port = intent?.getIntExtra("port", 8787) ?: 8787
                val token = intent?.getStringExtra("token") ?: "samu123456"
                val model = intent?.getStringExtra("model") ?: "samu"
                startForeground(NOTIF_ID, buildNotif(port))
                startServer(port, token, model)
                return START_STICKY
            }
        }
    }

    private fun startServer(port: Int, token: String, model: String) {
        stopServer()
        try {
            val s = BridgeHttpServer(applicationContext, port, token, model)
            s.start(fi.iki.elonen.NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            server = s
            isRunning = true
            boundPort = port
            LogRepo.info("HTTP server listening on 127.0.0.1:$port")
        } catch (e: Exception) {
            isRunning = false
            boundPort = 0
            LogRepo.error("Server start fail: ${e.message}")
        }
    }

    private fun stopServer() {
        try { server?.stop() } catch (_: Exception) {}
        server = null
        if (isRunning) LogRepo.info("HTTP server stopped")
        isRunning = false
        boundPort = 0
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(CHANNEL_ID, "SaMu Bridge Server", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotif(port: Int) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SaMu Bridge API")
            .setContentText("http://127.0.0.1:$port/v1")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()
}
