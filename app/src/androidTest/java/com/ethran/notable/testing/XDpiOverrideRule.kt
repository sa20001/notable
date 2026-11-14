package com.ethran.notable.testing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Runs a test at a given display `xdpi`, from the instrumentation argument `testXdpi`:
 *
 * ```
 * ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.testXdpi=227
 * ```
 *
 * With no argument it does nothing, so a normal run is untouched.
 *
 * This exists because [com.ethran.notable.ui.convertPointsToPixel] converts a sheet's PDF points to
 * pixels with `displayMetrics.xdpi`, so every sheet is a different width in pixels on a different
 * screen, while `SCREEN_WIDTH`/`SCREEN_HEIGHT` are physical pixels and do not move with it. The
 * ratio between a sheet and the view over it — which is what a fit, a scroll clamp and a centring
 * are all derived from — therefore changes with the display, and a test that leans on one particular
 * ratio fails on another device. `adb shell wm density` changes `densityDpi` only, never `xdpi`, so
 * overriding the metrics here is the only way to exercise that on a single device.
 *
 * Tests should derive their viewport sizes from
 * [com.ethran.notable.testing.sheetWidthInPixels] rather than hardcoding pixels, and never assume a
 * sheet-to-view ratio holds at every density.
 */
class XDpiOverrideRule : TestWatcher() {
    private var previousXdpi = 0f
    private var previousYdpi = 0f
    private var overridden = false

    private val metrics get() =
        ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics

    override fun starting(description: Description) {
        val xdpi = InstrumentationRegistry.getArguments().getString("testXdpi")?.toFloatOrNull()
            ?: return
        previousXdpi = metrics.xdpi
        previousYdpi = metrics.ydpi
        metrics.xdpi = xdpi
        metrics.ydpi = xdpi
        overridden = true
    }

    override fun finished(description: Description) {
        if (!overridden) return
        metrics.xdpi = previousXdpi
        metrics.ydpi = previousYdpi
    }
}
