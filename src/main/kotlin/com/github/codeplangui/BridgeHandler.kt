package com.github.codeplangui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter

@Serializable
private data class SendMessagePayload(
    val text: String,
    val includeContext: Boolean = true
)

@Serializable
private data class BridgePayload(
    val type: String,
    val text: String = "",
    val includeContext: Boolean = true
)

class BridgeHandler(
    private val browser: JBCefBrowser,
    private val chatService: ChatService
) {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var sendQuery: JBCefJSQuery

    fun register() {
        sendQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
        sendQuery.addHandler { payload ->
            try {
                val req = json.decodeFromString<BridgePayload>(payload)
                when (req.type) {
                    "sendMessage" -> {
                        val sendMessage = json.decodeFromString<SendMessagePayload>(payload)
                        chatService.sendMessage(sendMessage.text, sendMessage.includeContext)
                    }
                    "newChat" -> chatService.newChat()
                }
            } catch (_: Exception) {
                // Ignore malformed bridge payloads.
            }
            null
        }

        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (frame.isMain) {
                    val js = """
                        window.__bridge = {
                            sendMessage: function(text, includeContext) {
                                ${sendQuery.inject("""JSON.stringify({type:'sendMessage',text:text,includeContext:!!includeContext})""")}
                            },
                            newChat: function() {
                                ${sendQuery.inject("""JSON.stringify({type:'newChat',text:''})""")}
                            },
                            onStart: function(msgId) {},
                            onToken: function(token) {},
                            onEnd: function(msgId) {},
                            onError: function(message) {}
                        };
                        document.dispatchEvent(new Event('bridge_ready'));
                    """.trimIndent()
                    browser.executeJavaScript(js, "", 0)
                }
            }
        }, browser.cefBrowser)
    }

    fun notifyStart(msgId: String) = pushJS("window.__bridge.onStart(${msgId.quoted()})")

    fun notifyToken(token: String) = pushJS("window.__bridge.onToken(${json.encodeToString(token)})")

    fun notifyEnd(msgId: String) = pushJS("window.__bridge.onEnd(${msgId.quoted()})")

    fun notifyError(message: String) = pushJS("window.__bridge.onError(${json.encodeToString(message)})")

    private fun pushJS(js: String) {
        ApplicationManager.getApplication().invokeLater {
            browser.cefBrowser.executeJavaScript(js, "", 0)
        }
    }

    private fun String.quoted() = "'${replace("'", "\\'")}'"
}
