plugins {
    alias(libs.plugins.navi.android.library)
    alias(libs.plugins.navi.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.gruebling.navi.core.network"
    buildFeatures.buildConfig = true
}

dependencies {
    api(project(":core:model"))
    api(libs.okhttp)
    api(libs.retrofit)
    api(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
}
