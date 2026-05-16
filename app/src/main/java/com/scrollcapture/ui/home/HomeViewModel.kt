package com.scrollcapture.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scrollcapture.ScrollCaptureApp
import com.scrollcapture.data.CaptureSession
import com.scrollcapture.data.SessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: SessionRepository
) : ViewModel() {

    val sessions: StateFlow<List<CaptureSession>> = repository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteSession(session: CaptureSession) {
        viewModelScope.launch {
            repository.deleteSession(session)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(
                    ScrollCaptureApp.instance.container.sessionRepository
                ) as T
            }
        }
    }
}
