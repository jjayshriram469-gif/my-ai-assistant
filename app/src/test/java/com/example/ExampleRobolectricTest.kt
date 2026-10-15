package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Remix yadav ai", appName)
    }

    @Test
    fun `test chat entities structure`() {
        val sessionId = UUID.randomUUID().toString()
        val session = ChatSessionEntity(
            sessionId = sessionId,
            title = "Testing Session"
        )
        val message = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            sender = "user",
            text = "Hello Gemini!"
        )

        assertNotNull(session.sessionId)
        assertEquals("Testing Session", session.title)
        assertEquals("user", message.sender)
        assertEquals("Hello Gemini!", message.text)
    }

    @Test
    fun `test room database persists sessions and messages`() = runBlocking {
        val dao = database.chatDao()

        // 1. Create a session
        val sessionId = UUID.randomUUID().toString()
        val session = ChatSessionEntity(
            sessionId = sessionId,
            title = "Android Architecture",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        dao.insertSession(session)

        val retrievedSession = dao.getSessionById(sessionId)
        assertNotNull(retrievedSession)
        assertEquals("Android Architecture", retrievedSession?.title)

        // 2. Insert messages
        val msg1 = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            sender = "user",
            text = "How does Room work?",
            timestamp = 1001L
        )
        val msg2 = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            sender = "assistant",
            text = "Room provides an abstraction layer over SQLite.",
            timestamp = 1002L
        )
        dao.insertMessage(msg1)
        dao.insertMessage(msg2)

        // 3. Verify messages are persisted and ordered
        val messages = dao.getMessagesListForSession(sessionId)
        assertEquals(2, messages.size)
        assertEquals("How does Room work?", messages[0].text)
        assertEquals("Room provides an abstraction layer over SQLite.", messages[1].text)

        // 4. Test summary query with message count & last message preview
        val summaries = dao.getAllSessionSummaries().first()
        assertEquals(1, summaries.size)
        val summary = summaries[0]
        assertEquals(sessionId, summary.sessionId)
        assertEquals(2, summary.messageCount)
        assertEquals("Room provides an abstraction layer over SQLite.", summary.lastMessageText)

        // 5. Test search across sessions and message content
        val searchByTitle = dao.searchSessionSummaries("Architecture").first()
        assertEquals(1, searchByTitle.size)

        val searchByContent = dao.searchSessionSummaries("abstraction").first()
        assertEquals(1, searchByContent.size)

        val searchNoMatch = dao.searchSessionSummaries("NonExistentTermXYZ").first()
        assertTrue(searchNoMatch.isEmpty())

        // 6. Test session rename
        dao.updateSessionTitle(sessionId, "Room Persistence Guide", 1050L)
        val updated = dao.getSessionById(sessionId)
        assertEquals("Room Persistence Guide", updated?.title)

        // 7. Test single message deletion
        dao.deleteMessageById(msg1.id)
        val messagesAfterSingleDelete = dao.getMessagesListForSession(sessionId)
        assertEquals(1, messagesAfterSingleDelete.size)
        assertEquals(msg2.id, messagesAfterSingleDelete[0].id)

        // 8. Test cascade deletion of session
        dao.deleteSession(sessionId)
        val deletedSession = dao.getSessionById(sessionId)
        assertNull(deletedSession)
        val messagesAfterSessionDelete = dao.getMessagesListForSession(sessionId)
        assertTrue(messagesAfterSessionDelete.isEmpty())
    }
}
