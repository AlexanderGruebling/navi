plugins {
    alias(libs.plugins.navi.android.feature)
}

android {
    namespace = "dev.gruebling.navi.feature.player"
}

dependencies {
    implementation(project(":core:playback"))
}
