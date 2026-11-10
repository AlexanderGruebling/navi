plugins {
    alias(libs.plugins.navi.android.feature)
}

android {
    namespace = "dev.gruebling.navi.feature.search"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:playback"))
}
