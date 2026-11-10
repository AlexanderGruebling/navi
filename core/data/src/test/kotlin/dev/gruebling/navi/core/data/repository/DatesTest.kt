package dev.gruebling.navi.core.data.repository

import dev.gruebling.navi.core.data.mapping.toEpochMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DatesTest {
    @Test fun `parses Navidrome timestamps with nanoseconds and offset`() =
        assertEquals(1762677374585L, "2025-11-09T09:36:14.585789945+01:00".toEpochMillis())

    @Test fun `parses UTC timestamps`() = assertEquals(0L, "1970-01-01T00:00:00Z".toEpochMillis())

    @Test fun `garbage is null`() {
        assertNull("yesterday".toEpochMillis())
        assertNull((null as String?).toEpochMillis())
    }
}
