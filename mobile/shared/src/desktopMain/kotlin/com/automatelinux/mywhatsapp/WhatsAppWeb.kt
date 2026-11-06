package com.automatelinux.mywhatsapp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.ConnectException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Drives the WhatsApp Web tab of the everyday Chrome over DevTools (port 9222), so Show and
 * Message land in the tab that is already open. A URL handed to the browser can't do this:
 * it always makes a new tab, which takes the session from the old one ("Use here"), and
 * WhatsApp Web has no link to a group or a message at all.
 *
 * Two rules protect the user's real browser and real chats:
 *  - Text is only ever inserted into a search <input> that was verified to hold focus (the
 *    message composer is a contenteditable DIV), and Enter is never pressed — nothing here
 *    can send a message.
 *  - Never Browser.setDownloadBehavior and never Playwright on 9222: either one permanently
 *    kills Chrome's download buttons (see automateLinux .claude/CLAUDE.md).
 */
internal object WhatsAppWeb {
    private const val DEVTOOLS = "http://127.0.0.1:9222"
    private const val HOME = "https://web.whatsapp.com/"

    // WhatsApp Web's markup, all in one place for when it changes.
    private const val SIDE_SEARCH = "document.querySelector('#side input[role=textbox]')"
    private const val CHAT_TITLE = "document.querySelector('#main [data-testid=conversation-info-header] span[dir=auto]')"
    private const val CHAT_SEARCH_BUTTON = "document.querySelector('#main header button[aria-label=\"Search\"]')"
    private const val CHAT_SEARCH_BOX =
        "[...document.querySelectorAll('input[aria-label=\"Search\"]')].find(e => !e.closest('#side') && !e.closest('#main'))"
    private const val SEARCH_RESULTS = "[...document.querySelectorAll('[data-testid=chat-search-message-list] [role=listitem]')]"
    private const val CHAT_ROWS = "[...document.querySelectorAll('#side [role=row]')]"
    private const val USE_HERE = "[...document.querySelectorAll('button')].find(b => b.innerText.trim() === 'Use here')"

    /** A post's opening words: enough to tell it apart, as WhatsApp's search matches any post holding all of them. */
    private const val QUERY_WORDS = 8

    /** Same word-splitting as [words], on the page: emoji, formatting and line breaks don't take part in a comparison. */
    private const val PAGE_HELPERS =
        "const words = s => ((s || '').match(/[\\p{L}\\p{N}]+/gu) || []).join(' ');" +
            "const center = e => { e.scrollIntoView({block: 'nearest'}); const r = e.getBoundingClientRect();" +
            " return {x: r.x + r.width / 2, y: r.y + r.height / 2}; };"

