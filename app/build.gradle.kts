plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
val releaseSigning = listOf(
    "OLYMPUS_KEYSTORE",
    "OLYMPUS_STORE_PASSWORD",
    "OLYMPUS_KEY_ALIAS",
    "OLYMPUS_KEY_PASSWORD"
).associateWith { providers.environmentVariable(it).orNull?.takeIf(String::isNotBlank) }
val hasReleaseSigning = releaseSigning.values.all { it != null }
require(hasReleaseSigning || releaseSigning.values.none { it != null }) {
    "Set all four Olympus release signing variables or leave them unset for an unsigned build."
}

android {
    namespace = "dev.olympus.desk"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.olympus.desk"
        minSdk = 29
        targetSdk = 37
        versionCode = 13
        versionName = "0.8.1"
    }
    if (hasReleaseSigning) {
        signingConfigs.create("production") {
            storeFile = rootProject.file(releaseSigning.getValue("OLYMPUS_KEYSTORE")!!)
            storePassword = releaseSigning.getValue("OLYMPUS_STORE_PASSWORD")
            keyAlias = releaseSigning.getValue("OLYMPUS_KEY_ALIAS")
            keyPassword = releaseSigning.getValue("OLYMPUS_KEY_PASSWORD")
        }
    }
    buildTypes.named("release") {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        if (hasReleaseSigning) signingConfig = signingConfigs.getByName("production")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.material3:material3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
