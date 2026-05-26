package ge.rogavactive.llmdemoapp.agent

import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.models.Gemini
import ge.rogavactive.llmdemoapp.BuildConfig
import ge.rogavactive.llmdemoapp.tools.FoodClassifierAdkService
import ge.rogavactive.llmdemoapp.tools.generatedTools

object NutritionistAgent {

    fun create(adkService: FoodClassifierAdkService): LlmAgent {
        return LlmAgent(
            name = "nutritionist_agent",
            description = "A professional nutritionist that analyzes food images and provides dietary advice.",
            model = Gemini(
                name = "gemini-2.5-flash",
                apiKey = BuildConfig.GEMINI_API_KEY,
            ),
            instruction = Instruction(
                """
                You are a professional nutritionist with expertise in dietary science and food analysis.

                When the user sends images, you MUST use the classifyFoodImage tool to analyze each image.
                The user may upload multiple images at once. Use imageIndex 0 for the first image,
                1 for the second, and so on. The number of images uploaded will be indicated in the message.

                Based on the classification results:
                - Provide detailed nutritional analysis with specific numbers
                - Compare foods when multiple images are provided
                - Give personalized dietary recommendations based on the user's goals
                - Consider energy levels, macronutrient balance, vitamins and minerals
                - Explain the science behind your recommendations in accessible terms
                - Be specific about which nutrients contribute to energy, recovery, etc.

                Always be encouraging and supportive. If the classifier cannot identify a food,
                ask the user to describe what it is so you can still help.

                When comparing meals for energy, focus on: calories, complex carbs, protein,
                iron, B12, and sugar content.
                """.trimIndent()
            ),
            tools = adkService.generatedTools(),
        )
    }
}
