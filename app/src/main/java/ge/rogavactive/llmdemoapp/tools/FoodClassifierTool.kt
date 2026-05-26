package ge.rogavactive.llmdemoapp.tools

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import org.tensorflow.lite.support.image.TensorImage

data class FoodResult(
    val foodName: String,
    val confidence: Float,
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val fiber: Double,
    val sugar: Double,
    val sodium: Int,
    val cholesterol: Int,
    val vitaminA: Int,
    val vitaminC: Int,
    val vitaminD: Double,
    val vitaminB12: Double,
    val calcium: Int,
    val iron: Double,
    val potassium: Int,
    val folate: Int,
    val alternatives: List<String>
)

class FoodClassifierTool(private val context: Context) {

    companion object {
        private const val TAG = "FoodClassifierTool"
    }

    private val classifier: ImageClassifier by lazy {
        val options = ImageClassifier.ImageClassifierOptions.builder()
            .setMaxResults(3)
            .setScoreThreshold(0.1f)
            .build()
        ImageClassifier.createFromFileAndOptions(context, "food_classifier.tflite", options)
    }

    private val labelMap: Map<Int, String> by lazy {
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
    }

    private val nutritionMap: Map<String, JSONObject> by lazy {
        val map = mutableMapOf<String, JSONObject>()
        val json = context.assets.open("food_nutrition.json").bufferedReader().readText()
        val jsonObject = JSONObject(json)
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = jsonObject.getJSONObject(key)
        }
        Log.d(TAG, "Nutrition map loaded: ${map.size} entries")
        map
    }

    private var lastBitmap: Bitmap? = null

    fun setBitmap(bitmap: Bitmap) {
        lastBitmap = bitmap
    }

    fun classify(): FoodResult? {
        val bitmap = lastBitmap ?: return null

        return try {
            val tensorImage = TensorImage.fromBitmap(bitmap)
            val results = classifier.classify(tensorImage)

            if (results.isEmpty() || results[0].categories.isEmpty()) return null

            val categories = results[0].categories
            val topResult = categories[0]
            val topName = labelMap[topResult.index] ?: topResult.label

            val nutrition = nutritionMap[topName]

            val alternatives = categories.drop(1).map { cat ->
                val name = labelMap[cat.index] ?: cat.label
                "$name (${(cat.score * 100).toInt()}%)"
            }

            FoodResult(
                foodName = topName,
                confidence = topResult.score,
                calories = nutrition?.optInt("calories", 0) ?: 0,
                protein = nutrition?.optInt("protein", 0) ?: 0,
                carbs = nutrition?.optInt("carbs", 0) ?: 0,
                fat = nutrition?.optInt("fat", 0) ?: 0,
                fiber = nutrition?.optDouble("fiber", 0.0) ?: 0.0,
                sugar = nutrition?.optDouble("sugar", 0.0) ?: 0.0,
                sodium = nutrition?.optInt("sodium", 0) ?: 0,
                cholesterol = nutrition?.optInt("cholesterol", 0) ?: 0,
                vitaminA = nutrition?.optInt("vitamin_a_mcg", 0) ?: 0,
                vitaminC = nutrition?.optInt("vitamin_c_mg", 0) ?: 0,
                vitaminD = nutrition?.optDouble("vitamin_d_mcg", 0.0) ?: 0.0,
                vitaminB12 = nutrition?.optDouble("vitamin_b12_mcg", 0.0) ?: 0.0,
                calcium = nutrition?.optInt("calcium_mg", 0) ?: 0,
                iron = nutrition?.optDouble("iron_mg", 0.0) ?: 0.0,
                potassium = nutrition?.optInt("potassium_mg", 0) ?: 0,
                folate = nutrition?.optInt("folate_mcg", 0) ?: 0,
                alternatives = alternatives
            )
        } catch (e: Exception) {
            Log.e(TAG, "Classification failed", e)
            null
        }
    }
}
