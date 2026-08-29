import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

// Preferred way to configure the agent is the app's Settings screen — then no key touches
// the build at all. This is the opt-in shortcut for repeat installs: an explicit
// GEMINI_API_KEY line in local.properties, which is gitignored.
//
// Deliberately NOT read from the environment: an ambient GEMINI_API_KEY in a shell would
// end up baked into an APK without anyone deciding to put it there. Opting in has to be a
// visible act. NoHardcodedSecretsTest fails the build if a key ever lands in a tracked file.
//
// Whatever is set here is embedded in the debug APK's BuildConfig and can be extracted by
// anyone holding that APK — fine for a conference demo, wrong for a shipped app. The release
// variant below always gets an empty key. In production the model is called from your own
// backend, or through Firebase AI Logic, so the key never reaches the device at all.
val geminiApiKey: String =
    Properties()
        .apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load) }
        .getProperty("GEMINI_API_KEY")
        .orEmpty()

android {
    namespace = "com.example.a2uicomposelabs"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.example.a2uicomposelabs"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        }
        release {
            // A distributable APK never carries the key. The live demo falls back to replay.
            buildConfigField("String", "GEMINI_API_KEY", "\"\"")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true // carries GEMINI_API_KEY into the live-agent demo
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  // Demos that show remote artwork and play preview clips; the renderer keeps its own
  // copies for the Image and AudioPlayer components.
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.coil.compose)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // A2UI renderer library (spec v1.0 subset)
  implementation(project(":a2ui-renderer"))
  // The AOSP androidx.a2ui snapshot, compiled from source — see androidx-a2ui/build.gradle.kts
  implementation(project(":androidx-a2ui"))
  // A2UI messages are plain JSON — runtime JSON tree API only, no @Serializable codegen needed
  implementation(libs.kotlinx.serialization.json)
}
