plugins {
    alias(libs.plugins.navi.android.library)
    alias(libs.plugins.navi.hilt)
}

android {
    namespace = "dev.gruebling.navi.core.datastore"
}

dependencies {
    api(project(":core:model"))
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
}
