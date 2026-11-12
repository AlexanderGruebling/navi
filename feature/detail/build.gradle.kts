plugins {
    alias(libs.plugins.navi.android.feature)
}

android {
    namespace = "dev.gruebling.navi.feature.detail"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:playback"))
    implementation(project(":core:download"))
}
