// androidx.a2ui is not published to Maven. Inside AOSP it is reached as
// project(":a2ui:a2ui-model") and friends, which only works from an AndroidX build.
// To run the library in an ordinary app we compile the checked-in AOSP snapshot
// directly, as one library module.
//
// Not a fork: no file below is edited. The source dirs point straight at
// ../../androidx-a2ui-source and ../../androidx-material3-a2ui-source, which are
// byte-for-byte copies of androidx-main — see SOURCE_COMMIT.txt in each. Re-sync
// those folders and this module picks the new code up on the next build.
//
// The four AOSP modules collapse into one here because their boundaries are
// enforced by metalava and the AndroidX build, neither of which we have. The
// layering still holds in the source: a2ui-model and a2ui-engine contain zero
// Compose imports.

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

// The snapshots live at the repo root, one level above this Gradle build.
val aosp: String = rootProject.projectDir.parentFile.absolutePath

android {
    // material3-a2ui reads R.string.error, so the generated R class has to land in
    // its own package. One namespace covers the whole vendored stack.
    namespace = "androidx.compose.material3.a2ui"
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

// AGP 9.3 stopped having DefaultAndroidLibrarySourceSet implement the legacy
// com.android.build.gradle.api.AndroidLibrarySourceSet, so reaching sourceSets through the
// legacy `android {}` accessor throws ClassCastException at configuration time. The same
// object is reachable through the com.android.build.api.dsl type, which it does implement.
extensions.configure<com.android.build.api.dsl.LibraryExtension>("android") {
    sourceSets.named("main") {
        kotlin.setSrcDirs(
            listOf(
                "$aosp/androidx-a2ui-source/a2ui-model/src/main/kotlin",
                "$aosp/androidx-a2ui-source/a2ui-engine/src/main/kotlin",
                "$aosp/androidx-a2ui-source/compose/compose-runtime/src/main/kotlin",
                "$aosp/androidx-a2ui-source/compose/compose-ui/src/main/kotlin",
                "$aosp/androidx-material3-a2ui-source/src/main/kotlin",
                // One file of the snapshot is compiled from here instead — see below.
                "src/pinned/kotlin",
            )
        )
        // The A2UI Slider moved to the new material3 SliderState(trackRange = ...) API on
        // 20 Aug 2026 (androidx-main 30a6b57, "[Slider] Deprecate stateless Slider and
        // RangeSlider overloads"). That API is still unreleased: 1.5.0-alpha27 is the newest
        // material3 on Maven (re-checked 2026-09-02). Upstream does not notice, because
        // material3-a2ui's build.gradle switched the same day from the alpha26 artifact to
        // project(":compose:material3:material3") — it now builds only inside AOSP.
        //
        // On 1 Sep 2026 (6198d65) the standalone MaterialSliderComponent was deleted and Slider
        // joined the A2uiBasicCatalogV1 contract, so the pinned file moved with it: it is now
        // catalog/MaterialA2uiBasicCatalogV1Slider, which MaterialA2uiBasicCatalogV1Defaults
        // wires in by name. src/pinned/kotlin carries upstream's own file with one line changed
        // — the stateless Slider overload instead of SliderState(trackRange = ...). Everything
        // else is HEAD. Delete the pin and drop the exclude when material3 ships trackRange.
        // (the exclude that pairs with it is on the compile task below — the AGP 9 source
        // set DSL has no per-file filter)
        res.setSrcDirs(listOf("$aosp/androidx-material3-a2ui-source/src/main/res"))
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    // Drops the snapshot's copy; src/pinned/kotlin/PinnedMaterialA2uiBasicCatalogV1Slider.kt
    // supplies the same internal object against the published material3 API. The pattern is
    // relative to each source root, which is why the pinned file carries a different file name.
    exclude("**/MaterialA2uiBasicCatalogV1Slider.kt")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    // Everything the AOSP build files ask for, resolved to published artifacts
    // instead of project(":...") paths.
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.collection)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core) // PatternsCompat, for the email catalog function
}
