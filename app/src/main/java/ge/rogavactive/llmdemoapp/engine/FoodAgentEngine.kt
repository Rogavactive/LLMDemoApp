package ge.rogavactive.llmdemoapp.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.FunctionDeclaration
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.Tool
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.defineFunction
import com.google.ai.client.generativeai.type.generationConfig
import ge.rogavactive.llmdemoapp.tools.FoodClassifierTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class FoodAgentEngine(private val context: Context) {

    companion object {
        private const val TAG = "FoodAgentEngine"
    }

    private val foodClassifierTool = FoodClassifierTool(context)
    private var model: GenerativeModel? = null
    private var chat: com.google.ai.client.generativeai.Chat? = null

    fun initialize(apiKey: String) {
        Log.d(TAG, "Initializing with API key length: ${apiKey.length}")

        val classifyFoodFunction = defineFunction(
            name = "classifyFood",
            description = "Classify a food item from the user's currently selected image. Call this when the user wants to know what food is in their photo.",
            parameters = listOf()
        )

        val tools = listOf(Tool(listOf(classifyFoodFunction)))

        model = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = apiKey,
            tools = tools,
            systemInstruction = content {
                text("""You are a nutrition assistant. When the user asks you to analyze a food image:
1. Call the classifyFood function to identify the food
2. Based on the food identified, provide:
   - The food name
   - Estimated calories per 100g
   - Brief macros (protein, carbs, fat per 100g)
Be concise and helpful. If the function returns an error, tell the user.""")
            }
        )

        chat = model!!.startChat()
        Log.d(TAG, "Initialization complete")
    }

    fun setBitmap(bitmap: Bitmap) {
        Log.d(TAG, "Bitmap set: ${bitmap.width}x${bitmap.height}")
        foodClassifierTool.setBitmap(bitmap)
    }

    suspend fun analyzeFood(): String = withContext(Dispatchers.IO) {
        val currentChat = chat ?: throw IllegalStateException("Engine not initialized")

        try {
            Log.d(TAG, "Sending message to Gemini...")
            val response = currentChat.sendMessage("Please analyze the food in my image and tell me its calories per 100g.")
            Log.d(TAG, "Response received. Function calls: ${response.functionCalls.size}, text: ${response.text?.take(100)}")

            val functionCall = response.functionCalls.firstOrNull()
            if (functionCall != null) {
                Log.d(TAG, "Function call: ${functionCall.name}, args: ${functionCall.args}")

                val result = foodClassifierTool.classifyFood()
                Log.d(TAG, "Classifier result: $result")

                val jsonResult = JSONObject(result).toString()

                val followUp = currentChat.sendMessage(
                    content("function") {
                        part(com.google.ai.client.generativeai.type.FunctionResponsePart(
                            functionCall.name,
                            JSONObject(jsonResult)
                        ))
                    }
                )

                Log.d(TAG, "Follow-up response: ${followUp.text?.take(200)}")
                followUp.text ?: "No response from model"
            } else {
                Log.w(TAG, "No function call in response, returning text directly")
                response.text ?: "No response from model"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini API error", e)
            Log.e(TAG, "Error class: ${e.javaClass.simpleName}, message: ${e.message}")
            if (e.cause != null) {
                Log.e(TAG, "Cause: ${e.cause?.javaClass?.simpleName}: ${e.cause?.message}")
            }
            throw e
        }
    }
}
