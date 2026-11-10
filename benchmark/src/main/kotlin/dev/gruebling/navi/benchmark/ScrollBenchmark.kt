package dev.gruebling.navi.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Frame times while flinging Home and the album grid (111 covers, paged from Room). */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun albumGridNoProfile() = albumGrid(CompilationMode.None())

    @Test
    fun albumGridBaselineProfile() = albumGrid(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun homeNoProfile() = home(CompilationMode.None())

    @Test
    fun homeBaselineProfile() = home(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun albumGrid(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameMetric()),
        compilationMode = mode,
        // No startup mode: the setup opens the screen, the measured block only scrolls.
        // The setup kills the process first so every iteration starts on Home, whatever tab the
        // previous one left open. FrameMetric explains why FrameTimingMetric isn't used.
        startupMode = null,
        iterations = 5,
        setupBlock = {
            killProcess()
            startActivityAndWait()
            waitForHome()
            openAlbumGrid()
        },
    ) {
        flingAlbumGrid()
    }

    private fun home(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameMetric()),
        compilationMode = mode,
        // No startup mode: the setup opens the screen, the measured block only scrolls.
        // The setup kills the process first so every iteration starts on Home, whatever tab the
        // previous one left open. FrameMetric explains why FrameTimingMetric isn't used.
        startupMode = null,
        iterations = 5,
        setupBlock = {
            killProcess()
            startActivityAndWait()
            waitForHome()
        },
    ) {
        scrollHome()
    }
}
