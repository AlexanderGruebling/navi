package dev.gruebling.navi.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val PACKAGE = "dev.gruebling.navi"
private const val TIMEOUT_MS = 10_000L

/**
 * The app must be logged in (log in once by hand in the release / benchmark build: data survives reinstalls).
 * Elements are found by test tag, which the app exposes as resource id.
 */
fun MacrobenchmarkScope.waitForHome() {
    check(device.wait(Until.hasObject(By.res("home_list")), TIMEOUT_MS)) {
        "Home didn't appear. Is the benchmark build logged in?"
    }
}

fun MacrobenchmarkScope.scrollHome() {
    val list = device.findObject(By.res("home_list"))
    list.setGestureMargin(device.displayWidth / 5)
    list.fling(Direction.DOWN)
    device.waitForIdle()
    list.fling(Direction.UP)
    device.waitForIdle()
}

fun MacrobenchmarkScope.openAlbumGrid() {
    device.wait(Until.findObject(By.res("nav_library")), TIMEOUT_MS).click()
    // Uncompiled (CompilationMode.None) the first click on the tab is sometimes lost: retry once.
    repeat(2) {
        device.wait(Until.findObject(By.res("library_tab_albums")), TIMEOUT_MS)?.click()
        if (device.wait(Until.hasObject(By.res("library_albums")), TIMEOUT_MS / 2)) return
    }
    error("Album grid didn't appear")
}

fun MacrobenchmarkScope.flingAlbumGrid(times: Int = 3) {
    val grid = device.findObject(By.res("library_albums"))
    grid.setGestureMargin(device.displayWidth / 5)
    repeat(times) {
        grid.fling(Direction.DOWN)
        device.waitForIdle()
    }
    grid.fling(Direction.UP)
    device.waitForIdle()
}

/** Opens the full player from the mini player (only if something is queued) and closes it again. */
fun MacrobenchmarkScope.openAndCloseFullPlayer() {
    val mini = device.findObject(By.res("mini_player")) ?: return
    mini.click()
    if (device.wait(Until.hasObject(By.res("full_player")), TIMEOUT_MS)) {
        device.waitForIdle()
        device.pressBack()
        device.wait(Until.gone(By.res("full_player")), TIMEOUT_MS)
    }
}
