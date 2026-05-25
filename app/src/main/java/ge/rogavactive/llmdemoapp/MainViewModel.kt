package ge.rogavactive.llmdemoapp

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ge.rogavactive.llmdemoapp.tools.FoodClassifierTool
import ge.rogavactive.llmdemoapp.tools.FoodResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val classifierTool = FoodClassifierTool(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var selectedBitmap: Bitmap? = null

    data class UiState(
        val selectedImageUri: Uri? = null,
        val isAnalyzing: Boolean = false,
        val result: FoodResult? = null,
        val error: String? = null
    )

    fun onImageSelected(uri: Uri) {
        _uiState.value = UiState(selectedImageUri = uri)
        selectedBitmap = loadBitmapFromUri(uri)
    }

    fun analyzeImage() {
        val bitmap = selectedBitmap ?: run {
            _uiState.value = _uiState.value.copy(error = "No image selected")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true, result = null, error = null)
            val result = withContext(Dispatchers.Default) {
                classifierTool.setBitmap(bitmap)
                classifierTool.classify()
            }
            if (result != null) {
                _uiState.value = _uiState.value.copy(isAnalyzing = false, result = result)
            } else {
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    error = "Could not identify food in the image"
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
