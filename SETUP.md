# Food Calorie Estimator - Setup Guide

## Architecture

```
User picks photo from gallery
        |
        v
ADK Agent (Gemini 2.0 Flash, cloud)
        | calls @Tool
        v
classifyFood() -> TFLite food classifier (on-device, ~7MB)
        | returns "pizza, 92.3%"
        v
Agent responds: "Pizza has ~266 calories per 100g..."
```

The image classification runs **on-device** (never leaves the phone). Only the agent
reasoning (calorie estimation) uses the cloud Gemini model.

## Prerequisites

- Android device (S22 or any)
- Android Studio with SDK 36
- Free Gemini API key from https://ai.google.dev

## Step 1: Get a Gemini API Key

1. Go to https://ai.google.dev
2. Click "Get API Key"
3. Create a key (free tier is fine for this demo)

## Step 2: Download the Food Classifier Model

Download Google's AIY Food V1 TFLite model:
https://www.kaggle.com/models/google/aiy/tfLite/vision-classifier-food-v1/1

Place the downloaded `.tflite` file as:
```
app/src/main/assets/food_classifier.tflite
```

This model recognizes 2024 food categories including pizza, apple, burger, sushi, etc.

## Step 3: Build and Run

1. Open the project in Android Studio
2. Sync Gradle
3. Build and run on your device
4. Enter your Gemini API key when prompted
5. Pick a food photo and tap "Analyze Food"

## How It Works

1. **ADK Agent** is created with Gemini 2.0 Flash as the model and `classifyFood` as a tool
2. When you tap "Analyze", the agent receives your request
3. The agent decides to call `classifyFood` (ADK's tool-calling mechanism)
4. `classifyFood` runs the TFLite MobileNet model **on-device** and returns the food name
5. The agent uses the result + its knowledge to generate calorie/nutrition info
6. Response is displayed in the app

## Performance

- Food classification: ~100ms (on-device, instant)
- Agent response: ~2-3 seconds (cloud Gemini Flash is fast)
- No large model download needed on the device

## Project Structure

```
app/src/main/
├── assets/
│   └── food_classifier.tflite      (you download this)
├── java/ge/rogavactive/llmdemoapp/
│   ├── MainActivity.kt             (Compose UI)
│   ├── MainViewModel.kt            (state management)
│   ├── engine/
│   │   └── FoodAgentEngine.kt      (ADK agent setup)
│   └── tools/
│       └── FoodClassifierTool.kt   (TFLite classifier as ADK tool)
└── AndroidManifest.xml
```
