import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(name: String): String {
    return (localProps.getProperty(name) ?: System.getenv(name) ?: "")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}

android {
    namespace = "com.nicdeveloper.kuida"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.nicdeveloper.kuida"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GEMINI_API_KEY", "\"${secret("GEMINI_API_KEY")}\"")
        buildConfigField("String", "GEMINI_MODEL", "\"${secret("GEMINI_MODEL").ifBlank { "gemini-flash-lite-latest" }}\"")
        buildConfigField("String", "OPENROUTER_API_KEY", "\"${secret("OPENROUTER_API_KEY")}\"")
        buildConfigField("String", "OPENROUTER_ENDPOINT", "\"${secret("OPENROUTER_ENDPOINT").ifBlank { "https://openrouter.ai/api/v1" }}\"")
        buildConfigField("String", "OPENROUTER_MODEL", "\"${secret("OPENROUTER_MODEL").ifBlank { "openrouter/free" }}\"")
        buildConfigField("String", "OPENROUTER_MODEL_MULTIMODAL", "\"${secret("OPENROUTER_MODEL_MULTIMODAL").ifBlank { secret("OPENROUTER_MODEL").ifBlank { "openrouter/free" } }}\"")
        buildConfigField("String", "FISH_API_KEY", "\"${secret("FISH_API_KEY")}\"")
        buildConfigField("String", "FISH_VOICE_ID", "\"${secret("FISH_VOICE_ID")}\"")
        buildConfigField("String", "FISH_API_KEY", "\"${secret("FISH_API_KEY")}\"")
        buildConfigField("String", "FISH_VOICE_ID", "\"${secret("FISH_VOICE_ID")}\"")
        buildConfigField("String", "FISH_API_KEY", "\"${secret("FISH_API_KEY")}\"")
        buildConfigField("String", "FISH_VOICE_ID", "\"${secret("FISH_VOICE_ID")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    androidResources {
        noCompress += "tflite"
        // Modelo de voz Vosk: debe ir sin comprimir o no se puede cargar
        noCompress += listOf("mdl", "fst", "ark", "scp", "conf", "ali",
                "syms", "l", "mat", "vec", "tree", "topo", "hmm", "plp", "eps", "txt")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.ai.edge.litert:litert:1.4.2")
    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation("com.google.mlkit:digital-ink-recognition:18.1.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
