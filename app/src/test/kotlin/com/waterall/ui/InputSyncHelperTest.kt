package com.waterall.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying [InputSyncHelper] prevents cursor jumping and zero-overwrite
 * when users backspace or clear container count and daily consumption inputs.
 */
class InputSyncHelperTest {

    @Test
    fun testClearingContainerInputWithBackspaceDoesNotTriggerOverwriteWhenCountIsZero() {
        // When user backspaces "2" in etDrums200L, text becomes "" and StateFlow updates to 0.
        // Collector must NOT overwrite the blank field with "0".
        val shouldUpdate = InputSyncHelper.shouldUpdateContainerCount("", 0)
        assertFalse("Blank container field must not be overwritten when StateFlow count is 0", shouldUpdate)
    }

    @Test
    fun testNullContainerTextDoesNotTriggerOverwriteWhenCountIsZero() {
        val shouldUpdate = InputSyncHelper.shouldUpdateContainerCount(null, 0)
        assertFalse("Null container text must not trigger overwrite when count is 0", shouldUpdate)
    }

    @Test
    fun testWhitespaceContainerTextDoesNotTriggerOverwriteWhenCountIsZero() {
        assertFalse(InputSyncHelper.shouldUpdateContainerCount("   ", 0))
    }

    @Test
    fun testNonNumericContainerTextDoesNotTriggerOverwriteWhenCountIsZero() {
        assertFalse(InputSyncHelper.shouldUpdateContainerCount("abc", 0))
    }

    @Test
    fun testNegativeInputIsOverwrittenWhenClampedInStateFlow() {
        // If an invalid negative string is present, StateFlow clamped to 0 should trigger update to "0"
        assertTrue(InputSyncHelper.shouldUpdateContainerCount("-1", 0))
    }

    @Test
    fun testLeadingZeroEquivalentDoesNotTriggerUpdate() {
        // "05" parses to 5; if StateFlow is 5, no rewrite needed
        assertFalse(InputSyncHelper.shouldUpdateContainerCount("05", 5))
        assertFalse(InputSyncHelper.shouldUpdateContainerCount("00", 0))
    }

    @Test
    fun testDifferentContainerCountTriggersUpdate() {
        // Quick-adjust increment from 2 to 3
        assertTrue(InputSyncHelper.shouldUpdateContainerCount("2", 3))

        // Quick-adjust decrement from 1 to 0
        assertTrue(InputSyncHelper.shouldUpdateContainerCount("1", 0))

        // Initial populating or preset change from blank to 4
        assertTrue(InputSyncHelper.shouldUpdateContainerCount("", 4))
    }

    @Test
    fun testSameContainerCountDoesNotTriggerUpdate() {
        assertFalse(InputSyncHelper.shouldUpdateContainerCount("5", 5))
        assertFalse(InputSyncHelper.shouldUpdateContainerCount("0", 0))
        assertFalse(InputSyncHelper.shouldUpdateContainerCount("999", 999))
    }

    @Test
    fun testClearingConsumptionWithBackspaceDoesNotTriggerOverwriteWhenZero() {
        assertFalse(InputSyncHelper.shouldUpdateConsumption("", 0.0))
        assertFalse(InputSyncHelper.shouldUpdateConsumption(null, 0.0))
        assertFalse(InputSyncHelper.shouldUpdateConsumption("   ", 0.0))
        assertFalse(InputSyncHelper.shouldUpdateConsumption("0", 0.0))
        assertFalse(InputSyncHelper.shouldUpdateConsumption("0.0", 0.0))
    }

    @Test
    fun testEquivalentDecimalRepresentationDoesNotTriggerConsumptionUpdate() {
        // "150.00" parses to 150.0; collector should not trigger setText
        assertFalse(InputSyncHelper.shouldUpdateConsumption("150.00", 150.0))
    }

    @Test
    fun testDifferentConsumptionTriggersUpdate() {
        assertTrue(InputSyncHelper.shouldUpdateConsumption("100.0", 150.0))
        assertTrue(InputSyncHelper.shouldUpdateConsumption("", 200.0))
        assertTrue(InputSyncHelper.shouldUpdateConsumption("50.0", 0.0))
    }

    @Test
    fun testCalculateEndCursorPositionMatchesTextLength() {
        assertEquals(0, InputSyncHelper.calculateEndCursorPosition(null))
        assertEquals(0, InputSyncHelper.calculateEndCursorPosition(""))
        assertEquals(1, InputSyncHelper.calculateEndCursorPosition("0"))
        assertEquals(1, InputSyncHelper.calculateEndCursorPosition("5"))
        assertEquals(2, InputSyncHelper.calculateEndCursorPosition("25"))
        assertEquals(5, InputSyncHelper.calculateEndCursorPosition("150.0"))
    }

    @Test
    fun testSyncContainerCountInvokesCallbackWithEndCursorPositionWhenCountChanges() {
        var updatedText = ""
        var updatedCursor = -1

        val changed = InputSyncHelper.syncContainerCount("2", 3) { text, cursor ->
            updatedText = text
            updatedCursor = cursor
        }

        assertTrue(changed)
        assertEquals("3", updatedText)
        assertEquals(1, updatedCursor)
    }

    @Test
    fun testSyncContainerCountPreservesDoubleDigitCursorPosition() {
        var updatedText = ""
        var updatedCursor = -1

        val changed = InputSyncHelper.syncContainerCount("9", 10) { text, cursor ->
            updatedText = text
            updatedCursor = cursor
        }

        assertTrue(changed)
        assertEquals("10", updatedText)
        assertEquals(2, updatedCursor)
    }

    @Test
    fun testSyncContainerCountDoesNotInvokeCallbackWhenCountMatches() {
        var called = false

        val changed = InputSyncHelper.syncContainerCount("5", 5) { _, _ ->
            called = true
        }

        assertFalse(changed)
        assertFalse(called)
    }

    @Test
    fun testSyncContainerCountDoesNotOverwriteBlankWhenCountIsZero() {
        var called = false

        val changed = InputSyncHelper.syncContainerCount("", 0) { _, _ ->
            called = true
        }

        assertFalse(changed)
        assertFalse(called)
    }

    @Test
    fun testSyncConsumptionInvokesCallbackWithEndCursorPositionWhenChanged() {
        var updatedText = ""
        var updatedCursor = -1

        val changed = InputSyncHelper.syncConsumption("100.0", 150.0, "150") { text, cursor ->
            updatedText = text
            updatedCursor = cursor
        }

        assertTrue(changed)
        assertEquals("150", updatedText)
        assertEquals(3, updatedCursor)
    }

    @Test
    fun testSyncConsumptionDoesNotInvokeCallbackWhenUnchanged() {
        var called = false

        val changed = InputSyncHelper.syncConsumption("150.0", 150.0, "150") { _, _ ->
            called = true
        }

        assertFalse(changed)
        assertFalse(called)
    }
}
