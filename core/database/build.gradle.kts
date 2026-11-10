plugins {
    alias(libs.plugins.navi.android.library)
    alias(libs.plugins.navi.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "dev.gruebling.navi.core.database"
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.paging)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.core)
}
