plugins {
    alias(libs.plugins.navi.android.feature)
}

android {
    namespace = "dev.gruebling.navi.feature.library"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:playback"))
    implementation(libs.androidx.paging.compose)
}
