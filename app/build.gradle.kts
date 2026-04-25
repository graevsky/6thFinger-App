import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun readConfig(vararg keys: String, default: String = ""): String {
    for (key in keys) {
        val fromGradle = providers.gradleProperty(key).orNull
        if (!fromGradle.isNullOrBlank()) return fromGradle

        val fromEnv = System.getenv(key)
        if (!fromEnv.isNullOrBlank()) return fromEnv

        val fromLocal = localProps.getProperty(key)
        if (!fromLocal.isNullOrBlank()) return fromLocal
    }
    return default
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val backendBaseUrl = readConfig(
    "BACKEND_BASE_URL",
    "backend.baseUrl",
    default = "https://prothesis.ru/"
).let { if (it.endsWith("/")) it else "$it/" }

val appClientTokenEnabled = readConfig(
    "APP_CLIENT_TOKEN_ENABLED",
    "app.clientTokenEnabled",
    default = "false"
).equals("true", ignoreCase = true)

val appClientHeaderName = readConfig(
    "APP_CLIENT_HEADER_NAME",
    "app.clientHeaderName",
    default = "X-App-Token"
)

val appClientToken = readConfig(
    "APP_CLIENT_TOKEN",
    "app.clientToken",
    default = ""
)

android {
    namespace = "com.example.a6thfingercontrolapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.a6thfingercontrollapp"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BACKEND_BASE_URL", backendBaseUrl.asBuildConfigString())
        buildConfigField("boolean", "APP_CLIENT_TOKEN_ENABLED", appClientTokenEnabled.toString())
        buildConfigField("String", "APP_CLIENT_HEADER_NAME", appClientHeaderName.asBuildConfigString())
        buildConfigField("String", "APP_CLIENT_TOKEN", appClientToken.asBuildConfigString())
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.0.0-alpha.14")

    implementation("com.squareup.moshi:moshi:1.15.1")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")

    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.3")
    implementation("com.vanniktech:android-image-cropper:4.7.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}