import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val appConfigProps = Properties().apply {
    val file = rootProject.file("app-config.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val signingProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
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

        val fromAppConfig = appConfigProps.getProperty(key)
        if (!fromAppConfig.isNullOrBlank()) return fromAppConfig
    }
    return default
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

fun normalizeBaseUrl(raw: String): String =
    if (raw.endsWith("/")) raw else "$raw/"

val officialBackendBaseUrl = normalizeBaseUrl(
    readConfig(
        "OFFICIAL_BACKEND_BASE_URL",
        "BACKEND_BASE_URL",
        "official.backendBaseUrl",
        default = "https://api.prothesis.ru/"
    )
)

val localBackendBaseUrl = normalizeBaseUrl(
    readConfig(
        "LOCAL_BACKEND_BASE_URL",
        "local.backendBaseUrl",
        default = "http://10.0.2.2:8000/"
    )
)

val communityBackendBaseUrl = normalizeBaseUrl(
    readConfig(
        "COMMUNITY_BACKEND_BASE_URL",
        "community.backendBaseUrl",
        default = "https://community.invalid/"
    )
)

val emailOff = readConfig(
    "email_off",
    "EMAIL_OFF",
    "app.emailOff",
    default = "false"
).equals("true", ignoreCase = true)

val appGuideUrl = readConfig(
    "APP_GUIDE_URL",
    "app.guideUrl",
    default = "https://docs.google.com/document/d/1MEejkdQEGTkvxDuX7fgXnzfzSTgcVONKTlj-WCBkAp0/edit?usp=sharing"
)

val appRepositoryUrl = readConfig(
    "APP_REPOSITORY_URL",
    "app.repositoryUrl",
    default = "https://github.com/graevsky/6thFinger-App"
)

val esp32FirmwareUrl = readConfig(
    "ESP32_FIRMWARE_URL",
    "esp32.firmwareUrl",
    default = "https://github.com/graevsky/6thFinger-Controller"
)

val backendRepositoryUrl = readConfig(
    "BACKEND_REPOSITORY_URL",
    "backend.repositoryUrl",
    default = "https://github.com/graevsky/6thFinger-Backend"
)

val officialStoreFile = signingProps.getProperty("OFFICIAL_STORE_FILE")?.trim().orEmpty()
val hasOfficialSigning = officialStoreFile.isNotBlank()

android {
    namespace = "com.example.a6thfingercontrolapp"
    compileSdk = 36

    signingConfigs {
        create("officialRelease") {
            if (hasOfficialSigning) {
                storeFile = file(officialStoreFile)
                storePassword = signingProps.getProperty("OFFICIAL_STORE_PASSWORD")
                keyAlias = signingProps.getProperty("OFFICIAL_KEY_ALIAS")
                keyPassword = signingProps.getProperty("OFFICIAL_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.a6thfingercontrolapp"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("boolean", "EMAIL_OFF", emailOff.toString())

        buildConfigField("String", "APP_GUIDE_URL", appGuideUrl.asBuildConfigString())
        buildConfigField("String", "APP_REPOSITORY_URL", appRepositoryUrl.asBuildConfigString())
        buildConfigField("String", "ESP32_FIRMWARE_URL", esp32FirmwareUrl.asBuildConfigString())
        buildConfigField(
            "String",
            "BACKEND_REPOSITORY_URL",
            backendRepositoryUrl.asBuildConfigString()
        )
    }

    flavorDimensions += "channel"

    productFlavors {
        create("official") {
            dimension = "channel"
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            if (hasOfficialSigning) {
                signingConfig = signingConfigs.getByName("officialRelease")
            }
            buildConfigField(
                "String",
                "BACKEND_BASE_URL",
                officialBackendBaseUrl.asBuildConfigString()
            )
            buildConfigField("boolean", "CLIENT_ATTESTATION_REQUIRED", "true")
        }

        create("community") {
            dimension = "channel"
            applicationIdSuffix = ".community"
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField(
                "String",
                "BACKEND_BASE_URL",
                communityBackendBaseUrl.asBuildConfigString()
            )
            buildConfigField("boolean", "CLIENT_ATTESTATION_REQUIRED", "false")
        }

        create("local") {
            dimension = "channel"
            applicationIdSuffix = ".local"
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField(
                "String",
                "BACKEND_BASE_URL",
                localBackendBaseUrl.asBuildConfigString()
            )
            buildConfigField("boolean", "CLIENT_ATTESTATION_REQUIRED", "false")
        }

        create("officialLocal") {
            dimension = "channel"
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            if (hasOfficialSigning) {
                signingConfig = signingConfigs.getByName("officialRelease")
            }
            buildConfigField(
                "String",
                "BACKEND_BASE_URL",
                localBackendBaseUrl.asBuildConfigString()
            )
            buildConfigField("boolean", "CLIENT_ATTESTATION_REQUIRED", "true")
        }
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
