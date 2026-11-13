plugins {
    alias(libs.plugins.navi.android.feature)
}

android {
    namespace = "dev.gruebling.navi.feature.addtoplaylist"
}

dependencies {
    implementation(project(":core:data"))
}
