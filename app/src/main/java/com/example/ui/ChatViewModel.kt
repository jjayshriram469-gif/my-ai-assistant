package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.local.ChatSessionSummary
import com.example.data.repository.ChatRepository
import com.example.speech.TextToSpeechHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = ChatRepository(database.chatDao())
    val ttsHelper = TextToSpeechHelper(application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val sessionSummaries: StateFlow<List<ChatSessionSummary>> = repository.getAllSessionSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredSessionSummaries: StateFlow<List<ChatSessionSummary>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.getAllSessionSummaries()
            } else {
                repository.searchSessionSummaries(query.trim())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeSessionId = MutableStateFlow("")
    val activeSessionId: StateFlow<String> = _activeSessionId.asStateFlow()

    private val _activeSession = MutableStateFlow<ChatSessionEntity?>(null)
    val activeSession: StateFlow<ChatSessionEntity?> = _activeSession.asStateFlow()

    val currentMessages: StateFlow<List<ChatMessageEntity>> = _activeSessionId
        .flatMapLatest { id ->
            if (id.isBlank()) MutableStateFlow(emptyList())
            else repository.getMessagesForSession(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _storageStats = MutableStateFlow(Pair(0, 0))
    val storageStats: StateFlow<Pair<Int, Int>> = _storageStats.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    private var selectedImageBitmap: Bitmap? = null

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _activelyStreamingMessageId = MutableStateFlow<String?>(null)
    val activelyStreamingMessageId: StateFlow<String?> = _activelyStreamingMessageId.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var streamingJob: Job? = null

    init {
        // Automatically restore or select the most recent session from Room
        viewModelScope.launch {
            sessionSummaries.collect { list ->
                if (_activeSessionId.value.isBlank() && list.isNotEmpty()) {
                    val first = list.first()
                    _activeSessionId.value = first.sessionId
                    _activeSession.value = repository.getSession(first.sessionId)
                }
                updateStats()
            }
        }
    }

    fun isApiKeyConfigured(): Boolean = repository.geminiClient.isApiKeyConfigured()

    fun updateStats() {
        viewModelScope.launch {
            _storageStats.value = repository.getStorageStats()
        }
    }

    fun updateInputText(text: String) {
        _inputText.value = text
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSearchQuery() {
        _searchQuery.value = ""
    }

    fun setSelectedImage(uri: Uri?, bitmap: Bitmap?) {
        _selectedImageUri.value = uri
        selectedImageBitmap = bitmap
    }

    fun clearSelectedImage() {
        _selectedImageUri.value = null
        selectedImageBitmap = null
    }

    fun setListening(listening: Boolean) {
        _isListening.value = listening
    }

    fun onSpeechResult(text: String) {
        if (text.isNotBlank()) {
            val current = _inputText.value.trim()
            _inputText.value = if (current.isEmpty()) text else "$current $text"
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun selectSession(sessionId: String) {
        if (_activeSessionId.value != sessionId) {
            ttsHelper.stop()
            _activeSessionId.value = sessionId
            viewModelScope.launch {
                _activeSession.value = repository.getSession(sessionId)
            }
            clearError()
        }
    }

    fun createNewChat() {
        viewModelScope.launch {
            ttsHelper.stop()
            clearError()

            // If the current active chat already has 0 messages, reuse it instead of creating duplicates
            val currentMsgList = currentMessages.value
            val currentSession = _activeSession.value
            if (currentMsgList.isEmpty() && currentSession != null && currentSession.title == "New Chat") {
                return@launch
            }

            val newSession = repository.createNewSession()
            _activeSessionId.value = newSession.sessionId
            _activeSession.value = newSession
            updateStats()
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            if (_activeSessionId.value == sessionId) {
                ttsHelper.stop()
            }
            repository.deleteSession(sessionId)
            updateStats()

            val currentList = sessionSummaries.value.filter { it.sessionId != sessionId }
            if (_activeSessionId.value == sessionId) {
                if (currentList.isNotEmpty()) {
                    val next = currentList.first()
                    _activeSessionId.value = next.sessionId
                    _activeSession.value = repository.getSession(next.sessionId)
                } else {
                    val fresh = repository.createNewSession()
                    _activeSessionId.value = fresh.sessionId
                    _activeSession.value = fresh
                }
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
            updateStats()
        }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameSession(sessionId, newTitle)
            if (_activeSessionId.value == sessionId) {
                _activeSession.value = repository.getSession(sessionId)
            }
        }
    }

    fun clearCurrentSession() {
        viewModelScope.launch {
            val sId = _activeSessionId.value
            if (sId.isNotBlank()) {
                ttsHelper.stop()
                repository.clearSessionMessages(sId)
                updateStats()
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            ttsHelper.stop()
            repository.clearAll()
            val fresh = repository.createNewSession()
            _activeSessionId.value = fresh.sessionId
            _activeSession.value = fresh
            updateStats()
        }
    }

    fun exportCurrentTranscript(onResult: (String) -> Unit) {
        val sId = _activeSessionId.value
        if (sId.isBlank()) {
            onResult("")
            return
        }
        viewModelScope.launch {
            val transcript = repository.exportConversation(sId)
            onResult(transcript)
        }
    }

    fun sendMessage(customPrompt: String? = null) {
        val promptToSend = (customPrompt ?: _inputText.value).trim()
        val imageUriToSend = _selectedImageUri.value?.toString()
        val imageBitmapToSend = selectedImageBitmap

        if (promptToSend.isBlank() && imageBitmapToSend == null) return

        // Clear input field and attachment
        if (customPrompt == null) {
            _inputText.value = ""
        }
        clearSelectedImage()
        clearError()

        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            var currentSessionId = _activeSessionId.value
            if (currentSessionId.isBlank()) {
                val newSession = repository.createNewSession()
                currentSessionId = newSession.sessionId
                _activeSessionId.value = currentSessionId
                _activeSession.value = newSession
            }

            _isGenerating.value = true

            val result = repository.sendMessage(
                sessionId = currentSessionId,
                userText = promptToSend,
                imageUri = imageUriToSend,
                imageBitmap = imageBitmapToSend
            ) { /* chunks */ }

            _isGenerating.value = false
            _activelyStreamingMessageId.value = null

            // Refresh active session entity (e.g. title may have changed)
            _activeSession.value = repository.getSession(currentSessionId)
            updateStats()

            if (result.isFailure) {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to generate reply"
            }
        }
    }

    fun stopGenerating() {
        streamingJob?.cancel()
        _isGenerating.value = false
        _activelyStreamingMessageId.value = null
        updateStats()
    }

    fun regenerateLastMessage() {
        val messages = currentMessages.value
        val lastAssistantMessage = messages.lastOrNull { it.sender == "assistant" } ?: return
        val currentSessionId = _activeSessionId.value
        if (currentSessionId.isBlank()) return

        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            _isGenerating.value = true
            _activelyStreamingMessageId.value = lastAssistantMessage.id

            val result = repository.regenerateAssistantMessage(
                sessionId = currentSessionId,
                lastAssistantMessageId = lastAssistantMessage.id
            ) { /* chunks */ }

            _isGenerating.value = false
            _activelyStreamingMessageId.value = null
            updateStats()

            if (result.isFailure) {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to regenerate reply"
            }
        }
    }

    fun toggleSpeech(messageId: String, text: String) {
        ttsHelper.speak(messageId, text)
    }

    override fun onCleared() {
        super.onCleared()
        streamingJob?.cancel()
        ttsHelper.shutdown()
    }
}
