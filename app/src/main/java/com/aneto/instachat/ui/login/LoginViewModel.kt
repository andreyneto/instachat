package com.aneto.instachat.ui.login

import androidx.lifecycle.ViewModel
import com.aneto.instachat.core.auth.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(private val sessionStore: SessionStore) : ViewModel() {

    private var capturedAppId: String = SessionStore.DEFAULT_APP_ID
    private var capturedCsrf: String = ""

    fun captureHeaders(appId: String, csrf: String) {
        if (appId.isNotBlank()) capturedAppId = appId
        if (csrf.isNotBlank()) capturedCsrf = csrf
    }

    fun captureGraphTokens(fbDtsg: String, lsd: String, accountFbid: String) {
        sessionStore.saveGraphTokens(fbDtsg, lsd, accountFbid)
        android.util.Log.i(
            "LoginViewModel",
            "captured dtsg=${fbDtsg.take(12)}... lsd=${lsd.take(8)}... accountFbid=$accountFbid",
        )
    }

    fun onCookiesReady(cookieHeader: String, onSuccess: () -> Unit) {
        val userId = cookieHeader.parseCookie("ds_user_id") ?: return
        val csrf = capturedCsrf.ifBlank { cookieHeader.parseCookie("csrftoken").orEmpty() }
        sessionStore.saveSession(
            cookieHeader = cookieHeader,
            userId = userId,
            csrfToken = csrf,
            appId = capturedAppId,
        )
        onSuccess()
    }
}

internal fun String.parseCookie(name: String): String? {
    val prefix = "$name="
    return split(";").map { it.trim() }.firstOrNull { it.startsWith(prefix) }
        ?.removePrefix(prefix)
        ?.takeIf { it.isNotBlank() }
}
