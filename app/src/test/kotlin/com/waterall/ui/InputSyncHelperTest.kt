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

    @Test
    fun testSequentialStepperInteractionsPreserveEndCursorAcrossMagnitudeBoundaries() {
        var currentText = "0"
        var currentCursor = 1

        val simulateStepperStep: (Int) -> Unit = { newCount ->
            InputSyncHelper.syncContainerCount(currentText, newCount) { text, cursor ->
                currentText = text
                currentCursor = cursor
            }
        }

        // Stepper: 0 -> 1 -> 9 -> 10 -> 99 -> 100
        simulateStepperStep(1)
        assertEquals("1", currentText)
        assertEquals(1, currentCursor)

        simulateStepperStep(9)
        assertEquals("9", currentText)
        assertEquals(1, currentCursor)

        simulateStepperStep(10)
        assertEquals("10", currentText)
        assertEquals(2, currentCursor)

        simulateStepperStep(99)
        assertEquals("99", currentText)
        assertEquals(2, currentCursor)

        simulateStepperStep(100)
        assertEquals("100", currentText)
        assertEquals(3, currentCursor)

        // Decrement stepper: 100 -> 99 -> 10 -> 9 -> 0
        simulateStepperStep(99)
        assertEquals("99", currentText)
        assertEquals(2, currentCursor)

        simulateStepperStep(10)
        assertEquals("10", currentText)
        assertEquals(2, currentCursor)

        simulateStepperStep(9)
        assertEquals("9", currentText)
        assertEquals(1, currentCursor)

        simulateStepperStep(0)
        assertEquals("0", currentText)
        assertEquals(1, currentCursor)
    }

    @Test
    fun testCursorPositionInvariantIsAlwaysEqualToFormattedTextLength() {
        val testCases = listOf(
            0 to "0",
            1 to "1",
            12 to "12",
            123 to "123",
            9999 to "9999"
        )

        for ((count, expectedString) in testCases) {
            var observedCursor = -1
            var observedText = ""
            InputSyncHelper.syncContainerCount("-1", count) { text, cursor ->
                observedText = text
                observedCursor = cursor
            }
            assertEquals(expectedString, observedText)
            assertEquals(observedText.length, observedCursor)
        }
    }

    @Test
    fun testShouldUpdateSupplyHoursWithBlankTextAndZeroAvoidsOverwriting() {
        assertFalse(InputSyncHelper.shouldUpdateSupplyHours("", 0.0))
        assertFalse(InputSyncHelper.shouldUpdateSupplyHours(null, 0.0))
        assertFalse(InputSyncHelper.shouldUpdateSupplyHours("   ", 0.0))
        assertFalse(InputSyncHelper.shouldUpdateSupplyHours("0", 0.0))
        assertFalse(InputSyncHelper.shouldUpdateSupplyHours("0.0", 0.0))
    }

    @Test
    fun testShouldUpdateSupplyHoursWithMatchingValueReturnsFalse() {
        assertFalse(InputSyncHelper.shouldUpdateSupplyHours("12.0", 12.0))
        assertFalse(InputSyncHelper.shouldUpdateSupplyHours("6.5", 6.5))
    }

    @Test
    fun testShouldUpdateSupplyHoursWithDivergentValueReturnsTrue() {
        assertTrue(InputSyncHelper.shouldUpdateSupplyHours("", 12.0))
        assertTrue(InputSyncHelper.shouldUpdateSupplyHours("10.0", 15.5))
        assertTrue(InputSyncHelper.shouldUpdateSupplyHours("5.0", 0.0))
    }

    @Test
    fun testSyncSupplyHoursInvokesCallbackWithCorrectEndCursor() {
        var observedText = ""
        var observedCursor = -1

        val updated = InputSyncHelper.syncSupplyHours("", 12.5, "12.5") { text, cursor ->
            observedText = text
            observedCursor = cursor
        }

        assertTrue(updated)
        assertEquals("12.5", observedText)
        assertEquals(4, observedCursor)

        // When values match, callback should not be invoked
        val noUpdate = InputSyncHelper.syncSupplyHours("12.5", 12.5, "12.5") { _, _ ->
            org.junit.Assert.fail("Should not invoke update when supply hours match")
        }
        assertFalse(noUpdate)
    }

    @Test
    fun testIsEnterOrDoneActionIdentifiesImeDoneAndSubmitActions() {
        // IME_ACTION_DONE = 6
        assertTrue("IME_ACTION_DONE should trigger cursor dismissal", InputSyncHelper.isEnterOrDoneAction(actionId = 6))
        // IME_ACTION_GO = 2, IME_ACTION_NEXT = 5, IME_ACTION_SEARCH = 3, IME_ACTION_SEND = 4
        assertTrue(InputSyncHelper.isEnterOrDoneAction(actionId = 2))
        assertTrue(InputSyncHelper.isEnterOrDoneAction(actionId = 5))
        assertTrue(InputSyncHelper.isEnterOrDoneAction(actionId = 3))
        assertTrue(InputSyncHelper.isEnterOrDoneAction(actionId = 4))

        // IME_ACTION_NONE = 1 or unspecified = 0
        assertFalse(InputSyncHelper.isEnterOrDoneAction(actionId = 1))
        assertFalse(InputSyncHelper.isEnterOrDoneAction(actionId = 0))
    }

    @Test
    fun testIsEnterOrDoneActionIdentifiesHardwareAndVirtualEnterKeys() {
        // KEYCODE_ENTER = 66, ACTION_DOWN = 0
        assertTrue("Enter keydown should trigger cursor dismissal", InputSyncHelper.isEnterOrDoneAction(actionId = 0, keyCode = 66, keyAction = 0))
        // Enter key with null action (unspecified)
        assertTrue(InputSyncHelper.isEnterOrDoneAction(actionId = 0, keyCode = 66, keyAction = null))
        // KEYCODE_NUMPAD_ENTER = 160
        assertTrue("Numpad Enter should trigger cursor dismissal", InputSyncHelper.isEnterOrDoneAction(actionId = 0, keyCode = 160, keyAction = 0))

        // Enter keyup (ACTION_UP = 1) should not trigger to avoid double-handling
        assertFalse(InputSyncHelper.isEnterOrDoneAction(actionId = 0, keyCode = 66, keyAction = 1))

        // Non-enter keycodes (e.g., BACK = 4, A = 29, DEL = 67)
        assertFalse(InputSyncHelper.isEnterOrDoneAction(actionId = 0, keyCode = 4, keyAction = 0))
        assertFalse(InputSyncHelper.isEnterOrDoneAction(actionId = 0, keyCode = 29, keyAction = 0))
        assertFalse(InputSyncHelper.isEnterOrDoneAction(actionId = 0, keyCode = 67, keyAction = 0))
    }

    @Test
    fun testIsEnterOrDoneActionForReportNotesSubmission() {
        // When typing in Note (optional) in Report screen, keyboard Done action (6) or Enter key (66) triggers dismiss
        assertTrue("IME Done action on notes should dismiss cursor", InputSyncHelper.isEnterOrDoneAction(actionId = 6))
        assertTrue("Hardware Enter on notes should dismiss cursor", InputSyncHelper.isEnterOrDoneAction(actionId = 0, keyCode = 66, keyAction = 0))
        assertTrue("Numpad Enter on notes should dismiss cursor", InputSyncHelper.isEnterOrDoneAction(actionId = 0, keyCode = 160, keyAction = 0))

        // Normal typing keys (e.g., Space = 62, Tab = 61, letters) must not trigger cursor dismissal
        assertFalse(InputSyncHelper.isEnterOrDoneAction(actionId = 0, keyCode = 62, keyAction = 0))
        assertFalse(InputSyncHelper.isEnterOrDoneAction(actionId = 0, keyCode = 61, keyAction = 0))
    }
}
