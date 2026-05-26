package ge.rogavactive.llmdemoapp.chat

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.sessions.InMemorySessionService
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import ge.rogavactive.llmdemoapp.agent.NutritionistAgent
import ge.rogavactive.llmdemoapp.tools.FoodClassifierAdkService
import ge.rogavactive.llmdemoapp.tools.FoodClassifierTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    private val classifierTool = FoodClassifierTool(application)
    private val adkService = FoodClassifierAdkService(classifierTool)
    private val agent = NutritionistAgent.create(adkService)
    private val sessionService = InMemorySessionService()
    private val runner = InMemoryRunner(
        agent = agent,
        sessionService = sessionService
    )

    private val userId = "user"
    private val sessionId = UUID.randomUUID().toString()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun onInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun onImagesSelected(uris: List<Uri>) {
        val current = _uiState.value.pendingImageUris
        _uiState.value = _uiState.value.copy(pendingImageUris = current + uris)
    }

    fun onRemovePendingImage(index: Int) {
        val current = _uiState.value.pendingImageUris.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _uiState.value = _uiState.value.copy(pendingImageUris = current)
        }
    }

    fun onSendMessage() {
        val state = _uiState.value
        val text = state.inputText.trim()
        val imageUris = state.pendingImageUris

        if (text.isBlank() && imageUris.isEmpty()) return

        val userMessage = ChatMessage.User(
            id = UUID.randomUUID().toString(),
            text = text,
            imageUris = imageUris
        )

        val messages = state.messages + userMessage
        _uiState.value = state.copy(
            messages = messages,
            inputText = "",
            pendingImageUris = emptyList(),
            isAgentResponding = true,
            error = null
        )

        viewModelScope.launch {
            try {
                val bitmaps = withContext(Dispatchers.IO) {
                    imageUris.mapNotNull { loadBitmapFromUri(it) }
                }
                adkService.registerImages(bitmaps)
                Log.d(TAG, "Registered ${bitmaps.size} bitmap(s) in image registry")
                bitmaps.forEachIndexed { i, bmp ->
                    Log.d(TAG, "  Image[$i]: ${bmp.width}x${bmp.height}")
                }

                val messageText = buildString {
                    if (bitmaps.isNotEmpty()) {
                        append("[User uploaded ${bitmaps.size} image(s). Use classifyFoodImage with indices 0..${bitmaps.size - 1} to analyze them.]\n")
                    }
                    append(text)
                }

                Log.d(TAG, "Sending to agent: $messageText")

                val agentMessageId = UUID.randomUUID().toString()
                val responseBuilder = StringBuilder()

                withContext(Dispatchers.IO) {
                    runner.runAsync(
                        userId = userId,
                        sessionId = sessionId,
                        newMessage = Content(
                            role = Role.USER,
                            parts = listOf(Part(text = messageText))
                        )
                    ).collect { event ->
                        val parts = event.content?.parts ?: emptyList()
                        Log.d(TAG, "Event: author=${event.author}, role=${event.content?.role}, parts=${parts.size}")
                        parts.forEachIndexed { i, part ->
                            Log.d(TAG, "  Part[$i]: text=${part.text?.take(100)}, functionCall=${part.functionCall}, functionResponse=${part.functionResponse}")
                        }
                        val eventText = event.content?.parts?.firstOrNull()?.text
                        if (!eventText.isNullOrBlank()) {
                            Log.d(TAG, "Agent text: ${eventText.take(200)}")
                            responseBuilder.clear()
                            responseBuilder.append(eventText)

                            val agentMsg = ChatMessage.Agent(
                                id = agentMessageId,
                                text = responseBuilder.toString()
                            )
                            val currentMessages = _uiState.value.messages
                                .filter { it.id != agentMessageId }
                            _uiState.value = _uiState.value.copy(
                                messages = currentMessages + agentMsg
                            )
                        }
                    }
                }

                _uiState.value = _uiState.value.copy(isAgentResponding = false)
                adkService.clearImages()

            } catch (e: Exception) {
                Log.e(TAG, "Agent error", e)
                _uiState.value = _uiState.value.copy(
                    isAgentResponding = false,
                    error = "Error: ${e.message}"
                )
                adkService.clearImages()
            }
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val contentResolver = getApplication<Application>().contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.setTargetSampleSize(2)
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap from $uri", e)
            null
        }
    }
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val pendingImageUris: List<Uri> = emptyList(),
    val isAgentResponding: Boolean = false,
    val error: String? = null
)

sealed interface ChatMessage {
    val id: String

    data class User(
        override val id: String,
        val text: String,
        val imageUris: List<Uri>
    ) : ChatMessage

    data class Agent(
        override val id: String,
        val text: String
    ) : ChatMessage
}
