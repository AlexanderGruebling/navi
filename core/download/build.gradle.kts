plugins {
    alias(libs.plugins.navi.android.library)
    alias(libs.plugins.navi.hilt)
}

android {
    namespace = "dev.gruebling.navi.core.download"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:data"))
    api(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    implementation(libs.okhttp)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.androidx.datastore.preferences)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.okhttp.mockwebserver)
}
