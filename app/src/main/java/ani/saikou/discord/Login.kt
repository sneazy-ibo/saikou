package ani.saikou.discord

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import ani.saikou.R
import ani.saikou.printIt
import ani.saikou.saveData
import ani.saikou.startMainActivity
import ani.saikou.toast
import ani.saikou.tryWith
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FilenameFilter

class Login : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_discord)

        val token = extractToken()
        if(token!=null) login(token)
        else{
            val webView = findViewById<WebView>(R.id.discordWebview)
            webView.apply {
                settings.javaScriptEnabled = true
                settings.databaseEnabled = true
                settings.domStorageEnabled = true
                clearCache(true)
                clearFormData()
                clearHistory()
                clearSslPreferences()
            }
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()

            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val str = request.url.toString()
                    if (str.endsWith("/app")) {
                        webView.stopLoading()
                        handleToken()
                        return false
                    }
                    return super.shouldOverrideUrlLoading(view, request)
                }
            }
            webView.loadUrl("https://discord.com/login")
        }
    }

    private fun handleToken() {
        finish()
        val token = extractToken()
        if(token!=null) login(token)
        else toast(getString(R.string.discord_try_again))
    }
    private fun login(token: String) {
        DiscordRPC.saveToken(this, token)
        startMainActivity(this@Login)
    }

    private fun extractToken(): String? {
        return tryWith {
            class DiscordFilenameFilter : FilenameFilter {
                override fun accept(dir: File?, str: String): Boolean {
                    return str.endsWith(".log")
                }
            }

            val listFiles =
                File(filesDir.parentFile, "app_webview/Default/Local Storage/leveldb")
                    .listFiles(DiscordFilenameFilter())
                    ?: return@tryWith null
            listFiles.printIt("List Files : ")
            if (listFiles.isEmpty()) return@tryWith null

            val bufferedReader = BufferedReader(FileReader(listFiles[0]))
            val token = bufferedReader.readLines()
                .also { saveData("01uhh.txt", it) }
                .find { it.contains("token") }
                ?.let {
                    it.printIt("Line : ")
                    val substring = it.substring(it.indexOf("token") + 5)
                    val substring2 = substring.substring(substring.indexOf("\"") + 1)
                    substring2.substring(0, substring2.indexOf("\""))
                }
            token
        }
    }
}
