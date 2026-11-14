plugins {
    alias(libs.plugins.navi.android.feature)
}

android {
    namespace = "dev.gruebling.navi.feature.releases"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:playback"))
}
