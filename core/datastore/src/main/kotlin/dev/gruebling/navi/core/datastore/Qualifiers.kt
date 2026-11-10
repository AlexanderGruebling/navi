package dev.gruebling.navi.core.datastore

import javax.inject.Qualifier

/** Login data (separate file, so clearing settings never logs out). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SessionPreferences

/** User settings. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsPreferences
