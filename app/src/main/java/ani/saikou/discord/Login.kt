package ani.saikou.discord

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import ani.saikou.R
import ani.saikou.startMainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URI

class Login : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_discord)

        lifecycleScope.launch {
            val webView = findViewById<WebView>(R.id.discordWebview)

            webView.settings.apply {
                javaScriptEnabled = true
                databaseEnabled = true
                domStorageEnabled = true
            }

            var token: String? = null
            var isLoggedIn = false

            webView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    if (url == null || URI(url).path != "/app" || !url.contains("/channels/")) return;

                    webView.evaluateJavascript("""
                            (function() {
                                const iframe = document.createElement("iframe");
        
                                document.head.append(iframe);
                                const localStorage = Object.getOwnPropertyDescriptor(iframe.contentWindow, "localStorage");
        
                                iframe.remove();
                                const token = localStorage.get.call(window).getItem("token");
                                return token.substr(1, token.length-2);
                            })()
                        """.trimIndent()) {
                        if (it == "null") token = null
                        else {
                            token = it.substring(1, it.length-1)
                            isLoggedIn = true;
                        }
                    }
                }
            }

            webView.loadUrl("https://discord.com/login")

            var loop = 0
            val totalTime = 60 * 1000L // 60 seconds

            val delayTime = 100L

            while ((loop < (totalTime / delayTime)) && (token == null)) {
                delay(delayTime)
                if (isLoggedIn) loop += 1
            }

            webView.destroy()
            Log.d("DiscordLogin", "Discord token: $token")

            if (loop >= totalTime / delayTime)
                Log.d("DiscordLogin", "Webview timeout after ${totalTime / 1000}s")
            else {
                val sharedPref = this@Login.getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE)
                sharedPref.edit {
                    putString("discord_token", token)
                    commit()
                }

                startMainActivity(this@Login)
            }
        }
    }
}
