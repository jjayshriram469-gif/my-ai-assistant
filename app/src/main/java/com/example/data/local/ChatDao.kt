package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    @Query("""
        SELECT s.sessionId, s.title, s.createdAt, s.updatedAt,
               COUNT(m.id) AS messageCount,
               (SELECT m2.text FROM chat_messages m2 WHERE m2.sessionId = s.sessionId ORDER BY m2.timestamp DESC LIMIT 1) AS lastMessageText,
               (SELECT m3.timestamp FROM chat_messages m3 WHERE m3.sessionId = s.sessionId ORDER BY m3.timestamp DESC LIMIT 1) AS lastMessageTime
        FROM chat_sessions s
        LEFT JOIN chat_messages m ON s.sessionId = m.sessionId
        GROUP BY s.sessionId, s.title, s.createdAt, s.updatedAt
        ORDER BY s.updatedAt DESC
    """)
    fun getAllSessionSummaries(): Flow<List<ChatSessionSummary>>

    @Query("""
        SELECT s.sessionId, s.title, s.createdAt, s.updatedAt,
               COUNT(m.id) AS messageCount,
               (SELECT m2.text FROM chat_messages m2 WHERE m2.sessionId = s.sessionId ORDER BY m2.timestamp DESC LIMIT 1) AS lastMessageText,
               (SELECT m3.timestamp FROM chat_messages m3 WHERE m3.sessionId = s.sessionId ORDER BY m3.timestamp DESC LIMIT 1) AS lastMessageTime
        FROM chat_sessions s
        LEFT JOIN chat_messages m ON s.sessionId = m.sessionId
        WHERE s.title LIKE '%' || :query || '%' 
           OR s.sessionId IN (SELECT DISTINCT sessionId FROM chat_messages WHERE text LIKE '%' || :query || '%')
        GROUP BY s.sessionId, s.title, s.createdAt, s.updatedAt
        ORDER BY s.updatedAt DESC
    """)
    fun searchSessionSummaries(query: String): Flow<List<ChatSessionSummary>>

    @Query("SELECT * FROM chat_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET title = :title, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updateSessionTitle(sessionId: String, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM chat_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesListForSession(sessionId: String): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE text LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchMessages(query: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Query("UPDATE chat_messages SET text = :text WHERE id = :messageId")
    suspend fun updateMessageText(messageId: String, text: String)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Query("DELETE FROM chat_sessions")
    suspend fun clearAllSessions()

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()

    @Query("SELECT COUNT(*) FROM chat_sessions")
    suspend fun getSessionCount(): Int

    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun getTotalMessageCount(): Int
}
