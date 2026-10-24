package com.waterall.ui

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
    }

    @Test
    fun testClearingConsumptionWithBackspaceDoesNotTriggerOverwriteWhenZero() {
        assertFalse(InputSyncHelper.shouldUpdateConsumption("", 0.0))
        assertFalse(InputSyncHelper.shouldUpdateConsumption(null, 0.0))
        assertFalse(InputSyncHelper.shouldUpdateConsumption("0", 0.0))
        assertFalse(InputSyncHelper.shouldUpdateConsumption("0.0", 0.0))
    }

    @Test
    fun testDifferentConsumptionTriggersUpdate() {
        assertTrue(InputSyncHelper.shouldUpdateConsumption("100.0", 150.0))
        assertTrue(InputSyncHelper.shouldUpdateConsumption("", 200.0))
        assertTrue(InputSyncHelper.shouldUpdateConsumption("50.0", 0.0))
    }
}