    private val http: HttpClient = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /** One at a time, off the UI thread: a job takes seconds and two would fight over one tab. */
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "whatsapp-web").apply { isDaemon = true } }

    private class Failure(message: String) : Exception(message)

    /** Open [chatName] and land on the post with [text]. */
    fun show(chatName: String, text: String) = enqueue { tab ->
        tab.openChat(chatName)
        val query = words(text).take(QUERY_WORDS).joinToString(" ")
        if (query.isNotEmpty()) tab.landOn(query, chatName)
    }

    /** Open the private chat with [phone] (WhatsApp Web does have a link for this one). */
    fun message(phone: String) = enqueue { tab ->
        tab.navigate("${HOME}send?phone=$phone")
    }

    /** Just bring the WhatsApp tab to the front. */
    fun focus() = enqueue { }

    private fun enqueue(job: (Tab) -> Unit) {
        worker.execute {
            try {
                val target = whatsAppTarget()
                raise(target)
                Tab.connect(target.webSocket).use { tab ->
                    tab.waitUntilReady()
                    job(tab)
                }
            } catch (e: Failure) {
                notifyUser(e.message ?: "WhatsApp Web failed")
            } catch (e: Exception) {
                notifyUser("Couldn't drive WhatsApp Web: ${e.message}")
            }
        }
    }

    private fun words(s: String): List<String> = Regex("[\\p{L}\\p{N}]+").findAll(s).map { it.value }.toList()

    private fun quoted(s: String) = JsonPrimitive(s).toString()

    /** The result lands in another window, so it's said in a notification: text in this window would be behind Chrome. */
    private fun notifyUser(message: String) {
        System.err.println("WhatsApp Web: $message")
        runCatching { ProcessBuilder("notify-send", "-a", "MyWhatsApp", "MyWhatsApp", message).inheritIO().start() }
            .onFailure { System.err.println("notify-send failed: ${it.message}") }
    }

    // ---- Which tab ----

    private class Target(val id: String, val webSocket: String)

    private fun request(path: String, method: String = "GET"): String {
        val req = HttpRequest.newBuilder(URI("$DEVTOOLS$path")).method(method, HttpRequest.BodyPublishers.noBody())
            .timeout(java.time.Duration.ofSeconds(5)).build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() !in 200..299) throw Failure("Chrome answered ${res.statusCode()} to $path")
        return res.body()
    }

    private fun toTarget(o: JsonObject) = Target(
        o.getValue("id").jsonPrimitive.content,
        o.getValue("webSocketDebuggerUrl").jsonPrimitive.content,
    )

    /** Chrome's open pages; starts Chrome (on WhatsApp Web) when it isn't running. */
    private fun pages(): List<JsonObject> {
        fun list() = json.parseToJsonElement(request("/json/list")).jsonArray.map { it.jsonObject }
            .filter { it["type"]?.jsonPrimitive?.content == "page" }
        try {
            return list()
        } catch (_: ConnectException) {
            ProcessBuilder("xdg-open", HOME).start()
        }
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(500)
            try { return list() } catch (_: ConnectException) { }
        }
        throw Failure("Chrome didn't open its debugging port (9222) — is it running without it?")
    }

    /** The tab holding the live session when there's one, a dormant "Use here" tab next, a new tab only if none is open. */
    private fun whatsAppTarget(): Target {
        val open = pages().filter { it["url"]?.jsonPrimitive?.content?.startsWith(HOME) == true }.map(::toTarget)
        if (open.isEmpty()) {
            val encoded = URLEncoder.encode(HOME, Charsets.UTF_8)
            return toTarget(json.parseToJsonElement(request("/json/new?$encoded", "PUT")).jsonObject)
        }
        if (open.size == 1) return open.single()
        return open.firstOrNull { t ->
            Tab.connect(t.webSocket).use { it.eval("return !!$SIDE_SEARCH;").jsonPrimitive.booleanOrNull == true }
        } ?: open.first()
    }

    /** Selecting the tab doesn't raise Chrome's window (measured), and it's usually behind this app: the daemon does that. */
    private fun raise(target: Target) {
        request("/json/activate/${target.id}")
        val deadline = System.currentTimeMillis() + 3_000
        while (true) {
            val window = json.parseToJsonElement(daemon("listWindows")).jsonArray.map { it.jsonObject }.firstOrNull {
                it["wm_class"]?.jsonPrimitive?.contentOrNull == "google-chrome" &&
                    it["title"]?.jsonPrimitive?.contentOrNull?.contains("WhatsApp") == true
            }
            if (window != null) {
                daemon("activateWindow", "--windowId", window.getValue("id").jsonPrimitive.content)
                return
            }
            if (System.currentTimeMillis() > deadline) throw Failure("Couldn't find Chrome's WhatsApp window to raise")
            Thread.sleep(200)
        }
    }

    private fun daemon(vararg args: String): String {
        val p = ProcessBuilder(listOf("daemon", "send") + args).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(10, TimeUnit.SECONDS) || p.exitValue() != 0) throw Failure("daemon ${args[0]} failed: $out")
        return out
    }

    // ---- One tab, over DevTools ----

    private class Tab(private val socket: WebSocket, private val pending: ConcurrentHashMap<Int, CompletableFuture<JsonObject>>) : AutoCloseable {
        private val nextId = AtomicInteger()

        fun send(method: String, params: JsonObject = JsonObject(emptyMap())): JsonObject {
            val id = nextId.incrementAndGet()
            val answer = CompletableFuture<JsonObject>()
            pending[id] = answer
            val message = buildJsonObject { put("id", id); put("method", method); put("params", params) }
            socket.sendText(message.toString(), true).join()
            val reply = answer.get(15, TimeUnit.SECONDS)
            reply["error"]?.let { throw Failure("Chrome refused $method: $it") }
            return reply.getValue("result").jsonObject
        }

        /** Run [body] (a function body that returns a value) on the page. */
        fun eval(body: String): JsonElement {
            val result = send("Runtime.evaluate", buildJsonObject {
                put("expression", "(() => { $PAGE_HELPERS $body })()")
                put("returnByValue", true)
            })
            result["exceptionDetails"]?.let { throw Failure("WhatsApp Web's page changed: $it") }
            return result.getValue("result").jsonObject["value"] ?: JsonNull
        }

        /** Keep evaluating [body] until it returns something truthy; null when [millis] run out. */
        fun poll(body: String, millis: Long): JsonElement? {
            val deadline = System.currentTimeMillis() + millis
            while (true) {
                val value = eval(body)
                val empty = value is JsonNull || (value as? JsonPrimitive)?.booleanOrNull == false
                if (!empty) return value
                if (System.currentTimeMillis() > deadline) return null
                Thread.sleep(150)
            }
        }

        /** A real mouse click: WhatsApp's rows ignore a scripted el.click(). */
        fun click(point: JsonElement) {
            val x = point.jsonObject.getValue("x").jsonPrimitive.double
            val y = point.jsonObject.getValue("y").jsonPrimitive.double
            listOf("mouseMoved", "mousePressed", "mouseReleased").forEach { type ->
                send("Input.dispatchMouseEvent", buildJsonObject {
                    put("type", type); put("x", x); put("y", y)
                    put("button", if (type == "mouseMoved") "none" else "left")
                    put("clickCount", if (type == "mouseMoved") 0 else 1)
                })
            }
        }

        /** Replace the text of the search <input> [box] finds. Refuses unless that input really has the focus. */
        fun search(box: String, text: String) {
            // Clicked until it holds the focus: the in-chat panel slides in, so an early click lands beside the box.
            val focused = "const e = $box; return !!e && e.tagName === 'INPUT' && document.activeElement === e;"
            val deadline = System.currentTimeMillis() + 4_000
            while (true) {
                val point = eval("const e = $box; return e && e.getBoundingClientRect().width > 0 ? center(e) : null;")
                if (point !is JsonNull) click(point)
                if (poll(focused, 250) != null) break
                if (System.currentTimeMillis() > deadline) {
                    throw Failure("WhatsApp Web's search box wouldn't take focus, so nothing was typed")
                }
            }
            eval("$box.select(); return true;")
            send("Input.insertText", buildJsonObject { put("text", text) })
        }

        fun navigate(url: String) {
            send("Page.navigate", buildJsonObject { put("url", url) })
            Thread.sleep(1_000) // the old document still answers for a moment
            waitUntilReady()
        }

        /** Signed in with the chat list up. A dormant tab ("open in another window") is taken over, as asked of this tab. */
        fun waitUntilReady() {
            val deadline = System.currentTimeMillis() + 30_000
            while (System.currentTimeMillis() < deadline) {
                if (eval("return !!$SIDE_SEARCH;").jsonPrimitive.booleanOrNull == true) return
                val useHere = eval("const b = $USE_HERE; return b ? center(b) : null;")
                if (useHere !is JsonNull) click(useHere)
                Thread.sleep(300)
            }
            throw Failure("WhatsApp Web isn't ready in Chrome — is it signed in?")
        }

        /** Find [name] through the chat list's search: there's no other way to reach a group in WhatsApp Web. */
        fun openChat(name: String) {
            val want = quoted(words(name).joinToString(" "))
            if (want == "\"\"") throw Failure("This group's name has no words to search WhatsApp for")
            val isOpen = "return words($CHAT_TITLE?.innerText) === $want;"
            if (eval(isOpen).jsonPrimitive.booleanOrNull == true) return
            search(SIDE_SEARCH, words(name).joinToString(" "))
            val row = poll(
                "const r = $CHAT_ROWS.find(r => [...r.querySelectorAll('span[title]')]" +
                    ".some(s => words(s.getAttribute('title')) === $want)); return r ? center(r) : null;",
                8_000,
            ) ?: run {
                clearChatListSearch()
                throw Failure("\"$name\" wasn't found in this WhatsApp account")
            }
            click(row)
            val opened = poll(isOpen, 5_000)
            clearChatListSearch()
            if (opened == null) throw Failure("WhatsApp Web didn't open \"$name\"")
        }

        /** Leave the chat list as the user had it. */
        private fun clearChatListSearch() {
            eval("const e = $SIDE_SEARCH; if (e && e.value) { e.focus(); e.select(); document.execCommand('delete'); } return true;")
        }

        /**
         * Search the open chat and click the post. WhatsApp's search matches words anywhere in
         * any order, so the first result is often someone else's post: only one holding the
         * opening words in order counts. Newest first, so a repost lands on its latest copy.
         */
        fun landOn(query: String, chatName: String) {
            val panelOpen = eval("return !!$CHAT_SEARCH_BOX;").jsonPrimitive.booleanOrNull == true
            if (!panelOpen) {
                val button = eval("const b = $CHAT_SEARCH_BUTTON; return b ? center(b) : null;")
                if (button is JsonNull) throw Failure("Opened \"$chatName\", but its Search button isn't where it was")
                click(button)
            }
            search(CHAT_SEARCH_BOX, query)
            val want = quoted(query)
            val result = poll(
                "const r = $SEARCH_RESULTS.find(e => words(e.innerText).includes($want)); return r ? center(r) : null;",
                6_000,
            ) ?: throw Failure("Opened \"$chatName\", but WhatsApp Web's search can't find this post — it only searches what it has synced")
            click(result)
            poll("return words(document.querySelector('#main')?.innerText).includes($want);", 4_000)
                ?: throw Failure("Opened \"$chatName\" and searched, but the post didn't come into view")
        }

        override fun close() {
            socket.abort()
        }

        companion object {
            fun connect(url: String): Tab {
                val pending = ConcurrentHashMap<Int, CompletableFuture<JsonObject>>()
                val socket = http.newWebSocketBuilder().buildAsync(URI(url), object : WebSocket.Listener {
                    private val buffer = StringBuilder()

                    override fun onText(ws: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                        buffer.append(data)
                        if (last) {
                            val message = json.parseToJsonElement(buffer.toString()).jsonObject
                            buffer.clear()
                            message["id"]?.jsonPrimitive?.content?.toIntOrNull()?.let { pending.remove(it)?.complete(message) }
                        }
                        ws.request(1)
                        return null
                    }
                }).get(5, TimeUnit.SECONDS)
                return Tab(socket, pending)
            }
        }
    }
}
