plugins {
    alias(libs.plugins.navi.android.library)
    alias(libs.plugins.navi.android.compose)
}

android {
    namespace = "dev.gruebling.navi.core.ui"
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.collections.immutable)
    api(libs.coil.compose)
}
