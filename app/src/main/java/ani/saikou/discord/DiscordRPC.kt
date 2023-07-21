package ani.saikou.discord

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import ani.saikou.R
import ani.saikou.toast
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonNull
import com.google.gson.reflect.TypeToken
import okhttp3.*
import java.io.File
import java.lang.Exception
import java.util.concurrent.TimeUnit

class DiscordRPC(private val token: String) {
    companion object {
        fun getToken(context: Context): String? {
            val sharedPref = context.getSharedPreferences(
                context.run { getString(R.string.preference_file_key) },
                Context.MODE_PRIVATE
            )
            return sharedPref.getString("discord_token", null)
        }

        fun removeToken(context: Context) {
            val sharedPref =
                context.getSharedPreferences(context.run { getString(R.string.preference_file_key) }, Context.MODE_PRIVATE)
            sharedPref.edit {
                remove("discord_token")
                commit()
            }

            var successful: Boolean
            try {
                val dir   = File(context.filesDir?.parentFile, "app_webview")
                val dir2  = File(context.filesDir?.parentFile, "cache")
                val dir3  = File(context.filesDir?.parentFile, "shared_prefs")
                val dir4  = File(context.filesDir?.parentFile, "app_textures")
                val file6 = File(context.filesDir, "user")
                successful = dir.deleteRecursively()

                if (file6.exists() && !file6.delete()) successful = false
                if (!dir2.deleteRecursively()) successful = false
                if (!dir3.deleteRecursively()) successful = false
                if (!dir4.deleteRecursively()) successful = false

                if (successful)
                    toast("Successfully logged out!")
                else
                    toast("Failed to fully logout.\nPlease clear cache and data in the settings.")

            } catch (_: Exception) {
                toast("Failed to Logout!\nPlease clear cache and data in the settings.")
            }
        }
    }

    private var activityName: String? = null
    private var details: String? = null
    private var state: String? = null
    private var largeImage: String? = null
    private var smallImage: String? = null
    private var largeText: String? = null
    private var smallText: String? = null
    private var status: String? = null
    private var startTimestamps: Long? = null
    private var stopTimestamps: Long? = null
    private var type = 0
    private val rpc = mutableMapOf<String, Any>()
    private var webSocket: WebSocket? = null
    private val gson: Gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()

    private var heartbeatRunnable: Runnable? = null
    private var heartbeatThread: Thread? = null
    private var heartbeatInterval = 0
    private var seq = 0

    private var sessionId: String? = null
    private var reconnectSession = false
    private val buttons = mutableListOf<String>()

