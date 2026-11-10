package dev.gruebling.navi.core.data.mapping

import kotlin.time.Instant

/** Navidrome sends ISO-8601 with offset and nanoseconds ("2026-11-09T09:36:14.585789945+01:00"). Null if unparseable. */
internal fun String?.toEpochMillis(): Long? = this?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }

internal fun Long?.toInstant(): Instant? = this?.let(Instant::fromEpochMilliseconds)
