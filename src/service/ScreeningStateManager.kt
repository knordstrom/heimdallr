package com.heimdallr.service

import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates between CallScreenerService (which flags calls for screening)
 * and ScreeningInCallService (which answers and records them).
 *
 * In-process only — both services run in the same process, so no IPC needed.
 * State does not survive process death, but CallScreenerService always runs
 * before ScreeningInCallService, so the flag is always set first.
 */
object ScreeningStateManager {

    private val pending = ConcurrentHashMap.newKeySet<String>()

    fun markForScreening(phoneNumber: String) {
        pending.add(phoneNumber)
    }

    /** Returns true and removes the entry if this number was pending screening. */
    fun consumeScreeningRequest(phoneNumber: String): Boolean = pending.remove(phoneNumber)

    fun isPendingScreening(phoneNumber: String): Boolean = pending.contains(phoneNumber)
}
