import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        configureKotlinAndroid()
        extensions.configure<LibraryExtension> {
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            // Each library ships its own keep rules to the app's R8 run.
            defaultConfig.consumerProguardFiles("consumer-rules.pro")
        }
        // Modules without tests yet (screens only) shouldn't fail the build.
        tasks.withType(Test::class.java).configureEach {
            failOnNoDiscoveredTests.set(false)
            // Robolectric pokes into java.io.FileDescriptor, which JDK 17+ hides by default.
            jvmArgs(
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            )
        }
        dependencies {
            add("testImplementation", libs.lib("junit"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
            add("testImplementation", libs.lib("turbine"))
        }
    }
}
