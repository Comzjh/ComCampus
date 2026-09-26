package com.xmu.course.data

import android.content.SharedPreferences

/** Reads a preference with its expected type while preserving malformed stored data for later repair. */
internal inline fun <T> SharedPreferences.readSafely(
    defaultValue: T,
    readValue: SharedPreferences.() -> T,
): T = try {
    readValue(this)
} catch (_: ClassCastException) {
    defaultValue
}
