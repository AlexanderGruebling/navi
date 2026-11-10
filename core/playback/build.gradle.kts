plugins {
    alias(libs.plugins.navi.android.library)
    alias(libs.plugins.navi.hilt)
}

android {
    namespace = "dev.gruebling.navi.core.playback"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:data"))
    api(libs.androidx.media3.session)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
}
