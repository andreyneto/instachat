package com.aneto.instachat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aneto.instachat.core.auth.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RootState(val hasSession: Boolean = false, val ready: Boolean = false)

@HiltViewModel
class RootViewModel @Inject constructor(private val sessionStore: SessionStore) : ViewModel() {

    private val _state = MutableStateFlow(
        RootState(hasSession = sessionStore.hasValidSession(), ready = true),
    )
    val state: StateFlow<RootState> = _state.asStateFlow()

    fun markAuthenticated() {
        viewModelScope.launch {
            _state.value = RootState(hasSession = true, ready = true)
        }
    }
}
