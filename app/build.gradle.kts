plugins {
    alias(libs.plugins.navi.android.application)
    alias(libs.plugins.navi.android.compose)
    alias(libs.plugins.navi.hilt)
}

android {
    namespace = "dev.gruebling.navi"

    defaultConfig {
        applicationId = "dev.gruebling.navi"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // TODO: real release key. Debug signing keeps release builds installable for benchmarking.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures.buildConfig = true

    androidResources {
        localeFilters += listOf("en", "de")
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:navigation"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:playback"))
    implementation(project(":feature:login"))
    implementation(project(":feature:home"))
    implementation(project(":feature:search"))
    implementation(project(":feature:library"))
    implementation(project(":feature:detail"))
    implementation(project(":feature:player"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
