package com.scrollcapture.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scrollcapture.ScrollCaptureApp
import com.scrollcapture.data.CaptureSession
import com.scrollcapture.data.SessionRepository
import com.scrollcapture.prompt.PromptBuilder
import com.scrollcapture.prompt.PromptTemplate
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ReviewViewModel(
    private val sessionId: Long,
    private val repository: SessionRepository
) : ViewModel() {

    private val _session = MutableStateFlow<CaptureSession?>(null)
    val session: StateFlow<CaptureSession?> = _session.asStateFlow()

    private val _editedText = MutableStateFlow("")
    val editedText: StateFlow<String> = _editedText.asStateFlow()

    private val _selectedTemplate = MutableStateFlow<PromptTemplate?>(null)
    val selectedTemplate: StateFlow<PromptTemplate?> = _selectedTemplate.asStateFlow()

    private val _customInstruction = MutableStateFlow("")
    val customInstruction: StateFlow<String> = _customInstruction.asStateFlow()

    val generatedPrompt: StateFlow<String> = combine(
        _editedText, _selectedTemplate, _customInstruction
    ) { text, template, custom ->
        if (template == null) ""
        else PromptBuilder.buildPrompt(template, custom.ifEmpty { null }, text)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    init {
        viewModelScope.launch {
            repository.getSessionById(sessionId).collectLatest { s ->
                if (s != null) {
                    _session.value = s
                    if (_editedText.value.isEmpty()) {
                        _editedText.value = s.assembledText
                    }
                }
            }
        }
    }

    fun updateText(text: String) { _editedText.value = text }

    fun selectTemplate(template: PromptTemplate) {
        _selectedTemplate.value = if (_selectedTemplate.value == template) null else template
    }

    fun updateCustomInstruction(instruction: String) { _customInstruction.value = instruction }

    fun saveEdits() {
        viewModelScope.launch {
            val current = _session.value ?: return@launch
            repository.updateSession(
                current.copy(
                    assembledText = _editedText.value,
                    characterCount = _editedText.value.length
                )
            )
        }
    }

    fun deleteSession(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteSessionById(sessionId)
            onDeleted()
        }
    }

    companion object {
        fun factory(sessionId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ReviewViewModel(
                        sessionId,
                        ScrollCaptureApp.instance.container.sessionRepository
                    ) as T
                }
            }
    }
}
