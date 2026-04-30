// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

tasks.register("assembleAllReleaseApks") {
    group = "build"
    description = "Builds all four release APK variants."
    dependsOn(
        ":app:assembleCommunityRelease",
        ":app:assembleLocalRelease",
        ":app:assembleOfficialRelease",
        ":app:assembleOfficialLocalRelease"
    )
}

tasks.register("assembleAllDebugApks") {
    group = "build"
    description = "Builds all four debug APK variants."
    dependsOn(
        ":app:assembleCommunityDebug",
        ":app:assembleLocalDebug",
        ":app:assembleOfficialDebug",
        ":app:assembleOfficialLocalDebug"
    )
}
