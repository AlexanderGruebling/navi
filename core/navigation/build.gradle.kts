plugins {
    alias(libs.plugins.navi.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.gruebling.navi.core.navigation"
}

dependencies {
    api(libs.androidx.navigation3.runtime)
    api(libs.kotlinx.serialization.json)
}
