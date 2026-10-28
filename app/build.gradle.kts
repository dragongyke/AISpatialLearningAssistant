import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.spotless)
}

fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val aiLocalProperties =
    Properties().apply {
        val text =
            providers
                .fileContents(rootProject.layout.projectDirectory.file("ai.local.properties"))
                .asText
                .orElse("")
                .get()
        try {
            load(text.removePrefix("\uFEFF").reader())
        } catch (_: IllegalArgumentException) {
            throw GradleException("Invalid ai.local.properties syntax. Check property escaping.")
        }
    }

// Explicit build/CI inputs override project-local values; user Gradle settings are the fallback.
fun secretValue(name: String): String =
    (gradle.startParameter.projectProperties[name]
            ?: providers.environmentVariable(name).orNull
            ?: aiLocalProperties.getProperty(name)
            ?: providers.gradleProperty(name).orNull
            ?: "")
        .trim()

val arkVisionModel = secretValue("ARK_VISION_MODEL")
val arkTextModel = secretValue("ARK_TEXT_MODEL").ifBlank { arkVisionModel }

tasks.register("checkAiConfiguration") {
    group = "verification"
    description =
        "Check the cloud AI fields for a private Debug build without printing credentials."
    val fields =
        mapOf(
            "ARK_API_KEY" to secretValue("ARK_API_KEY"),
            "ARK_TEXT_MODEL" to arkTextModel,
            "ARK_VISION_MODEL" to arkVisionModel,
            "VOLCENGINE_SPEECH_APP_ID" to secretValue("VOLCENGINE_SPEECH_APP_ID"),
            "VOLCENGINE_SPEECH_ACCESS_TOKEN" to secretValue("VOLCENGINE_SPEECH_ACCESS_TOKEN"),
        )
    doLast {
        val placeholder = Regex("(?i)^(?:(?:sk-|ark-)?your[-_]|replace[-_]|example|placeholder|<)")
        val missing =
            fields.filterValues {
                it.isBlank() ||
                    placeholder.containsMatchIn(it) ||
                    it.any { character ->
                        character.isWhitespace() || character == '"' || character == '\''
                    }
            }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "AI configuration is incomplete. Missing/placeholder/invalid fields: " +
                    missing.keys.joinToString() +
                    ". Fill ai.local.properties using ai.local.properties.example. Values are not printed."
            )
        }
        logger.lifecycle("AI configuration fields are complete. No cloud services were called.")
        logger.lifecycle(
            "Account permissions, quota and connectivity still need runtime verification."
        )
        logger.lifecycle("Use assembleDebug for private testing. Release strips credentials.")
    }
}

android {
    namespace = "com.pico.spatial.sample.spatialvideo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pico.spatial.sample.aispatiallearning"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = libs.versions.spatialBom.get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        ndk { abiFilters.add("arm64-v8a") }

        val deepSeekApiKey = secretValue("DEEPSEEK_API_KEY")
        buildConfigField("String", "DEEPSEEK_API_KEY", deepSeekApiKey.asBuildConfigString())
        buildConfigField(
            "String",
            "DEEPSEEK_BASE_URL",
            "https://api.deepseek.com".asBuildConfigString()
        )
        buildConfigField("String", "DEEPSEEK_MODEL", "deepseek-v4-flash".asBuildConfigString())

        buildConfigField(
            "String",
            "VOLCENGINE_SPEECH_APP_ID",
            secretValue("VOLCENGINE_SPEECH_APP_ID").asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "VOLCENGINE_SPEECH_ACCESS_TOKEN",
            secretValue("VOLCENGINE_SPEECH_ACCESS_TOKEN").asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "VOLCENGINE_ASR_RESOURCE_ID",
            "volc.bigasr.sauc.duration".asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "VOLCENGINE_TTS_RESOURCE_ID",
            "volc.service_type.10029".asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "VOLCENGINE_TTS_VOICE_TYPE",
            "zh_female_cancan_mars_bigtts".asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "ARK_API_KEY",
            secretValue("ARK_API_KEY").asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "ARK_VISION_MODEL",
            arkVisionModel.asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "ARK_TEXT_MODEL",
            arkTextModel.asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "ARK_BASE_URL",
            "https://ark.cn-beijing.volces.com/api/v3".asBuildConfigString(),
        )
    }

    buildTypes {
        release {
            // Shared APKs must never contain the developer's local service credentials.
            listOf(
                    "DEEPSEEK_API_KEY",
                    "ARK_API_KEY",
                    "VOLCENGINE_SPEECH_APP_ID",
                    "VOLCENGINE_SPEECH_ACCESS_TOKEN"
                )
                .forEach { buildConfigField("String", it, "".asBuildConfigString()) }
            isMinifyEnabled = true
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
    kotlinOptions { jvmTarget = "11" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources { noCompress += listOf(".bundle", ".mp4", ".ktx") }
}

spotless {
    java {
        target("**/*.java")
        targetExclude("**/build/**/*.java", ".gradle/**", "**/.gradle/**")
        googleJavaFormat("1.17.0").aosp().reflowLongStrings()
    }
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt", ".gradle/**", "**/.gradle/**")
        ktfmt("0.50").kotlinlangStyle()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude(
            "**/build/**/*.gradle.kts",
            "**/build/**/*.gradle",
            ".gradle/**",
            "**/.gradle/**"
        )
        ktfmt("0.50").kotlinlangStyle()
    }
}

dependencies {
    // Spatial SDK dependencies
    implementation(platform(libs.spatial.bom))
    implementation(libs.spatial.core)
    implementation(libs.spatial.foundation)
    implementation(libs.spatial.tracking)
    implementation(libs.spatial.sense)
    implementation(libs.spatial.ui.foundation)
    implementation(libs.spatial.ui.platform)
    implementation(libs.spatial.ui.design)

    // AndroidX, Compose, and other dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)
    implementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.navigation.compose)
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("com.bytedance.speechengine:speechengine_tob:0.0.14.6.1-bugfix")
    implementation("com.squareup.okhttp3:okhttp:4.9.1")
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.9.1")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.ui.tooling.preview)
}

configurations.all {
    resolutionStrategy {
        exclude("androidx.compose.ui", "ui")
        exclude("androidx.compose.ui", "ui-graphics")
        exclude("androidx.compose.ui", "ui-text")
        exclude("androidx.compose.foundation", "foundation")
    }
}
