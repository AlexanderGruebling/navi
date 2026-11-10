package dev.gruebling.navi.benchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.TraceMetric
import androidx.benchmark.traceprocessor.TraceProcessor

/**
 * Same numbers as [androidx.benchmark.macro.FrameTimingMetric] (frameDurationCpuMs, frameOverrunMs),
 * without its `thread.name = 'RenderThread'` filter.
 *
 * Why: the process is already running when the measured trace starts, and on this phone the
 * on-device trace processor then often has no name for the RenderThread (thread.name is NULL),
 * so FrameTimingMetric fails with "Observed no renderthread slices" although the frames are there.
 * Here the render thread is any non-main thread of the app with `DrawFrame*` slices.
 */
@OptIn(ExperimentalMetricApi::class)
class FrameMetric : TraceMetric() {
    override fun getMeasurements(captureInfo: CaptureInfo, traceSession: TraceProcessor.Session): List<Measurement> {
        val app = """(process.name = '${captureInfo.targetPackageName}' OR process.name LIKE '${captureInfo.targetPackageName}:%')"""

        // Per vsync id: start of the UI thread's doFrame to the end of the last DrawFrame for it.
        val cpu = traceSession.query(
            """
            WITH ui AS (
                SELECT CAST(SUBSTR(slice.name, 23) AS INT) AS vsync, slice.ts AS ts
                FROM slice
                    JOIN thread_track ON slice.track_id = thread_track.id
                    JOIN thread USING(utid) JOIN process USING(upid)
                WHERE slice.name LIKE 'Choreographer#doFrame %'
                    AND slice.name NOT LIKE '%resynced%'
                    AND thread.tid = process.pid AND slice.dur > 0 AND $app
            ), rt AS (
                SELECT CAST(SUBSTR(slice.name, INSTR(slice.name, ' ') + 1) AS INT) AS vsync,
                    MAX(slice.ts + slice.dur) AS endTs
                FROM slice
                    JOIN thread_track ON slice.track_id = thread_track.id
                    JOIN thread USING(utid) JOIN process USING(upid)
                WHERE slice.name LIKE 'DrawFrame%' AND thread.tid != process.pid
                    AND slice.dur > 0 AND $app
                GROUP BY vsync
            )
            SELECT rt.endTs - MIN(ui.ts) AS dur FROM ui JOIN rt USING(vsync) GROUP BY vsync
            """.trimIndent(),
        ).map { it.long("dur") / 1_000_000.0 }.toList()

        // Actual frame end minus expected (deadline) end; negative = finished early.
        val overrun = traceSession.query(
            """
            SELECT (a.ts + a.dur) - (e.ts + e.dur) AS overrun
            FROM actual_frame_timeline_slice a
                JOIN expected_frame_timeline_slice e ON a.name = e.name AND a.upid = e.upid
                JOIN process ON a.upid = process.upid
            WHERE $app
            """.trimIndent(),
        ).map { it.long("overrun") / 1_000_000.0 }.toList()

        check(cpu.isNotEmpty()) { "No frames of ${captureInfo.targetPackageName} in the trace" }
        return buildList {
            add(Measurement("frameCount", cpu.size.toDouble()))
            add(Measurement("frameDurationCpuMs", cpu))
            if (overrun.isNotEmpty()) add(Measurement("frameOverrunMs", overrun))
        }
    }
}
