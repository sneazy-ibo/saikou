package ani.saikou.discord

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import ani.saikou.R
import ani.saikou.toast

class DiscordRPCService : Service() {
    companion object {
        const val CHANNEL = "Discord RPC"

        var rpc: DiscordRPC? = null

        var userName: String? = null
        var userAvatar: String? = null
    }

    override fun onCreate() {
        super.onCreate()

        rpc = DiscordRPC.getToken(this@DiscordRPCService)?.let {
            DiscordRPC(it)
        }

        if (rpc == null) {
            toast("Not started")
            return
        }

        rpc!!.setName("Saikou")
            .setLargeImage("mp:attachments/1082940109504131072/1112752996833558668/94010054.png", "Saikou")
            .setType(0)
            .setStatus("online")
            .setButton1("github.com/saikou-app/saikou")
            .setButton2("discord.gg/2T7TunuwFZ")
            .setDefaultPresence()
            .build()

        notification()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        rpc?.closeRPC()
        rpc = null
        super.onDestroy()
    }

    private fun notification() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
        )

        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_splash)
            .setUsesChronometer(true)
            .setContentText("Discord RPC running")

        startForeground(11234, builder.build())
    }
}