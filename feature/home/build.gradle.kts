plugins {
    alias(libs.plugins.navi.android.feature)
}

android {
    namespace = "dev.gruebling.navi.feature.home"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:playback"))
    implementation(libs.androidx.activity.compose)
}
