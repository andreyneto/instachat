package com.aneto.instachat.data.direct

import com.aneto.instachat.core.auth.SessionStore
import com.aneto.instachat.core.web.WebBridge
import com.aneto.instachat.data.direct.dto.DirectInboxResponse
import com.aneto.instachat.data.direct.dto.ThreadResponse
import com.aneto.instachat.domain.model.Message
import com.aneto.instachat.domain.model.Thread
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import javax.inject.Inject
import javax.inject.Singleton

data class ThreadPage(val thread: Thread, val messages: List<Message>, val oldestCursor: String?, val hasOlder: Boolean)

@Singleton
class DirectRepository @Inject constructor(
    private val bridge: WebBridge,
    private val sessionStore: SessionStore,
    private val json: Json,
) {

    private val _seenEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val seenEvents: SharedFlow<String> = _seenEvents.asSharedFlow()

    private val fbidByThreadId = mutableMapOf<String, String>()

    suspend fun loadInbox(cursor: String? = null): List<Thread> {
        val response = json.decodeFromString<DirectInboxResponse>(bridge.inbox(cursor))
        val viewerId = sessionStore.userId()
        val dtos = response.inbox?.threads.orEmpty()
        dtos.forEach { dto ->
            val fbid = dto.threadFbid?.takeIf { it.isNotBlank() }
                ?: dto.threadV2Id?.takeIf { it.isNotBlank() }
            if (fbid != null) fbidByThreadId[dto.threadId] = fbid
        }
        return dtos.map { MessageMapper.toThread(it, viewerId) }
            .sortedByDescending { it.lastActivityAt }
    }

    suspend fun loadThread(threadId: String, cursor: String? = null): ThreadPage {
        val root = json.parseToJsonElement(bridge.thread(threadId, cursor))
        val response = json.decodeFromJsonElement<ThreadResponse>(root)
        val dto = response.thread ?: error("empty thread")
        val viewerId = sessionStore.userId()
        val fbid = dto.threadFbid?.takeIf { it.isNotBlank() }
            ?: dto.threadV2Id?.takeIf { it.isNotBlank() }
        if (fbid != null) fbidByThreadId[dto.threadId] = fbid
        val thread = MessageMapper.toThread(dto, viewerId)
        val rawItemsById = rawItemsById(root)
        val messages = dto.items.map { MessageMapper.toMessage(it, rawItemsById[it.itemId]) }
        return ThreadPage(
            thread = thread,
            messages = messages,
            oldestCursor = dto.oldestCursor,
            hasOlder = dto.hasOlder,
        )
    }

    suspend fun sendText(threadId: String, text: String) {
        val fbid = fbidByThreadId[threadId]
            ?: error("no fbid for thread $threadId; cannot send via DOM")
        val ok = bridge.sendTextViaDom(fbid, text)
        if (!ok) error("send via DOM did not complete")
    }

    /**
     * React to a message by driving IG's own web UI inside the backing WebView
     * (no REST/GraphQL route exists for this on the web origin). [anchorText] is the
     * message's text, used to locate the right bubble; null for media messages.
     */
    suspend fun react(threadId: String, itemId: String, emoji: String, anchorText: String?) {
        val fbid = fbidByThreadId[threadId]
            ?: error("no fbid for thread $threadId; cannot react via DOM")
        val ok = bridge.reactViaDom(fbid, emoji, anchorText)
        if (!ok) error("reaction via DOM did not complete")
    }

    /**
     * Mark a thread read. Tries the cheapest strategy first and verifies after each
     * by re-reading the thread's actual read state (reusing [MessageMapper.toThread]'s
     * unread computation). Stops at the first strategy that genuinely sticks.
     */
    suspend fun markSeen(threadId: String, itemId: String) {
        _seenEvents.tryEmit(threadId)

        // Strategy A — direct "seen" REST endpoint.
        runCatching { bridge.markSeenRest(threadId, itemId) }
        if (verifyRead(threadId)) {
            android.util.Log.i(TAG, "markSeen $threadId ok via rest-seen")
            return
        }

        val fbid = fbidByThreadId[threadId]
        if (fbid.isNullOrBlank()) {
            android.util.Log.w(TAG, "markSeen $threadId unconfirmed, no fbid for fallback")
            return
        }

        // Strategy B — navigate the visible WebView to the thread; IG's own client
        // emits the native read receipt on mount.
        runCatching { bridge.navigateAndSettle(fbid) }
        if (verifyRead(threadId)) {
            android.util.Log.i(TAG, "markSeen $threadId ok via navigation")
            return
        }

        // Strategy C — legacy mark-unread-off GraphQL mutation (best effort).
        runCatching {
            bridge.graphql(
                docId = MARK_UNREAD_OFF_DOC_ID,
                friendlyName = MARK_UNREAD_OFF_NAME,
                variablesJson = """{"thread_fbid":"$fbid","marked":false}""",
            )
        }
        if (verifyRead(threadId)) {
            android.util.Log.i(TAG, "markSeen $threadId ok via graphql")
            return
        }

        android.util.Log.w(TAG, "markSeen $threadId could not be confirmed")
    }

    /** Raw item JSON keyed by item_id, so the mapper can expose payloads the DTOs don't model. */
    private fun rawItemsById(root: JsonElement): Map<String, JsonObject> =
        ((root as? JsonObject)?.get("thread") as? JsonObject)
            ?.let { it["items"] as? JsonArray }
            ?.filterIsInstance<JsonObject>()
            ?.mapNotNull { item ->
                (item["item_id"] as? JsonPrimitive)?.contentOrNull?.let { id -> id to item }
            }
            ?.toMap()
            .orEmpty()

    private suspend fun verifyRead(threadId: String): Boolean = runCatching {
        val response = json.decodeFromString<ThreadResponse>(bridge.thread(threadId, null))
        val dto = response.thread ?: return@runCatching false
        !MessageMapper.toThread(dto, sessionStore.userId()).unread
    }.getOrDefault(false)

    companion object {
        private const val TAG = "DirectRepository"
        private const val MARK_UNREAD_OFF_DOC_ID = "25875885148710881"
        private const val MARK_UNREAD_OFF_NAME = "IGDThreadListActionsMarkUnreadOptionOffMsysMutation"
    }
}
