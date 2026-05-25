package ge.rogavactive.llmdemoapp.engine

import android.content.Context
import android.graphics.Bitmap
import com.google.adk.agents.LlmAgent
import com.google.adk.models.Gemini
import com.google.adk.runner.InMemoryRunner
import com.google.adk.sessions.InMemorySessionService
import com.google.adk.tools.FunctionTool
import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.Part
import ge.rogavactive.llmdemoapp.tools.FoodClassifierTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FoodAgentEngine(private val context: Context) {

    private val foodClassifierTool = FoodClassifierTool(context)
    private var runner: InMemoryRunner? = null
    private var sessionId: String? = null

    companion object {
        private const val APP_NAME = "FoodCalorieEstimator"
        private const val USER_ID = "user_1"
    }

    suspend fun initialize(apiKey: String) {
        val client = Client.builder()
            .apiKey(apiKey)
            .build()

        val model = Gemini(client = client, name = "gemini-2.0-flash")

        val classifyFoodTool = FunctionTool.create(
            foodClassifierTool::classifyFood
        )

        val agent = LlmAgent(
            name = "food_nutrition_agent",
            model = model,
            instruction = """You are a nutrition assistant. When the user asks you to analyze a food image:
1. Call the classifyFood tool to identify the food
2. Based on the food identified, provide:
   - The food name
   - Estimated calories per 100g
   - Brief macros (protein, carbs, fat per 100g)
Be concise and helpful. If the tool returns an error, tell the user.""",
            description = "An agent that identifies food from images and estimates calories",
            tools = listOf(classifyFoodTool)
        )

        val sessionService = InMemorySessionService()
        runner = InMemoryRunner(agent = agent, appName = APP_NAME, sessionService = sessionService)

        val session = sessionService.createSession(
            appName = APP_NAME,
            userId = USER_ID
        )
        sessionId = session.id
    }

    fun setBitmap(bitmap: Bitmap) {
        foodClassifierTool.setBitmap(bitmap)
    }

    suspend fun analyzeFood(): String = withContext(Dispatchers.IO) {
        val currentRunner = runner ?: throw IllegalStateException("Engine not initialized")
        val currentSessionId = sessionId ?: throw IllegalStateException("No session created")

        val userMessage = Content.builder()
            .role("user")
            .parts(listOf(Part.fromText("Please analyze the food in my image and tell me its calories per 100g.")))
            .build()

        val responseBuilder = StringBuilder()

        currentRunner.runAsync(
            userId = USER_ID,
            sessionId = currentSessionId,
            newMessage = userMessage
        ).collect { event ->
            if (event.isFinalResponse() && event.content != null) {
                event.content.parts?.forEach { part ->
                    part.text?.let { responseBuilder.append(it) }
                }
            }
        }

        responseBuilder.toString().ifEmpty { "No response from agent" }
    }
}
