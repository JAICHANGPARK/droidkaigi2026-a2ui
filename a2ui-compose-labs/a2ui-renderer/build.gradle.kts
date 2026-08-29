plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.example.a2uicomposelabs.a2ui"
    compileSdk = 37
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    // Modifier / @Composable appear in the public API surface
    api(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    // Full Basic Catalog coverage: images, icons, media
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    // JsonObject / JsonElement appear in the public API surface
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
