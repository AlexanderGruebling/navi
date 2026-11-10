package dev.gruebling.navi.core.data.testing

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.gruebling.navi.core.database.NaviDatabase
import kotlin.time.Clock
import kotlin.time.Instant

fun inMemoryDatabase(): NaviDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NaviDatabase::class.java)
        .allowMainThreadQueries()
        .build()

class FakeClock(var now: Instant = Instant.parse("2026-11-10T12:00:00Z")) : Clock {
    override fun now(): Instant = now
}
