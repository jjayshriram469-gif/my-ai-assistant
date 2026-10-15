package com.example.data.repository

import android.graphics.Bitmap
import com.example.data.local.ChatDao
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.local.ChatSessionSummary
import com.example.data.remote.ChatMessagePayload
import com.example.data.remote.GeminiClient
import com.example.util.DateFormatter
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ChatRepository(
    private val chatDao: ChatDao,
    val geminiClient: GeminiClient = GeminiClient()
) {
    fun getAllSessions(): Flow<List<ChatSessionEntity>> = chatDao.getAllSessions()

    fun getAllSessionSummaries(): Flow<List<ChatSessionSummary>> =
        chatDao.getAllSessionSummaries()

    fun searchSessionSummaries(query: String): Flow<List<ChatSessionSummary>> =
        chatDao.searchSessionSummaries(query)

    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>> =
        chatDao.getMessagesForSession(sessionId)

    suspend fun createNewSession(title: String = "New Chat"): ChatSessionEntity {
        val session = ChatSessionEntity(
            sessionId = UUID.randomUUID().toString(),
            title = title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        chatDao.insertSession(session)
        return session
    }

    suspend fun getSession(sessionId: String): ChatSessionEntity? =
        chatDao.getSessionById(sessionId)

    suspend fun renameSession(sessionId: String, newTitle: String) {
        val cleanTitle = newTitle.trim().ifBlank { "Untitled Chat" }
        chatDao.updateSessionTitle(sessionId, cleanTitle, System.currentTimeMillis())
    }

    suspend fun deleteSession(sessionId: String) {
        chatDao.deleteMessagesForSession(sessionId)
        chatDao.deleteSession(sessionId)
    }

    suspend fun deleteMessage(messageId: String) {
        chatDao.deleteMessageById(messageId)
    }

    suspend fun clearSessionMessages(sessionId: String) {
        chatDao.deleteMessagesForSession(sessionId)
    }

    suspend fun clearAll() {
        chatDao.clearAllMessages()
        chatDao.clearAllSessions()
    }

    suspend fun getStorageStats(): Pair<Int, Int> {
        val sessions = chatDao.getSessionCount()
        val messages = chatDao.getTotalMessageCount()
        return Pair(sessions, messages)
    }

    suspend fun exportConversation(sessionId: String): String {
        val session = chatDao.getSessionById(sessionId) ?: return ""
        val messages = chatDao.getMessagesListForSession(sessionId)

        val sb = StringBuilder()
        sb.append("=== Conversation Transcript ===\n")
        sb.append("Title: ${session.title}\n")
        sb.append("Started: ${DateFormatter.formatDateHeader(session.createdAt)} ${DateFormatter.formatMessageTime(session.createdAt)}\n")
        sb.append("Total Messages: ${messages.size}\n")
        sb.append("================================\n\n")

        for (msg in messages) {
            val senderLabel = when (msg.sender) {
                "user" -> "User"
                "assistant" -> "Gemini Assistant"
                else -> "System"
            }
            val time = DateFormatter.formatMessageTime(msg.timestamp)
            sb.append("[$time] $senderLabel:\n")
            if (!msg.imageUri.isNullOrBlank()) {
                sb.append("[Photo Attached: ${msg.imageUri}]\n")
            }
            sb.append("${msg.text.trim()}\n\n")
        }

        sb.append("Exported from Remix yadav ai (Room Database)")
        return sb.toString()
    }

    suspend fun sendMessage(
        sessionId: String,
        userText: String,
        imageUri: String? = null,
        imageBitmap: Bitmap? = null,
        onChunk: (String) -> Unit
    ): Result<String> {
        val userMsgId = UUID.randomUUID().toString()
        val userMessage = ChatMessageEntity(
            id = userMsgId,
            sessionId = sessionId,
            sender = "user",
            text = userText,
            imageUri = imageUri,
            timestamp = System.currentTimeMillis()
        )
        chatDao.insertMessage(userMessage)

        // If this session is named "New Chat", auto-update title based on the first prompt
        val currentSession = chatDao.getSessionById(sessionId)
        if (currentSession != null && (currentSession.title == "New Chat" || currentSession.title.isBlank())) {
            val autoTitle = if (userText.length > 32) {
                userText.take(30).trim() + "…"
            } else if (userText.isNotBlank()) {
                userText.trim()
            } else {
                "Photo Query"
            }
            chatDao.updateSessionTitle(sessionId, autoTitle, System.currentTimeMillis())
        } else {
            chatDao.updateSessionTitle(sessionId, currentSession?.title ?: "Chat", System.currentTimeMillis())
        }

        // Fetch recent history from DB for conversational context
        val dbMessages = chatDao.getMessagesListForSession(sessionId)
        val historyPayloads = dbMessages
            .filter { it.id != userMsgId && (it.sender == "user" || it.sender == "assistant") && it.text.isNotBlank() }
            .takeLast(10)
            .map {
                ChatMessagePayload(
                    role = if (it.sender == "assistant") "model" else "user",
                    text = it.text
                )
            }

        // Create Assistant placeholder message in Room
        val assistantMsgId = UUID.randomUUID().toString()
        val assistantPlaceholder = ChatMessageEntity(
            id = assistantMsgId,
            sessionId = sessionId,
            sender = "assistant",
            text = "",
            timestamp = System.currentTimeMillis()
        )
        chatDao.insertMessage(assistantPlaceholder)

        val fullResponseBuffer = StringBuilder()
        var lastDbUpdateTime = System.currentTimeMillis()

        val result = geminiClient.streamGenerateContent(
            history = historyPayloads,
            currentMessage = userText.ifBlank { "What is shown in this image?" },
            currentImageBitmap = imageBitmap
        ) { chunk ->
            fullResponseBuffer.append(chunk)
            onChunk(chunk)

            // Periodically persist streaming chunks to Room (every 300ms)
            // so partial responses are never lost if app is closed or network fails
            val now = System.currentTimeMillis()
            if (now - lastDbUpdateTime > 300) {
                lastDbUpdateTime = now
                val currentAccumulated = fullResponseBuffer.toString()
                // Update room on background thread
                kotlinx.coroutines.runBlocking {
                    chatDao.updateMessageText(assistantMsgId, currentAccumulated)
                }
            }
        }

        if (result.isSuccess) {
            val finalText = result.getOrNull() ?: fullResponseBuffer.toString()
            chatDao.updateMessageText(assistantMsgId, finalText)
            chatDao.updateSessionTitle(sessionId, currentSession?.title ?: "Chat", System.currentTimeMillis())
            return Result.success(finalText)
        } else {
            val errorMsg = result.exceptionOrNull()?.message ?: "An unexpected error occurred."
            val errorText = "⚠️ $errorMsg"
            chatDao.updateMessageText(assistantMsgId, errorText)
            return Result.failure(Exception(errorMsg))
        }
    }

    suspend fun regenerateAssistantMessage(
        sessionId: String,
        lastAssistantMessageId: String,
        onChunk: (String) -> Unit
    ): Result<String> {
        val dbMessages = chatDao.getMessagesListForSession(sessionId)
        val lastUserMessage = dbMessages.lastOrNull { it.sender == "user" }
            ?: return Result.failure(Exception("No user prompt found to regenerate."))

        // Clear the last assistant message
        chatDao.updateMessageText(lastAssistantMessageId, "")

        val historyPayloads = dbMessages
            .filter { it.id != lastUserMessage.id && it.id != lastAssistantMessageId && (it.sender == "user" || it.sender == "assistant") && it.text.isNotBlank() }
            .takeLast(10)
            .map {
                ChatMessagePayload(
                    role = if (it.sender == "assistant") "model" else "user",
                    text = it.text
                )
            }

        val fullResponseBuffer = StringBuilder()
        var lastDbUpdateTime = System.currentTimeMillis()

        val result = geminiClient.streamGenerateContent(
            history = historyPayloads,
            currentMessage = lastUserMessage.text,
            currentImageBitmap = null
        ) { chunk ->
            fullResponseBuffer.append(chunk)
            onChunk(chunk)

            val now = System.currentTimeMillis()
            if (now - lastDbUpdateTime > 300) {
                lastDbUpdateTime = now
                val currentAccumulated = fullResponseBuffer.toString()
                kotlinx.coroutines.runBlocking {
                    chatDao.updateMessageText(lastAssistantMessageId, currentAccumulated)
                }
            }
        }

        if (result.isSuccess) {
            val finalText = result.getOrNull() ?: fullResponseBuffer.toString()
            chatDao.updateMessageText(lastAssistantMessageId, finalText)
            return Result.success(finalText)
        } else {
            val errorMsg = result.exceptionOrNull()?.message ?: "An unexpected error occurred."
            val errorText = "⚠️ $errorMsg"
            chatDao.updateMessageText(lastAssistantMessageId, errorText)
            return Result.failure(Exception(errorMsg))
        }
    }
}
