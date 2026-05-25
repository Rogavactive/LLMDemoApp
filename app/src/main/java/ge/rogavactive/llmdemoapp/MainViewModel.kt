package ge.rogavactive.llmdemoapp

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ge.rogavactive.llmdemoapp.engine.FoodAgentEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val agentEngine = FoodAgentEngine(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var selectedBitmap: Bitmap? = null

    data class UiState(
        val selectedImageUri: Uri? = null,
        val isLoading: Boolean = true,
        val isReady: Boolean = false,
        val isAnalyzing: Boolean = false,
        val result: String? = null,
        val error: String? = null
    )

    init {
        initializeAgent()
    }

    private fun initializeAgent() {
        viewModelScope.launch {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "GEMINI_API_KEY not set in local.properties"
                    )
                    return@launch
                }
                agentEngine.initialize(apiKey)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isReady = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to initialize: ${e.message}"
                )
            }
        }
    }

    fun onImageSelected(uri: Uri) {
        _uiState.value = _uiState.value.copy(
            selectedImageUri = uri,
            result = null,
            error = null
        )
        selectedBitmap = loadBitmapFromUri(uri)
    }

    fun analyzeImage() {
        val bitmap = selectedBitmap ?: run {
            _uiState.value = _uiState.value.copy(error = "No image selected")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true, result = null, error = null)
            try {
                agentEngine.setBitmap(bitmap)
                val result = agentEngine.analyzeFood()
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    result = result
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    error = "Analysis failed: ${e.message}"
                )
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
            null
        }
    }
}
