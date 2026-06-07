package com.aneto.instachat.core.auth

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(private val sessionStore: SessionStore) {
    fun isLoggedIn(): Boolean = sessionStore.hasValidSession()

    fun logout() = sessionStore.invalidate()

    fun onSessionExpired() = sessionStore.invalidate()
}