    init {
        heartbeatRunnable = Runnable {
            try {
                if (heartbeatInterval < 10000) throw RuntimeException("Invalid")
                Thread.sleep(heartbeatInterval.toLong())
                webSocket?.send("{\"op\":1, \"d\":" + (seq.takeIf { it != 0 } ?: "null") + "}")
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    fun closeRPC() {
        heartbeatThread?.interrupt()
        webSocket?.close(1000, "Closing connection")
    }

    fun setName(activityName: String?): DiscordRPC = apply {
        this.activityName = activityName
    }

    fun setDetails(details: String?): DiscordRPC = apply {
        this.details = details
    }

    fun setState(state: String?): DiscordRPC = apply {
        this.state = state
    }

    /**
     * Large image on rpc
     * How to get Image ?
     * Upload image to any discord chat and copy its media link it should look like "https://media.discordapp.net/attachments/90202992002/xyz.png" now just use the image link from attachments part
     * so it would look like: .setLargeImage("attachments/90202992002/xyz.png")
     * @param largeImage
     * @return
     */
    fun setLargeImage(largeImage: String?, largeText: String?): DiscordRPC = apply {
        this.largeImage = largeImage
        this.largeText = largeText
    }

    fun setSmallImage(smallImage: String?, smallText: String?): DiscordRPC = apply {
        this.smallImage = smallImage
        this.smallText = smallText
    }

    fun setStartTimestamps(startTimestamps: Long?): DiscordRPC = apply {
        this.startTimestamps = startTimestamps
    }

    /**
     * Activity Types
     * 0: Playing
     * 1: Streaming
     * 2: Listening
     * 3: Watching
     * 5: Competing
     *
     * @param type
     * @return
     */
    fun setType(type: Int): DiscordRPC = apply {
        this.type = type
    }

    /**
     * Status type for profile online,idle,dnd
     *
     * @param status
     * @return
     */
    fun setStatus(status: String?): DiscordRPC = apply {
        this.status = status
    }

    /**
     * You should be able to set url to buttons, but I wasn't able to correctly implement it T_T,
     * so feel free to add btn links <3
     *
     * https://discord.com/developers/docs/topics/gateway-events#activity-object-activity-buttons
     */
    fun setButton1(label: String): DiscordRPC = apply {
        if (label.length > 32)
            throw Exception("Max allowed label length is 32 chars.")

        buttons.add(label)
    }

    /**
     * You should be able to set url to buttons, but I wasn't able to correctly implement it T_T,
     * so feel free to add btn links <3
     *
     * https://discord.com/developers/docs/topics/gateway-events#activity-object-activity-buttons
     */
    fun setButton2(label: String): DiscordRPC = apply {
        if (label.length > 32)
            throw Exception("Max allowed label length is 32 chars.")

        buttons.add(label)
    }

    fun setWatchingPresence(name: String?, current: String?, total: Int?): DiscordRPC = apply {
        this.details = "Watching: $name"
        this.state = "Episode: $current / ${total ?: "?"}"
        this.startTimestamps = System.currentTimeMillis()
    }

    fun setReadingPresence(name: String?, current: String?, total: Int?): DiscordRPC = apply {
        this.details = "Reading: $name"
        this.state = "Chapter: $current / ${total ?: "?"}"
        this.startTimestamps = System.currentTimeMillis()
    }

    fun setDefaultPresence(): DiscordRPC = apply {
        this.details = "(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧"
        this.state = "°˖✧◝(⁰▿⁰)◜✧˖°"
        this.startTimestamps = System.currentTimeMillis()
    }

    fun build() {
        val presence = mutableMapOf<String, Any?>()
        val activity = mutableMapOf<String, Any?>().apply {
            put("name", activityName)
            put("details", details)
            put("state", state)
            put("type", type)

            val timestamps = mutableMapOf<String, Any?>().apply {
                put("start", startTimestamps)
                put("stop", stopTimestamps)
            }
            put("timestamps", timestamps)

            val assets = mutableMapOf<String, Any?>().apply {
                put("large_image", largeImage)
                put("small_image", smallImage)
                put("large_text", largeText)
                put("small_text", smallText)
            }
            put("assets", assets)

            if (buttons.isNotEmpty()) {
                put("buttons", buttons)
            }
        }
        presence["activities"] = arrayOf(activity)
        presence["afk"] = true
        presence["since"] = startTimestamps
        presence["status"] = status
        rpc["op"] = 3
        rpc["d"] = presence
        createWebSocketClient()
    }

    fun sendData() {
        val presence = mutableMapOf<String, Any?>()
        val activity = mutableMapOf<String, Any?>().apply {
            put("name", activityName)
            put("details", details)
            put("state", state)
            put("type", type)

            val timestamps = mutableMapOf<String, Any?>().apply {
                put("start", startTimestamps)
                put("stop", stopTimestamps)
            }
            put("timestamps", timestamps)

            val assets = mutableMapOf<String, Any?>().apply {
                put("large_image", largeImage)
                put("small_image", smallImage)
                put("large_text", largeText)
                put("small_text", smallText)
            }
            put("assets", assets)

            if (buttons.isNotEmpty()) {
                put("buttons", buttons)
            }
        }
        presence["activities"] = arrayOf(activity)
        presence["afk"] = true
        presence["since"] = startTimestamps
        presence["status"] = status
        rpc["op"] = 3
        rpc["d"] = presence
        webSocket?.send(gson.toJson(rpc))
    }

    fun sendIdentify() {
        val prop = mutableMapOf<String, Any>().apply {
            put("\$os", "windows")
            put("\$browser", "Chrome")
            put("\$device", "disco")
        }
        val data = mutableMapOf<String, Any>().apply {
            put("token", token)
            put("properties", prop)
            put("compress", false)
            put("intents", 0)
        }
        val identify = mutableMapOf<String, Any>().apply {
            put("op", 2)
            put("d", data)
        }
        webSocket?.send(gson.toJson(identify))
    }

    private fun createWebSocketClient() {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("wss://gateway.discord.gg/?encoding=json&v=10")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val map = gson.fromJson<Map<String, Any>>(
                    text,
                    object : TypeToken<Map<String, Any>>() {}.type
                )
                val seq = map["s"] as? Double
                this@DiscordRPC.seq = seq?.toInt() ?: 0

                when ((map["op"] as Double?)!!.toInt()) {
                    0  -> if (map["t"] as String? == "READY") {
                        sessionId = (map["d"] as Map<*, *>?)!!["session_id"].toString()
                        val user = (map["d"] as Map<*, *>?)!!["user"] as? Map<*, *>?
                        DiscordRPCService.userName = "${user?.get("username")}#${user?.get("discriminator")}"

                        if (user?.get("avatar") != null && user["avatar"] != JsonNull.INSTANCE) {
                            DiscordRPCService.userAvatar =
                                "https://cdn.discordapp.com/avatars/${user["id"]}/${user["avatar"]}.png"
                        }

                        webSocket.send(gson.toJson(rpc))
                    }

                    10 -> if (!reconnectSession) {
                        val data = map["d"] as Map<*, *>?
                        heartbeatInterval = (data!!["heartbeat_interval"] as Double?)!!.toInt()
                        heartbeatThread = Thread(heartbeatRunnable)
                        heartbeatThread!!.start()
                        sendIdentify()
                    } else {
                        val data = map["d"] as Map<*, *>?
                        heartbeatInterval = (data!!["heartbeat_interval"] as Double?)!!.toInt()
                        heartbeatThread = Thread(heartbeatRunnable)
                        heartbeatThread!!.start()
                        reconnectSession = false
                        webSocket.send("{\"op\": 6,\"d\":{\"token\":\"$token\",\"session_id\":\"$sessionId\",\"seq\":$seq}}")
                    }

                    1  -> {
                        if (!Thread.interrupted()) {
                            heartbeatThread!!.interrupt()
                        }
                        webSocket.send("{\"op\":1, \"d\":" + (if (seq == 0.0) "null" else seq.toString()) + "}")
                    }

                    11 -> {
                        if (!Thread.interrupted()) {
                            heartbeatThread!!.interrupt()
                        }
                        heartbeatThread = Thread(heartbeatRunnable)
                        heartbeatThread!!.start()
                    }

                    7  -> {
                        reconnectSession = true
                        webSocket.close(400, "Reconnect")
                    }

                    9  -> if (!heartbeatThread!!.isInterrupted) {
                        heartbeatThread!!.interrupt()
                        heartbeatThread = Thread(heartbeatRunnable)
                        heartbeatThread!!.start()
                        sendIdentify()
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                if (code == 4000) {
                    reconnectSession = true
                    heartbeatThread?.interrupt()
                    val newThread = Thread {
                        try {
                            Thread.sleep(200)
                            reconnect()
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }
                    }
                    newThread.start()
                } else {
                    throw RuntimeException("Invalid")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                if (t.message != "Interrupt") {
                    closeRPC()
                }
            }
        })
    }

    private fun reconnect() {
        createWebSocketClient()
    }
}
