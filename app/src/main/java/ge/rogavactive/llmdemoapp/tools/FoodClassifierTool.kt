package ge.rogavactive.llmdemoapp.tools

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import org.tensorflow.lite.support.image.TensorImage

class FoodClassifierTool(private val context: Context) {

    private val classifier: ImageClassifier by lazy {
        val options = ImageClassifier.ImageClassifierOptions.builder()
            .setMaxResults(3)
            .setScoreThreshold(0.2f)
            .build()
        ImageClassifier.createFromFileAndOptions(context, "food_classifier.tflite", options)
    }

    private var lastBitmap: Bitmap? = null

    fun setBitmap(bitmap: Bitmap) {
        lastBitmap = bitmap
    }

    fun classifyFood(): Map<String, String> {
        val bitmap = lastBitmap ?: return mapOf(
            "error" to "No image is currently selected"
        )

        val tensorImage = TensorImage.fromBitmap(bitmap)
        val results = classifier.classify(tensorImage)

        if (results.isEmpty() || results[0].categories.isEmpty()) {
            return mapOf("error" to "Could not identify any food in the image")
        }

        val categories = results[0].categories
        val topResult = categories[0]

        return mapOf(
            "food_name" to topResult.label,
            "confidence" to "%.1f%%".format(topResult.score * 100),
            "alternatives" to categories.drop(1).joinToString(", ") { "${it.label} (${(it.score * 100).toInt()}%)" }
        )
    }
}
