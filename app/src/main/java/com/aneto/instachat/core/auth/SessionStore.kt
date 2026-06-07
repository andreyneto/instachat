package com.aneto.instachat.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences = buildEncryptedPrefs(context)

    private val _events = MutableSharedFlow<SessionEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    fun saveSession(cookieHeader: String, userId: String, csrfToken: String, appId: String) {
        prefs.edit()
            .putString(KEY_COOKIE, cookieHeader)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_CSRF, csrfToken)
            .putString(KEY_APP_ID, appId.ifBlank { DEFAULT_APP_ID })
            .apply()
    }

    fun saveGraphTokens(fbDtsg: String, lsd: String, accountFbid: String) {
        val editor = prefs.edit()
        if (fbDtsg.isNotBlank()) editor.putString(KEY_FB_DTSG, fbDtsg)
        if (lsd.isNotBlank()) editor.putString(KEY_LSD, lsd)
        if (accountFbid.isNotBlank()) editor.putString(KEY_ACCOUNT_FBID, accountFbid)
        editor.apply()
    }

    fun cookieHeader(): String? = prefs.getString(KEY_COOKIE, null)
    fun userId(): String? = prefs.getString(KEY_USER_ID, null)
    fun csrfToken(): String? = prefs.getString(KEY_CSRF, null)
    fun appId(): String = prefs.getString(KEY_APP_ID, DEFAULT_APP_ID) ?: DEFAULT_APP_ID
    fun fbDtsg(): String? = prefs.getString(KEY_FB_DTSG, null)
    fun lsd(): String? = prefs.getString(KEY_LSD, null)
    fun accountFbid(): String? = prefs.getString(KEY_ACCOUNT_FBID, null)

    fun deviceUuid(): String {
        prefs.getString(KEY_DEVICE_UUID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_UUID, generated).apply()
        return generated
    }

    fun androidDeviceId(): String {
        val uuid = deviceUuid().replace("-", "")
        return "android-" + uuid.take(16)
    }

    fun hasValidSession(): Boolean {
        val cookie = cookieHeader() ?: return false
        return cookie.contains("sessionid=") && !userId().isNullOrBlank()
    }

    fun invalidate() {
        prefs.edit().clear().apply()
        _events.tryEmit(SessionEvent.Invalidated)
    }

    private fun buildEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "instachat_session",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    companion object {
        private const val KEY_COOKIE = "cookie_header"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_CSRF = "csrf_token"
        private const val KEY_APP_ID = "app_id"
        private const val KEY_DEVICE_UUID = "device_uuid"
        private const val KEY_FB_DTSG = "fb_dtsg"
        private const val KEY_LSD = "lsd"
        private const val KEY_ACCOUNT_FBID = "account_fbid"
        const val DEFAULT_APP_ID = "936619743392459"
    }
}

sealed interface SessionEvent {
    data object Invalidated : SessionEvent
}
