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
    api(project(":core:database"))
    api(libs.androidx.paging.runtime)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
