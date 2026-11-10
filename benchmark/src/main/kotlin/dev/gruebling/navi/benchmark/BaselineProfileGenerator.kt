package dev.gruebling.navi.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline profile: the code paths of startup and the main screens get compiled ahead of time on
 * install. Run with `./gradlew :app:generateReleaseBaselineProfile` (phone connected, benchmark build logged in).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE,
        // Startup gets its own profile too (used for the dex layout of the startup path).
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        waitForHome()
        scrollHome()
        openAlbumGrid()
        flingAlbumGrid()
        openAndCloseFullPlayer()
    }
}
