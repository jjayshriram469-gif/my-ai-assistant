package com.example.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.AssistantMessageItem
import com.example.ui.components.ChatNavigationDrawerContent
import com.example.ui.components.DateHeaderSeparator
import com.example.ui.components.ImageAttachmentPreview
import com.example.ui.components.UserMessageItem
import com.example.ui.components.WelcomeHeroScreen
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentViolet
import com.example.ui.theme.AccentVioletDark
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.DarkSurfaceHover
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.DateFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onLaunchSpeechInput: () -> Unit,
    onLaunchPhotoPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val sessionSummaries by viewModel.filteredSessionSummaries.collectAsState()
    val activeSessionId by viewModel.activeSessionId.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val currentMessages by viewModel.currentMessages.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val storageStats by viewModel.storageStats.collectAsState()

    val inputText by viewModel.inputText.collectAsState()
    val selectedImageUri by viewModel.selectedImageUri.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val isSpeaking by viewModel.ttsHelper.isSpeaking.collectAsState()
    val speakingMessageId by viewModel.ttsHelper.speakingMessageId.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showTopMenu by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var sessionToDeleteId by remember { mutableStateOf<String?>(null) }

    // Rename dialog state
    var sessionToRename by remember { mutableStateOf<Pair<String, String>?>(null) }
    var renameInputText by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    // Auto-scroll when messages change or are streaming
    LaunchedEffect(currentMessages.size, currentMessages.lastOrNull()?.text?.length) {
        if (currentMessages.isNotEmpty()) {
            listState.animateScrollToItem(currentMessages.size - 1)
        }
    }

    val displayTitle = activeSession?.title?.ifBlank { stringResource(R.string.app_name) }
        ?: stringResource(R.string.app_name)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = DarkSurface,
                drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            ) {
                ChatNavigationDrawerContent(
                    sessions = sessionSummaries,
                    activeSessionId = activeSessionId,
                    searchQuery = searchQuery,
                    storageStats = storageStats,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onSelectSession = { id ->
                        viewModel.selectSession(id)
                        coroutineScope.launch { drawerState.close() }
                    },
                    onNewChat = {
                        viewModel.createNewChat()
                        coroutineScope.launch { drawerState.close() }
                    },
                    onDeleteSession = { id ->
                        sessionToDeleteId = id
                    },
                    onRenameSession = { id, currentTitle ->
                        sessionToRename = Pair(id, currentTitle)
                        renameInputText = currentTitle
                    },
                    onShareSession = { id ->
                        viewModel.exportCurrentTranscript { transcript ->
                            if (transcript.isNotBlank()) {
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, transcript)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Chat Transcript"))
                            }
                        }
                    },
                    onOpenSettings = {
                        coroutineScope.launch { drawerState.close() }
                        showSettingsDialog = true
                    }
                )
            }
        }
    ) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBackground),
            containerColor = DarkBackground,
            topBar = {
                TopAppBar(
                    title = {
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    activeSession?.let { s ->
                                        sessionToRename = Pair(s.sessionId, s.title)
                                        renameInputText = s.title
                                    }
                                }
                                .padding(vertical = 2.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = displayTitle,
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Persisted in Room • Gemini 3.5",
                                color = AccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                coroutineScope.launch { drawerState.open() }
                            },
                            modifier = Modifier.testTag("drawer_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Chat History",
                                tint = TextPrimary
                            )
                        }
                    },
                    actions = {
                        // Review History Button
                        IconButton(
                            onClick = { coroutineScope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("top_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = stringResource(R.string.chat_history),
                                tint = TextSecondary
                            )
                        }

                        // Quick New Chat Button
                        IconButton(
                            onClick = { viewModel.createNewChat() },
                            modifier = Modifier.testTag("top_new_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.new_chat),
                                tint = AccentViolet
                            )
                        }

                        // Stop Speaking icon if audio is currently playing
                        if (isSpeaking) {
                            IconButton(
                                onClick = { viewModel.ttsHelper.stop() },
                                modifier = Modifier.testTag("top_stop_speaking_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = "Stop reading aloud",
                                    tint = AccentCyan
                                )
                            }
                        }

                        // More Menu (Rename, Share Transcript, Clear Chat, Settings)
                        Box {
                            IconButton(
                                onClick = { showTopMenu = true },
                                modifier = Modifier.testTag("top_more_options_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Options",
                                    tint = TextSecondary
                                )
                            }

                            DropdownMenu(
                                expanded = showTopMenu,
                                onDismissRequest = { showTopMenu = false },
                                modifier = Modifier.background(DarkSurface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename Chat", color = TextPrimary) },
                                    onClick = {
                                        showTopMenu = false
                                        activeSession?.let { s ->
                                            sessionToRename = Pair(s.sessionId, s.title)
                                            renameInputText = s.title
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            tint = AccentCyan
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share Transcript", color = TextPrimary) },
                                    onClick = {
                                        showTopMenu = false
                                        viewModel.exportCurrentTranscript { transcript ->
                                            if (transcript.isNotBlank()) {
                                                val shareIntent = Intent().apply {
                                                    action = Intent.ACTION_SEND
                                                    putExtra(Intent.EXTRA_TEXT, transcript)
                                                    type = "text/plain"
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share Chat Transcript"))
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = null,
                                            tint = TextSecondary
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear Messages", color = TextPrimary) },
                                    onClick = {
                                        showTopMenu = false
                                        viewModel.clearCurrentSession()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = null,
                                            tint = TextSecondary
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Database Storage", color = TextPrimary) },
                                    onClick = {
                                        showTopMenu = false
                                        showSettingsDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Storage,
                                            contentDescription = null,
                                            tint = AccentCyan
                                        )
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DarkBackground,
                        titleContentColor = TextPrimary
                    ),
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                )
            },
            bottomBar = {
                ChatInputComposer(
                    text = inputText,
                    onTextChanged = { viewModel.updateInputText(it) },
                    selectedImageUri = selectedImageUri,
                    onRemoveImage = { viewModel.clearSelectedImage() },
                    isGenerating = isGenerating,
                    isListening = isListening,
                    onSendMessage = {
                        focusManager.clearFocus()
                        viewModel.sendMessage()
                    },
                    onStopGenerating = { viewModel.stopGenerating() },
                    onSpeechClick = onLaunchSpeechInput,
                    onAttachPhotoClick = onLaunchPhotoPicker,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding()
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Friendly Error Banner if any error occurs
                AnimatedVisibility(visible = errorMessage != null) {
                    val msg = errorMessage ?: ""
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, ErrorRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = msg,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearError() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Dismiss",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Chat Messages or Welcome Screen
                if (currentMessages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        WelcomeHeroScreen(
                            onSelectPrompt = { prompt ->
                                viewModel.sendMessage(prompt)
                            }
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(currentMessages, key = { _, msg -> msg.id }) { index, msg ->
                            // Check if date changed between consecutive messages
                            val showDateHeader = if (index == 0) {
                                true
                            } else {
                                val prevMsg = currentMessages[index - 1]
                                !DateFormatter.isSameDay(
                                    java.util.Calendar.getInstance().apply { timeInMillis = prevMsg.timestamp },
                                    java.util.Calendar.getInstance().apply { timeInMillis = msg.timestamp }
                                )
                            }

                            if (showDateHeader) {
                                DateHeaderSeparator(dateLabel = DateFormatter.formatDateHeader(msg.timestamp))
                            }

                            if (msg.sender == "user") {
                                UserMessageItem(
                                    message = msg,
                                    onDelete = { viewModel.deleteMessage(msg.id) }
                                )
                            } else {
                                val isActivelyStreaming = isGenerating && msg.id == currentMessages.lastOrNull()?.id
                                val isSpeakingThis = isSpeaking && speakingMessageId == msg.id
                                AssistantMessageItem(
                                    message = msg,
                                    isActivelyStreaming = isActivelyStreaming,
                                    isSpeakingThisMessage = isSpeakingThis,
                                    onSpeakToggle = { viewModel.toggleSpeech(msg.id, msg.text) },
                                    onRegenerate = { viewModel.regenerateLastMessage() },
                                    onDelete = { viewModel.deleteMessage(msg.id) }
                                )
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }

    // Rename Conversation Dialog
    if (sessionToRename != null) {
        val target = sessionToRename!!
        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            title = {
                Text(
                    text = "Rename Conversation",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a new title for this conversation:",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = renameInputText,
                        onValueChange = { renameInputText = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rename_chat_text_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentViolet,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameSession(target.first, renameInputText)
                        sessionToRename = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToRename = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Delete Single Session Confirmation Dialog
    if (sessionToDeleteId != null) {
        AlertDialog(
            onDismissRequest = { sessionToDeleteId = null },
            title = { Text(text = "Delete conversation?", color = TextPrimary) },
            text = {
                Text(
                    text = "This will permanently remove this conversation and its messages from your local Room database.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        sessionToDeleteId?.let { viewModel.deleteSession(it) }
                        sessionToDeleteId = null
                    }
                ) {
                    Text(text = "Delete", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDeleteId = null }) {
                    Text(text = "Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceCard
        )
    }

    // Storage Info & Settings Dialog
    if (showSettingsDialog) {
        val apiKeyConfigured = viewModel.isApiKeyConfigured()
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Room Database & Info",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Local Persistence:", color = TextSecondary, fontSize = 13.sp)
                        Text(
                            text = "Room SQLite (Active ✓)",
                            color = SuccessGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Saved Conversations:", color = TextSecondary, fontSize = 13.sp)
                        Text(
                            text = "${storageStats.first}",
                            color = AccentCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Total Stored Messages:", color = TextSecondary, fontSize = 13.sp)
                        Text(
                            text = "${storageStats.second}",
                            color = AccentCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "AI Model:", color = TextSecondary, fontSize = 13.sp)
                        Text(
                            text = "gemini-3.5-flash",
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "API Key Status:", color = TextSecondary, fontSize = 13.sp)
                        Text(
                            text = if (apiKeyConfigured) "Configured ✓" else "Missing API Key ⚠️",
                            color = if (apiKeyConfigured) SuccessGreen else ErrorRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "All chat history messages and sessions are persisted locally using Android Room database. You can review, search, rename, or export previous conversations anytime.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            showSettingsDialog = false
                            showClearConfirmDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "Clear All Chat History", color = ErrorRed, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text(text = "Close", color = AccentViolet)
                }
            },
            containerColor = DarkSurfaceCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Clear All Confirmation Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text(text = "Clear all chats?", color = TextPrimary) },
            text = {
                Text(
                    text = "This will permanently delete all conversation sessions and messages from your local Room database.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllData()
                        showClearConfirmDialog = false
                    }
                ) {
                    Text(text = "Delete All", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(text = "Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceCard
        )
    }
}

@Composable
fun ChatInputComposer(
    text: String,
    onTextChanged: (String) -> Unit,
    selectedImageUri: android.net.Uri?,
    onRemoveImage: () -> Unit,
    isGenerating: Boolean,
    isListening: Boolean,
    onSendMessage: () -> Unit,
    onStopGenerating: () -> Unit,
    onSpeechClick: () -> Unit,
    onAttachPhotoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkBackground)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Image Attachment Thumbnail (if any selected)
        if (selectedImageUri != null) {
            ImageAttachmentPreview(
                imageUri = selectedImageUri,
                onRemove = onRemoveImage,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        // Voice Listening Pulse Banner
        AnimatedVisibility(visible = isListening) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentVioletDark.copy(alpha = 0.4f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.speak_message),
                    color = AccentCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Composer Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(DarkSurfaceCard)
                .border(1.dp, DarkBorder, RoundedCornerShape(26.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Photo Attachment Button
            IconButton(
                onClick = onAttachPhotoClick,
                modifier = Modifier
                    .size(42.dp)
                    .testTag("attach_photo_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = "Attach Photo",
                    tint = if (selectedImageUri != null) AccentCyan else TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Speech Recognition Mic Button
            IconButton(
                onClick = onSpeechClick,
                modifier = Modifier
                    .size(42.dp)
                    .testTag("speech_mic_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Input",
                    tint = if (isListening) AccentCyan else TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Input Text Field
            OutlinedTextField(
                value = text,
                onValueChange = onTextChanged,
                placeholder = {
                    Text(
                        text = stringResource(R.string.type_a_message),
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .testTag("chat_input_text_field"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = AccentCyan
                ),
                maxLines = 4,
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Default
                )
            )

            // Send / Stop Button
            if (isGenerating) {
                IconButton(
                    onClick = onStopGenerating,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(AccentVioletDark)
                        .testTag("stop_generating_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop Generating",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                val canSend = text.isNotBlank() || selectedImageUri != null
                IconButton(
                    onClick = {
                        if (canSend) onSendMessage()
                    },
                    enabled = canSend,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (canSend) AccentViolet else DarkSurfaceHover)
                        .testTag("send_message_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = if (canSend) Color.White else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Status caption showing local Room storage
        Text(
            text = "Chat history is saved locally in Room database. Powered by Google Gemini.",
            color = TextMuted,
            fontSize = 11.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 2.dp)
        )
    }
}
