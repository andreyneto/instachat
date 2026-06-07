package com.aneto.instachat.core.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.aneto.instachat.core.auth.SessionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's data backend. A single long-lived WebView parked on instagram.com,
 * attached to the Activity window (offscreen, behind the Compose UI, technically
 * visible) so the real IG web client hydrates cookies/CSRF/fb_dtsg/lsd natively
 * and so visibility-gated behaviours (read receipts) actually fire.
 *
 * Every Instagram operation runs as a `fetch()` from inside the authenticated
 * page via a generic JS dispatcher; the JSON response is handed back to Kotlin
 * over the [bridge] interface keyed by a callId. Responses are the same JSON
 * shapes the DTOs already parse, so callers just deserialize the returned string.
 */
@Singleton
class WebBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionStore: SessionStore,
    private val json: Json,
) {

    private val main = Handler(Looper.getMainLooper())
    private var webView: WebView? = null

    /** In-flight fetch calls, keyed by callId. */
    private val pending = ConcurrentHashMap<String, CompletableDeferred<String>>()

    /** Completes once the warm page has loaded for the first time. */
    private val ready = CompletableDeferred<Boolean>()

    /** Completes on the next onPageFinished — used to await navigations. */
    @Volatile private var pageLoad: CompletableDeferred<String>? = null

    /** Serialises navigations (mark-read) so only one URL is loaded at a time. */
    private val navMutex = Mutex()

    private val bridge = object {
        @JavascriptInterface
        fun onResult(callId: String, payload: String) {
            pending.remove(callId)?.complete(payload)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun boot() {
        if (webView != null) return
        main.post {
            if (webView != null) return@post
            CookieManager.getInstance().setAcceptCookie(true)
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = UA
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                addJavascriptInterface(bridge, "InstaBridge")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        android.util.Log.i(TAG, "loaded $url")
                        pageLoad?.complete(url.orEmpty())
                        if (!ready.isCompleted && url?.startsWith(ORIGIN) == true) {
                            ready.complete(true)
                        }
                    }
                }
                loadUrl(WARM_URL)
            }
        }
    }

    /** Attach the backing WebView to [activity]'s content, offscreen & near-invisible. */
    fun attachTo(activity: Activity) {
        boot()
        main.post {
            val wv = webView ?: return@post
            (wv.parent as? ViewGroup)?.removeView(wv)
            val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return@post
            wv.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            wv.alpha = 0.01f
            content.addView(wv, 0)
        }
    }

    fun detach() {
        main.post { (webView?.parent as? ViewGroup)?.removeView(webView) }
    }

    private suspend fun awaitReady(): Boolean {
        if (ready.isCompleted) return true
        boot()
        return withTimeoutOrNull(READY_TIMEOUT_MS) { ready.await() } ?: false
    }

    // ---- Public RPC surface ------------------------------------------------

    suspend fun inbox(cursor: String?): String = rest(
        method = "GET",
        path = "/api/v1/direct_v2/inbox/",
        query = buildJsonObject {
            put("thread_message_limit", "20")
            put("persistentBadging", "true")
            put("limit", "20")
            if (!cursor.isNullOrBlank()) put("cursor", cursor)
        },
    )

    suspend fun thread(threadId: String, cursor: String?): String = rest(
        method = "GET",
        path = "/api/v1/direct_v2/threads/$threadId/",
        query = buildJsonObject {
            put("limit", "30")
            if (!cursor.isNullOrBlank()) put("cursor", cursor)
        },
    )

    /**
     * Send a text message by driving IG's own composer in the backing WebView.
     * The REST `broadcast/text/` route is the native-app endpoint and rejects browser
     * User-Agents ("useragent mismatch"); web sends go over IG's realtime socket, which
     * we reach by typing into the Lexical composer and pressing Enter, exactly like a user.
     */
    suspend fun sendTextViaDom(threadFbid: String, text: String): Boolean {
        val want = json.encodeToString(text)
        val body = """
              function sleep(ms){ return new Promise(function(r){ setTimeout(r, ms); }); }
              function composer(){
                return document.querySelector('div[contenteditable="true"][role="textbox"]')
                    || document.querySelector('div[aria-label][contenteditable="true"]')
                    || document.querySelector('div[contenteditable="true"]')
                    || document.querySelector('textarea');
              }
              function readText(b){ return b ? (b.value != null ? b.value : (b.textContent || '')) : ''; }
              var text = $want;
              var box = null;
              for (var i = 0; i < 25 && !box; i++) { box = composer(); if (!box) await sleep(300); }
              if (!box) return 'no-composer';
              // Replace whatever is in the composer (IG persists a draft across navigations,
              // so we must clear it) with exactly `text`, then verify before sending.
              function setComposer(b){
                b.focus();
                if (b.tagName === 'TEXTAREA') {
                  var setter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set;
                  setter.call(b, text);
                  b.dispatchEvent(new Event('input', { bubbles: true }));
                  return;
                }
                // contenteditable (Lexical): select all so insertText replaces the whole content.
                try {
                  var sel = window.getSelection(), r = document.createRange();
                  r.selectNodeContents(b); sel.removeAllRanges(); sel.addRange(r);
                } catch (e) {}
                var ok = document.execCommand && document.execCommand('insertText', false, text);
                if (!ok) {
                  b.dispatchEvent(new InputEvent('beforeinput', { inputType: 'insertReplacementText', data: text, bubbles: true, cancelable: true }));
                  b.dispatchEvent(new InputEvent('input', { inputType: 'insertText', data: text, bubbles: true }));
                }
              }
              setComposer(box);
              await sleep(200);
              if (readText(box).trim() !== text.trim()) {
                // Retry once: hard-clear via select-all + delete, then re-insert.
                box.focus();
                try {
                  var sel2 = window.getSelection(), r2 = document.createRange();
                  r2.selectNodeContents(box); sel2.removeAllRanges(); sel2.addRange(r2);
                } catch (e) {}
                document.execCommand && document.execCommand('delete', false, null);
                await sleep(80);
                setComposer(box);
                await sleep(200);
              }
              if (readText(box).trim() !== text.trim()) return 'bad-insert:' + readText(box).slice(0, 40);
              // Send via Enter (IG's composer submits on Enter).
              ['keydown','keypress','keyup'].forEach(function(t){
                box.dispatchEvent(new KeyboardEvent(t, { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true, cancelable: true }));
              });
              for (var w = 0; w < 12; w++) { await sleep(200); if (readText(composer()).trim() === '') return 'ok'; }
              // Fallback: click an explicit Send button if Enter didn't clear the box.
              var send = Array.from(document.querySelectorAll('[role="button"], button, div[role="button"]'))
                .find(function(b){ var t = (b.textContent || '').trim().toLowerCase(); return t === 'send' || t === 'enviar'; });
              if (send) { send.click(); for (var s = 0; s < 12; s++) { await sleep(200); if (readText(composer()).trim() === '') return 'ok'; } }
              return 'not-sent';
        """.trimIndent()

        val result = navMutex.withLock {
            if (!awaitReady()) return@withLock "not-ready"
            navigateTo("$ORIGIN/direct/t/$threadFbid/")
            delay(REACT_SETTLE_MS)
            focusWebView()
            val r = evalAsync(body)
            delay(400)
            navigateTo(WARM_URL)
            r
        }
        android.util.Log.i(TAG, "sendTextViaDom -> $result")
        return result.contains("ok")
    }

    /** Give the offscreen WebView input focus so the composer accepts execCommand text. */
    private fun focusWebView() {
        main.post {
            webView?.apply {
                isFocusableInTouchMode = true
                isFocusable = true
                requestFocus()
            }
        }
    }

    /**
     * Reactions are not exposed over any REST/GraphQL route on the web origin — IG's
     * web client sends them over its realtime socket. So we drive its own UI: navigate
     * to the thread, hover the target bubble to reveal the action buttons, click
     * "React to message…", then click the matching emoji in the quick-reaction tray.
     *
     * [anchorText] disambiguates which bubble to hover (the message's text); when null
     * (media messages) we fall back to the most recent bubble.
     */
    suspend fun reactViaDom(threadFbid: String, emoji: String, anchorText: String?): Boolean {
        val want = json.encodeToString(emoji)
        val anchor = anchorText?.let { json.encodeToString(it) } ?: "null"

        val body = """
              function norm(s){ return (s||'').replace(/️/g,'').trim(); }
              function sleep(ms){ return new Promise(function(r){ setTimeout(r, ms); }); }
              function fire(el, types){
                var r = el.getBoundingClientRect();
                var o = {bubbles:true,cancelable:true,view:window,clientX:r.x+r.width/2,clientY:r.y+r.height/2};
                types.forEach(function(t){
                  el.dispatchEvent(t.indexOf('pointer')===0 ? new PointerEvent(t,o) : new MouseEvent(t,o));
                });
              }
              function findEmoji(w){
                // Scope to the open reaction dialog so we never grab a stray decorative emoji
                // elsewhere on the page, then take the leaf element rendering exactly this emoji.
                var root = document.querySelector('[role=dialog]') || document.body;
                var nodes = Array.from(root.querySelectorAll('*'));
                return nodes.find(function(e){
                  if (e.children.length > 0) return false;
                  var t = norm(e.getAttribute && e.getAttribute('alt') || '') || norm(e.textContent);
                  return t === w && Array.from(t).length <= 2;
                }) || null;
              }
              function clickThrough(el){
                // Fire the full pointer sequence on the emoji leaf and its nearest ancestors —
                // whichever level carries the click handler will receive it.
                var n = el, depth = 0;
                while (n && depth < 4) {
                  fire(n, ['pointerover','pointerenter','pointerdown','mousedown','pointerup','mouseup','click']);
                  n = n.parentElement; depth++;
                }
              }
              var anchor = $anchor;
              var w = norm($want);
              // The thread DOM mounts asynchronously after navigation — poll for it.
              function locate(){
                var bubbles = Array.from(document.querySelectorAll('div[dir=auto]'));
                if (anchor) {
                  return bubbles.find(function(d){ return (d.textContent||'').trim() === anchor.trim(); })
                      || bubbles.find(function(d){ return (d.textContent||'').indexOf(anchor) >= 0; })
                      || null;
                }
                return bubbles.length ? bubbles[bubbles.length-1] : null;
              }
              var target = null;
              for (var w0 = 0; w0 < 20 && !target; w0++) { target = locate(); if(!target) await sleep(300); }
              if (!target) return 'no-target';
              var row = target.closest('[role=row]') || target.parentElement || target;
              var reachedTray = false;
              for (var attempt = 0; attempt < 5; attempt++) {
                fire(row, ['pointerover','pointerenter','mouseover','mouseenter','mousemove']);
                await sleep(250);
                var rb = document.querySelector('[aria-label^="React to message"]');
                if (rb) fire(rb, ['pointerover','pointerenter','pointerdown','mousedown','pointerup','mouseup','click']);
                // poll for the quick-reaction tray to render
                var hit = null;
                for (var p = 0; p < 8 && !hit; p++) { await sleep(150); hit = findEmoji(w); }
                if (!hit) continue;
                reachedTray = true;
                clickThrough(hit);
                await sleep(700);
                // The tray closes once the reaction is actually sent — our success signal.
                if (!document.querySelector('[role=dialog]')) return 'ok';
              }
              return reachedTray ? 'tray-no-send' : 'no-tray';
        """.trimIndent()

        val result = navMutex.withLock {
            if (!awaitReady()) return@withLock "not-ready"
            navigateTo("$ORIGIN/direct/t/$threadFbid/")
            delay(REACT_SETTLE_MS)
            val r = evalAsync(body)
            delay(400)
            navigateTo(WARM_URL)
            r
        }
        android.util.Log.i(TAG, "reactViaDom emoji=$emoji -> $result")
        return result.contains("ok")
    }

    /** Legacy mobile "seen" endpoint — most direct read semantic if the web origin honours it. */
    suspend fun markSeenRest(threadId: String, itemId: String): String = rest(
        method = "POST",
        path = "/api/v1/direct_v2/threads/$threadId/items/$itemId/seen/",
        form = buildJsonObject {
            put("use_unified_inbox", "true")
            put("action", "mark_seen")
            put("thread_id", threadId)
            put("item_id", itemId)
        },
    )

    /**
     * Navigate the visible WebView to the thread and let IG's own client mount it
     * and emit the native read receipt, then return to the warm inbox page.
     * Serialised so concurrent mark-reads don't fight over the single WebView.
     */
    suspend fun navigateAndSettle(threadFbid: String): Boolean = navMutex.withLock {
        if (!awaitReady()) return@withLock false
        val loaded = navigateTo("$ORIGIN/direct/t/$threadFbid/")
        delay(READ_SETTLE_MS)
        navigateTo(WARM_URL)
        loaded
    }

    /**
     * Run an async JS body that resolves to a string. [asyncBody] is the body of an
     * `async function(){ ... }` — it may `await` and must `return` a string. The result
     * is delivered back over the InstaBridge callback, so internal setTimeout/polling
     * works (unlike plain [eval], which can't await a promise return value).
     */
    private suspend fun evalAsync(asyncBody: String): String {
        val callId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<String>()
        pending[callId] = deferred
        val js = """
            (function(){
              var cid = "$callId";
              (async function(){ $asyncBody })()
                .then(function(r){ window.InstaBridge.onResult(cid, String(r)); })
                .catch(function(e){ window.InstaBridge.onResult(cid, 'err ' + (e && e.message || e)); });
            })();
        """.trimIndent()
        main.post { webView?.evaluateJavascript(js, null) }
        return withTimeoutOrNull(CALL_TIMEOUT_MS) { deferred.await() }
            ?: run {
                pending.remove(callId)
                "timeout"
            }
    }

    private suspend fun navigateTo(url: String): Boolean {
        val done = CompletableDeferred<String>()
        pageLoad = done
        main.post { webView?.loadUrl(url) }
        val res = withTimeoutOrNull(NAV_TIMEOUT_MS) { done.await() }
        pageLoad = null
        return res != null
    }

    /** Generic GraphQL escape hatch; reads fb_dtsg/lsd/av live from the page. */
    suspend fun graphql(docId: String, friendlyName: String, variablesJson: String): String = dispatch(
        buildJsonObject {
            put("kind", "graphql")
            put("docId", docId)
            put("friendlyName", friendlyName)
            put("variables", variablesJson)
        },
    )

    private suspend fun rest(
        method: String,
        path: String,
        query: JsonObject? = null,
        form: JsonObject? = null,
    ): String = dispatch(
        buildJsonObject {
            put("kind", "rest")
            put("method", method)
            put("path", path)
            if (query != null) put("query", query)
            if (form != null) put("form", form)
        },
    )

    // ---- Core fetch plumbing -----------------------------------------------

    private suspend fun dispatch(spec: JsonObject): String {
        if (!awaitReady()) throw WebBridgeException("bridge not ready")
        val callId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<String>()
        pending[callId] = deferred
        val js = DISPATCHER_JS + "\nwindow.__ig.call(\"" + callId + "\"," + spec.toString() + ");"
        main.post { webView?.evaluateJavascript(js, null) }
        val payload = withTimeoutOrNull(CALL_TIMEOUT_MS) { deferred.await() }
            ?: run {
                pending.remove(callId)
                throw WebBridgeException("timeout")
            }
        val env = json.decodeFromString<RpcEnvelope>(payload)
        if (env.status in 200..299) return env.body
        if (env.status == 401 || env.status == 403 ||
            env.body.contains("login_required") || env.body.contains("\"checkpoint")
        ) {
            sessionStore.invalidate()
            throw WebBridgeException("session expired (${env.status})")
        }
        throw WebBridgeException("http ${env.status}: ${env.body.take(180)}")
    }

    @Serializable
    private data class RpcEnvelope(val status: Int = 0, val body: String = "")

    companion object {
        private const val TAG = "WebBridge"
        private const val ORIGIN = "https://www.instagram.com"
        private const val WARM_URL = "$ORIGIN/direct/inbox/"
        private const val READY_TIMEOUT_MS = 20_000L
        private const val NAV_TIMEOUT_MS = 12_000L
        private const val READ_SETTLE_MS = 3_000L
        private const val REACT_SETTLE_MS = 1_000L
        private const val CALL_TIMEOUT_MS = 20_000L
        private const val UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        /**
         * Idempotent dispatcher prepended to every evaluateJavascript call so it is
         * present regardless of navigation state. Builds the fetch from a JSON spec
         * (args are never templated into source), runs it with credentials, and
         * returns {status, body} over the InstaBridge interface.
         */
        private const val DISPATCHER_JS = """
            window.__ig = window.__ig || {};
            window.__ig.call = function(callId, spec){
              (async function(){
                try {
                  var csrf = (document.cookie.match(/csrftoken=([^;]+)/) || [])[1] || '';
                  if (spec.kind === 'graphql') {
                    var h = document.documentElement.outerHTML;
                    var dtsg = (h.match(/"DTSGInitialData",\[\],\{"token":"([^"]+)"/) || [])[1] || '';
                    var lsd = (h.match(/"LSD",\[\],\{"token":"([^"]+)"/) || [])[1] || '';
                    var av = (h.match(/"CurrentUserInitialData",\[\],\{[^}]*"ACCOUNT_ID":"(\d+)"/) || [])[1] || '0';
                    var jazoest = '2' + Array.from(dtsg).reduce(function(s, c){ return s + c.charCodeAt(0); }, 0);
                    var gb = new URLSearchParams();
                    gb.set('av', av); gb.set('__d', 'www'); gb.set('__user', '0'); gb.set('__a', '1'); gb.set('__comet_req', '7');
                    gb.set('fb_dtsg', dtsg); gb.set('jazoest', jazoest); gb.set('lsd', lsd);
                    gb.set('doc_id', spec.docId); gb.set('variables', spec.variables);
                    gb.set('fb_api_caller_class', 'RelayModern'); gb.set('fb_api_req_friendly_name', spec.friendlyName);
                    gb.set('server_timestamps', 'true');
                    var gr = await fetch('/api/graphql/', {
                      method: 'POST', credentials: 'include',
                      headers: {
                        'Content-Type': 'application/x-www-form-urlencoded',
                        'X-FB-LSD': lsd, 'X-FB-Friendly-Name': spec.friendlyName,
                        'X-IG-App-ID': '936619743392459', 'X-ASBD-ID': '129477', 'X-CSRFToken': csrf
                      },
                      body: gb.toString()
                    });
                    var gt = await gr.text();
                    window.InstaBridge.onResult(callId, JSON.stringify({ status: gr.status, body: gt }));
                    return;
                  }
                  var url = spec.path;
                  if (spec.query) {
                    var qs = Object.keys(spec.query)
                      .filter(function(k){ return spec.query[k] != null; })
                      .map(function(k){ return encodeURIComponent(k) + '=' + encodeURIComponent(spec.query[k]); })
                      .join('&');
                    if (qs) url += (url.indexOf('?') >= 0 ? '&' : '?') + qs;
                  }
                  var headers = {
                    'X-IG-App-ID': '936619743392459',
                    'X-ASBD-ID': '129477',
                    'X-CSRFToken': csrf,
                    'X-Requested-With': 'XMLHttpRequest'
                  };
                  var init = { method: spec.method || 'GET', credentials: 'include', headers: headers };
                  if (spec.form) {
                    var body = new URLSearchParams();
                    Object.keys(spec.form).forEach(function(f){ if (spec.form[f] != null) body.set(f, spec.form[f]); });
                    headers['Content-Type'] = 'application/x-www-form-urlencoded';
                    init.body = body.toString();
                  }
                  var resp = await fetch(url, init);
                  var txt = await resp.text();
                  window.InstaBridge.onResult(callId, JSON.stringify({ status: resp.status, body: txt }));
                } catch (e) {
                  window.InstaBridge.onResult(callId, JSON.stringify({ status: -1, body: 'throw ' + (e && e.message || e) }));
                }
              })();
            };
        """
    }
}

class WebBridgeException(message: String) : Exception(message)
