package com.waterall.ui

import kotlinx.coroutines.CoroutineScope

/**
 * Common contract for UI screen delegates in BWD HydroTrack.
 *
 * Screen delegates decouple the view-binding, user interaction listeners,
 * and state-rendering logic of individual functional screens (Calendar, Reserves, Report)
 * from [MainActivity], which remains strictly responsible for navigation routing
 * and top-level Android lifecycle coordination.
 */
interface ViewDelegate {
    /**
     * Sets up views, adapters, and UI listeners.
     */
    fun setup()

    /**
     * Binds lifecycle-aware coroutines and StateFlow observers.
     */
    fun observeState(scope: CoroutineScope)

    /**
     * Toggles visibility of this delegate's view container.
     */
    fun setVisible(visible: Boolean)

    /**
     * Notified when this screen's tab becomes active.
     */
    fun onTabSelected() {}

    /**
     * Halts any active timers, background coroutines, or dialogs to prevent leaks.
     */
    fun cleanup() {}
}
