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

    /**
     * Calculates the cursor selection offset that positions the cursor at the end of [text],
     * preventing cursor jumping to position 0 when EditText contents are refreshed.
     */
    fun calculateEndCursorPosition(text: CharSequence?): Int {
        return text?.length ?: 0
    }

    /**
     * Synchronizes a container count text input with [newCount] if an update is needed.
     * When updated, [onUpdate] is invoked with the string representation of [newCount]
     * and the target cursor index at the end of the text.
     *
     * @return true if an update was triggered, false otherwise.
     */
    fun syncContainerCount(
        currentText: CharSequence?,
        newCount: Int,
        onUpdate: (formattedText: String, cursorPosition: Int) -> Unit
    ): Boolean {
        if (shouldUpdateContainerCount(currentText, newCount)) {
            val text = newCount.toString()
            onUpdate(text, calculateEndCursorPosition(text))
            return true
        }
        return false
    }

    /**
     * Synchronizes a daily consumption text input with [newConsumption] and [formattedText] if an update is needed.
     * When updated, [onUpdate] is invoked with [formattedText] and the target cursor index at the end of the text.
     *
     * @return true if an update was triggered, false otherwise.
     */
    fun syncConsumption(
        currentText: CharSequence?,
        newConsumption: Double,
        formattedText: String,
        onUpdate: (formattedText: String, cursorPosition: Int) -> Unit
    ): Boolean {
        if (shouldUpdateConsumption(currentText, newConsumption)) {
            onUpdate(formattedText, calculateEndCursorPosition(formattedText))
            return true
        }
        return false
    }
}
