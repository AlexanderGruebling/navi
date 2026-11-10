plugins {
    alias(libs.plugins.navi.android.feature)
}

android {
    namespace = "dev.gruebling.navi.feature.login"
}

dependencies {
    implementation(project(":core:data"))
}
