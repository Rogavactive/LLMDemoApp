package ge.rogavactive.llmdemoapp.tools

import android.graphics.Bitmap
import android.util.Log
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool

class FoodClassifierAdkService(
    private val classifierTool: FoodClassifierTool
) {
    companion object {
        private const val TAG = "FoodClassifierAdk"
    }

    private val imageRegistry = mutableListOf<Bitmap>()

    fun registerImages(bitmaps: List<Bitmap>) {
        imageRegistry.clear()
        imageRegistry.addAll(bitmaps)
        Log.d(TAG, "registerImages: ${bitmaps.size} image(s) registered, instance=${System.identityHashCode(this)}")
        bitmaps.forEachIndexed { i, bmp ->
            Log.d(TAG, "  Registered[$i]: ${bmp.width}x${bmp.height}, bitmapHash=${System.identityHashCode(bmp)}")
        }
    }

    fun clearImages() {
        imageRegistry.clear()
        Log.d(TAG, "clearImages: registry cleared")
    }

    private val classifierLock = Object()

    @Tool
    fun classifyFoodImage(
        @Param("Zero-based index of the image to classify from the user's uploaded images")
        imageIndex: Int
    ): Map<String, String> {
        Log.d(TAG, ">>> classifyFoodImage CALLED with imageIndex=$imageIndex, registry size=${imageRegistry.size}, instance=${System.identityHashCode(this)}")

        if (imageIndex < 0 || imageIndex >= imageRegistry.size) {
            Log.w(TAG, "Invalid index $imageIndex, registry has ${imageRegistry.size} images")
            return mapOf(
                "error" to "Invalid image index $imageIndex. User uploaded ${imageRegistry.size} image(s), valid indices: 0..${imageRegistry.size - 1}"
            )
        }

        val bitmap = imageRegistry[imageIndex]
        Log.d(TAG, "Classifying image[$imageIndex]: ${bitmap.width}x${bitmap.height}, bitmapHash=${System.identityHashCode(bitmap)}")

        val result = synchronized(classifierLock) {
            classifierTool.setBitmap(bitmap)
            classifierTool.classify()
        }

        if (result == null) {
            Log.w(TAG, "Classifier returned null for image[$imageIndex]")
            return mapOf("error" to "Could not identify food in image at index $imageIndex")
        }
        Log.d(TAG, "Classification result: ${result.foodName} (${(result.confidence * 100).toInt()}%)")

        val output = mapOf(
            "food_name" to result.foodName,
            "confidence" to "${(result.confidence * 100).toInt()}%",
            "calories_per_100g" to "${result.calories} kcal",
            "protein_per_100g" to "${result.protein}g",
            "carbs_per_100g" to "${result.carbs}g",
            "fat_per_100g" to "${result.fat}g",
            "fiber_per_100g" to "${result.fiber}g",
            "sugar_per_100g" to "${result.sugar}g",
            "sodium_per_100g" to "${result.sodium}mg",
            "cholesterol_per_100g" to "${result.cholesterol}mg",
            "vitamin_a" to "${result.vitaminA} mcg",
            "vitamin_c" to "${result.vitaminC} mg",
            "vitamin_d" to "${result.vitaminD} mcg",
            "vitamin_b12" to "${result.vitaminB12} mcg",
            "calcium" to "${result.calcium} mg",
            "iron" to "${result.iron} mg",
            "potassium" to "${result.potassium} mg",
            "folate" to "${result.folate} mcg",
            "alternatives" to result.alternatives.joinToString(", ")
        )
        Log.d(TAG, "Returning: food=${result.foodName}, cal=${result.calories}, prot=${result.protein}")
        return output
    }
}
