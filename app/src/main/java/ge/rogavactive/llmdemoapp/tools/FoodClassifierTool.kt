package ge.rogavactive.llmdemoapp.tools

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import org.tensorflow.lite.support.image.TensorImage

class FoodClassifierTool(private val context: Context) {

    companion object {
        private const val TAG = "FoodClassifierTool"
    }

    private val classifier: ImageClassifier by lazy {
        Log.d(TAG, "Loading TFLite model from assets...")
        try {
            val options = ImageClassifier.ImageClassifierOptions.builder()
                .setMaxResults(3)
                .setScoreThreshold(0.1f)
                .build()
            ImageClassifier.createFromFileAndOptions(context, "food_classifier.tflite", options).also {
                Log.d(TAG, "Model loaded successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            throw e
        }
    }

    private val labelMap: Map<Int, String> by lazy {
        Log.d(TAG, "Loading label map...")
        try {
            val map = mutableMapOf<Int, String>()
            context.assets.open("aiy_food_V1_labelmap.csv").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val parts = line.split(",", limit = 2)
                    if (parts.size == 2) {
                        val index = parts[0].trim().toIntOrNull()
                        val name = parts[1].trim()
                        if (index != null && !name.startsWith("/")) {
                            map[index] = name
                        }
                    }
                }
            }
            Log.d(TAG, "Label map loaded: ${map.size} entries")
            map
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load label map", e)
            emptyMap()
        }
    }

    private var lastBitmap: Bitmap? = null

    fun setBitmap(bitmap: Bitmap) {
        Log.d(TAG, "setBitmap called: ${bitmap.width}x${bitmap.height}, config=${bitmap.config}")
        lastBitmap = bitmap
    }

    fun classifyFood(): Map<String, String> {
        Log.d(TAG, "classifyFood called, lastBitmap=${lastBitmap != null}")

        val bitmap = lastBitmap ?: run {
            Log.w(TAG, "No bitmap set!")
            return mapOf("error" to "No image is currently selected")
        }

        Log.d(TAG, "Running inference on ${bitmap.width}x${bitmap.height} image...")

        return try {
            val tensorImage = TensorImage.fromBitmap(bitmap)
            val results = classifier.classify(tensorImage)

            Log.d(TAG, "Classification returned ${results.size} result(s)")
            if (results.isNotEmpty()) {
                Log.d(TAG, "Categories count: ${results[0].categories.size}")
                results[0].categories.forEach { cat ->
                    val readableName = labelMap[cat.index] ?: cat.label
                    Log.d(TAG, "  -> index=${cat.index}, label=${cat.label}, name=$readableName, score=${cat.score}")
                }
            }

            if (results.isEmpty() || results[0].categories.isEmpty()) {
                Log.w(TAG, "No categories found above threshold")
                return mapOf("error" to "Could not identify any food in the image")
            }

            val categories = results[0].categories
            val topResult = categories[0]
            val topName = labelMap[topResult.index] ?: topResult.label

            val alternatives = categories.drop(1).joinToString(", ") { cat ->
                val name = labelMap[cat.index] ?: cat.label
                "$name (${(cat.score * 100).toInt()}%)"
            }

            val output = mapOf(
                "food_name" to topName,
                "confidence" to "%.1f%%".format(topResult.score * 100),
                "alternatives" to alternatives
            )
            Log.d(TAG, "Returning: $output")
            output
        } catch (e: Exception) {
            Log.e(TAG, "Classification failed", e)
            mapOf("error" to "Classification error: ${e.message}")
        }
    }
}
