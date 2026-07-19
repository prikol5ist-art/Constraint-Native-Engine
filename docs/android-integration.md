# Android Integration

The Constraint-Native Generative Engine is built as a pure JVM library targeting Java 17. It is fully compatible with Android development, requiring Android SDK level 26 (Android 8.0) or higher.

---

## 1. Pure-JVM Compatibility

Because the core engine components and generative ranker orchestration contain no dependency on framework contexts or specific OS layers, they compile to standard JVM bytecode.
*   **Android API Level 26+**: Leverages standard Kotlin libraries, coroutines, value classes, and modern collection functions fully supported by Android Studio's desugaring mechanism.
*   **No Reflection**: Minimizes startup latency and reflection overhead, ensuring local solver tree traversal runs quickly on mobile CPU architectures.

---

## 2. Gradle Dependency Configuration

Add the core engine, solver, and the optional Gemini adapter to your Android application module's `build.gradle.kts`:

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.smartwardrobe"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.smartwardrobe"
        minSdk = 26
        targetSdk = 34
        // ...
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Core modeling and constraints
    implementation("io.github.constraintnative:engine-core:0.1.0-alpha")
    
    // Deterministic DFS/Beam solvers
    implementation("io.github.constraintnative:engine-solver:0.1.0-alpha")
    
    // Generative AI orchestration
    implementation("io.github.constraintnative:engine-generative:0.1.0-alpha")
    
    // Optional: Gemini API Adapter
    implementation("io.github.constraintnative:adapter-gemini:0.1.0-alpha")
    
    // Coroutines and Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
```

---

## 3. Injected Custom Transport Pattern

To preserve credential security, you must **never** hardcode Gemini API keys or credentials inside the Android application codebase. Instead, delegate the internet request to a custom `GeminiTransport`.

A secure transport can forward payloads to a backend proxy server (such as a Supabase Edge Function or corporate API Gateway) that appends credentials server-side.

### Example: Network Client Transport (with OkHttp)
Here is an example showing how to implement `GeminiTransport` using OkHttp on Android, delegating requests through a secure server proxy:

```kotlin
package com.example.smartwardrobe.network

import io.github.constraintnative.gemini.GeminiTransport
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class SecureProxyGeminiTransport(
    private val httpClient: OkHttpClient,
    private val proxyEndpointUrl: String,
    private val userAuthToken: String
) : GeminiTransport {

    override suspend fun generateContent(requestJson: String): String = withContext(Dispatchers.IO) {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = requestJson.toRequestBody(mediaType)
        
        // Build request to your secure proxy server (not directly to Google's Gemini API)
        val request = Request.Builder()
            .url(proxyEndpointUrl)
            .post(body)
            .addHeader("Authorization", "Bearer $userAuthToken")
            .build()
            
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected proxy server code: ${response.code} - ${response.message}")
            }
            response.body?.string() ?: throw IOException("Empty body from proxy server")
        }
    }
}
```

### Initializing the Engine in an Android Activity / ViewModel
Once the custom transport is created, pass it to the engine ranker during initialization:

```kotlin
package com.example.smartwardrobe.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartwardrobe.network.SecureProxyGeminiTransport
import io.github.constraintnative.core.*
import io.github.constraintnative.generative.ConstraintNativeEngine
import io.github.constraintnative.gemini.GeminiGenerativeRanker
import io.github.constraintnative.fashion.FashionContextRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class WardrobeViewModel : ViewModel() {

    private val engine = ConstraintNativeEngine()
    
    // Inject OkHttp and secure credentials
    private val transport = SecureProxyGeminiTransport(
        httpClient = OkHttpClient(),
        proxyEndpointUrl = "https://api.yourdomain.com/v1/generate-looks",
        userAuthToken = "session_jwt_here"
    )
    
    private val ranker = GeminiGenerativeRanker(transport)
    
    private val _uiState = MutableStateFlow<List<FeasibleSolution<String>>>(emptyList())
    val uiState: StateFlow<List<FeasibleSolution<String>>> = _uiState

    fun assembleOutfit(candidates: List<Candidate<String>>, occasion: String, temperature: Double) {
        viewModelScope.launch {
            val request = SolveRequest(
                candidates = candidates,
                slots = listOf(
                    SlotRequirement(SlotId("top"), min = 1, max = 1),
                    SlotRequirement(SlotId("bottom"), min = 1, max = 1)
                ),
                context = WardrobeContext(occasion, temperature),
                hardConstraints = listOf(WeatherSuitabilityConstraint()),
                softScorers = listOf(ColorHarmonyScorer()),
                desiredSolutions = 3,
                searchMode = SearchMode.BOUNDED_BEAM,
                searchBudget = SearchBudget(
                    maxExpandedNodes = 2000L,
                    maxDurationMillis = 3000L,
                    beamWidth = 10
                )
            )

            val result = engine.generate(
                request = request,
                ranker = ranker,
                renderer = FashionContextRenderer(),
                rankerTimeoutMillis = 4000L,
                diversityWeight = 0.5
            )
            
            _uiState.value = result.solutions
        }
    }
}
```
