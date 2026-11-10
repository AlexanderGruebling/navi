plugins {
    alias(libs.plugins.navi.android.library)
    alias(libs.plugins.navi.hilt)
}

android {
    namespace = "dev.gruebling.navi.core.data"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:network"))
    api(project(":core:datastore"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.okhttp.mockwebserver)
}
