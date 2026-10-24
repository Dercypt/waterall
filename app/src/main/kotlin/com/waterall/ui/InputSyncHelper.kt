package com.waterall.ui

/**
 * Pure helper for synchronizing numeric EditText inputs with StateFlow values.
 *
 * Prevents the cursor-reset bug where clearing an input field with backspace
 * emits an empty string (parsed as 0 by doAfterTextChanged), which causes
 * StateFlow collectors comparing `currentText?.toIntOrNull() != count` (null != 0)
 * to immediately overwrite the blank field with "0" and reset the cursor to index 0.
 */
object InputSyncHelper {

    /**
     * Determines whether a container count EditText needs to be updated with [newCount].
     *
     * Coalesces null, empty, or unparseable input to 0 so that if the user cleared the
     * field via backspace, the collector will see current (0) == newCount (0) and avoid
     * immediately repopulating the field with "0".
     */
    fun shouldUpdateContainerCount(currentText: CharSequence?, newCount: Int): Boolean {
        val current = currentText?.toString()?.toIntOrNull() ?: 0
        return current != newCount
    }

    /**
     * Determines whether the daily consumption EditText needs to be updated with [newConsumption].
     *
     * Coalesces null, empty, or unparseable input to 0.0.
     */
    fun shouldUpdateConsumption(currentText: CharSequence?, newConsumption: Double): Boolean {
        val current = currentText?.toString()?.toDoubleOrNull() ?: 0.0
        return current != newConsumption
    }
}
