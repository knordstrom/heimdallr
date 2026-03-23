package com.callscreener.data

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for BlocklistRepository.
 *
 * Run with: ./gradlew test
 */
class BlocklistRepositoryTest {

    // Uses a real in-memory SharedPreferences via Context mock.
    // In a full project, use Robolectric or an instrumented test instead.

    private lateinit var prefs: SharedPreferences
    private lateinit var repo: BlocklistRepository

    @Before
    fun setUp() {
        val storage = mutableMapOf<String, Any?>()
        prefs = mockk(relaxed = true) {
            every { getStringSet(any(), any()) } answers {
                @Suppress("UNCHECKED_CAST")
                storage[firstArg()] as? Set<String> ?: secondArg()
            }
            every { edit() } returns mockk(relaxed = true) {
                every { putStringSet(any(), any()) } answers {
                    storage[firstArg()] = secondArg<Set<String>>()
                    this@mockk
                }
                every { apply() } returns Unit
            }
        }
        val context = mockk<Context> {
            every { getSharedPreferences(any(), any()) } returns prefs
        }
        repo = BlocklistRepository(context)
    }

    @Test
    fun `number not in blocklist returns false`() {
        assertFalse(repo.isBlocked("+12025551234"))
    }

    @Test
    fun `added number is detected as blocked`() {
        repo.addToBlocklist("+12025551234")
        assertTrue(repo.isBlocked("+12025551234"))
    }

    @Test
    fun `normalization strips formatting`() {
        repo.addToBlocklist("+1 (202) 555-1234")
        assertTrue(repo.isBlocked("12025551234"))
        assertTrue(repo.isBlocked("+1-202-555-1234"))
    }

    @Test
    fun `removed number no longer blocked`() {
        repo.addToBlocklist("+12025551234")
        repo.removeFromBlocklist("+12025551234")
        assertFalse(repo.isBlocked("+12025551234"))
    }

    @Test
    fun `soft block and hard block are independent`() {
        repo.addToBlocklist("+12025550001")
        repo.addToSoftBlocklist("+12025550002")

        assertTrue(repo.isBlocked("+12025550001"))
        assertFalse(repo.isSoftBlocked("+12025550001"))

        assertFalse(repo.isBlocked("+12025550002"))
        assertTrue(repo.isSoftBlocked("+12025550002"))
    }
}
