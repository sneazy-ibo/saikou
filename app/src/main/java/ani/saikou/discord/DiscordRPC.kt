package ani.saikou.discord

import android.content.Context
import androidx.core.content.edit
import ani.saikou.Mapper
import ani.saikou.R
import ani.saikou.toast
import ani.saikou.tryWith
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.File
import java.util.concurrent.*

class DiscordRPC(private val token: String) {
    companion object {
        private const val TOKEN = "discord_token"
        fun getToken(context: Context): String? {
            val sharedPref = context.getSharedPreferences(
                context.getString(R.string.preference_file_key),
                Context.MODE_PRIVATE
            )
            return sharedPref.getString(TOKEN, null)
        }

        fun saveToken(context: Context, token: String) {
            val sharedPref = context.getSharedPreferences(
                context.getString(R.string.preference_file_key),
                Context.MODE_PRIVATE
            )
            sharedPref.edit {
                putString(TOKEN, token)
                commit()
            }
        }

        fun removeToken(context: Context) {
            val sharedPref = context.getSharedPreferences(
                context.getString(R.string.preference_file_key),
                Context.MODE_PRIVATE
            )
            sharedPref.edit {
                remove(TOKEN)
                commit()
            }

            tryWith(true) {
                val dir = File(context.filesDir?.parentFile, "app_webview")
                if (dir.deleteRecursively())
                    toast(context.getString(R.string.discord_logout_success))
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
    private val rpc = buildJsonObject {  }
    private var webSocket: WebSocket? = null
    private val json = Mapper.json
    
    private var heartbeatRunnable: Runnable? = null
    private lateinit var heartbeatThread: Thread
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
        heartbeatThread.interrupt()
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
        createWebSocketClient()
    }

    private fun createRPC() : JsonObject {
        val activity = buildJsonObject {
            put("name", activityName)
            put("details", details)
            put("state", state)
            put("type", type)

            val timestamps = buildJsonObject {
                put("start", startTimestamps)
                put("stop", stopTimestamps)
            }
            put("timestamps", timestamps)

            val assets = buildJsonObject {
                put("large_image", largeImage)
                put("small_image", smallImage)
                put("large_text", largeText)
                put("small_text", smallText)
            }
            put("assets", assets)

            if (buttons.isNotEmpty()) {
                put("buttons", buttons.toJsonArray())
            }
        }
        val presence = buildJsonObject {
            put("activities", buildJsonArray {
                add(activity)
            })
            put("afk", true)
            put("since", startTimestamps)
            put("status", status)
        }
        return buildJsonObject {
            put("op", 3)
            put("d", presence)
        }
    }

    fun sendData() {
        webSocket?.send(createRPC().toString())
    }

    fun sendIdentify() {
        val prop = buildJsonObject {
            put("\$os", "windows")
            put("\$browser", "Chrome")
            put("\$device", "disco")
        }
        val data = buildJsonObject {
            put("token", token)
            put("properties", prop)
            put("compress", false)
            put("intents", 0)
        }
        val identify = buildJsonObject {
            put("op", 2)
            put("d", data)
        }
        webSocket?.send(identify.toString())
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
                val map = json.decodeFromString<JsonObject>(text)
                val seq = map["s"]?.jsonPrimitive?.intOrNull
                this@DiscordRPC.seq = seq ?: 0

                when (map["op"]?.jsonPrimitive?.intOrNull) {
                    0  -> if (map["t"]?.jsonPrimitive?.content == "READY") {
                        sessionId = map["d"]?.jsonObject?.get("session_id")?.jsonPrimitive?.content
                        val user = map["d"]?.jsonObject?.get("user")?.jsonObject
                        DiscordRPCService.userName = "${user?.get("username")?.jsonPrimitive?.content}"
                        val avatar = user?.get("avatar")?.jsonPrimitive?.content
                        if (avatar != null) {
                            DiscordRPCService.userAvatar =
                                "https://cdn.discordapp.com/avatars/${user["id"]?.jsonPrimitive?.content}/${user["avatar"]}.png"
                        }
                        sendData()
                    }

                    10 -> {
                        val data = map["d"]?.jsonObject!!
                        heartbeatInterval = data["heartbeat_interval"]!!.jsonPrimitive.int
                        heartbeatThread = Thread(heartbeatRunnable).apply { start() }
                        if (!reconnectSession)
                            sendIdentify()
                        else {
                            reconnectSession = false
                            webSocket.send("{\"op\": 6,\"d\":{\"token\":\"$token\",\"session_id\":\"$sessionId\",\"seq\":$seq}}")
                        }
                    }

                    1  -> {
                        if (!Thread.interrupted()) {
                            heartbeatThread.interrupt()
                        }
                        webSocket.send("{\"op\":1, \"d\":" + (if (seq == 0) "null" else seq.toString()) + "}")
                    }

                    11 -> {
                        if (!Thread.interrupted()) {
                            heartbeatThread.interrupt()
                        }
                        heartbeatThread = Thread(heartbeatRunnable)
                        heartbeatThread.start()
                    }

                    7  -> {
                        reconnectSession = true
                        webSocket.close(400, "Reconnect")
                    }

                    9  -> if (!heartbeatThread.isInterrupted) {
                        heartbeatThread.interrupt()
                        heartbeatThread = Thread(heartbeatRunnable).apply { start() }
                        sendIdentify()
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                if (code == 4000) {
                    reconnectSession = true
                    heartbeatThread.interrupt()
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

private fun List<String>.toJsonArray() = buildJsonArray { 
    forEach { add(it) }
}
