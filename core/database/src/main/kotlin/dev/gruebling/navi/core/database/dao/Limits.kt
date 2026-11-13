package dev.gruebling.navi.core.database.dao

/** Most ids per `IN (…)` query: SQLite before 3.32 (Android 11 and older) allows 999 variables per statement. */
internal const val MAX_IN_LIST = 900
