package com.waterall.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying [ViewDelegate] contract behavior and lifecycle coordination.
 */
class ViewDelegateTest {

    private class FakeViewDelegate : ViewDelegate {
        var isSetup = false
        var visibleState = false
        var tabSelectedCount = 0
        var isCleanedUp = false
        var stateObserved = false

        override fun setup() {
            isSetup = true
        }

        override fun observeState(scope: CoroutineScope) {
            stateObserved = true
        }

        override fun setVisible(visible: Boolean) {
            visibleState = visible
        }

        override fun onTabSelected() {
            tabSelectedCount++
        }

        override fun cleanup() {
            isCleanedUp = true
        }
    }

    @Test
    fun testLifecycleMethodsTriggerAsExpected() {
        val delegate = FakeViewDelegate()
        val testScope = CoroutineScope(Dispatchers.Unconfined + Job())

        assertFalse(delegate.isSetup)
        assertFalse(delegate.visibleState)
        assertFalse(delegate.stateObserved)
        assertEquals(0, delegate.tabSelectedCount)
        assertFalse(delegate.isCleanedUp)

        delegate.setup()
        assertTrue(delegate.isSetup)

        delegate.observeState(testScope)
        assertTrue(delegate.stateObserved)

        delegate.setVisible(true)
        assertTrue(delegate.visibleState)

        delegate.onTabSelected()
        assertEquals(1, delegate.tabSelectedCount)

        delegate.setVisible(false)
        assertFalse(delegate.visibleState)

        delegate.cleanup()
        assertTrue(delegate.isCleanedUp)
    }

    @Test
    fun testCompositeDelegateCoordination() {
        val calendarDelegate = FakeViewDelegate()
        val reservesDelegate = FakeViewDelegate()
        val reportDelegate = FakeViewDelegate()
        val delegates = listOf(calendarDelegate, reservesDelegate, reportDelegate)

        // Setup all
        delegates.forEach { it.setup() }
        assertTrue(delegates.all { it.isSetup })

        // Switch to reserves
        calendarDelegate.setVisible(false)
        reservesDelegate.setVisible(true)
        reportDelegate.setVisible(false)
        reservesDelegate.onTabSelected()

        assertFalse(calendarDelegate.visibleState)
        assertTrue(reservesDelegate.visibleState)
        assertFalse(reportDelegate.visibleState)
        assertEquals(0, calendarDelegate.tabSelectedCount)
        assertEquals(1, reservesDelegate.tabSelectedCount)
        assertEquals(0, reportDelegate.tabSelectedCount)

        // Cleanup all
        delegates.forEach { it.cleanup() }
        assertTrue(delegates.all { it.isCleanedUp })
    }

    @Test
    fun testSequentialTabTransitionsAcrossAllScreens() {
        val calendar = FakeViewDelegate()
        val reserves = FakeViewDelegate()
        val report = FakeViewDelegate()

        // Helper mimicking MainActivity.applyTab
        fun selectTab(tab: Tab) {
            calendar.setVisible(tab == Tab.CALENDAR)
            reserves.setVisible(tab == Tab.RESERVES)
            report.setVisible(tab == Tab.REPORT)
            when (tab) {
                Tab.CALENDAR -> calendar.onTabSelected()
                Tab.RESERVES -> reserves.onTabSelected()
                Tab.REPORT -> report.onTabSelected()
            }
        }

        // Start on CALENDAR
        selectTab(Tab.CALENDAR)
        assertTrue(calendar.visibleState)
        assertFalse(reserves.visibleState)
        assertFalse(report.visibleState)
        assertEquals(1, calendar.tabSelectedCount)
        assertEquals(0, reserves.tabSelectedCount)
        assertEquals(0, report.tabSelectedCount)

        // Switch to RESERVES
        selectTab(Tab.RESERVES)
        assertFalse(calendar.visibleState)
        assertTrue(reserves.visibleState)
        assertFalse(report.visibleState)
        assertEquals(1, calendar.tabSelectedCount)
        assertEquals(1, reserves.tabSelectedCount)
        assertEquals(0, report.tabSelectedCount)

        // Switch to REPORT
        selectTab(Tab.REPORT)
        assertFalse(calendar.visibleState)
        assertFalse(reserves.visibleState)
        assertTrue(report.visibleState)
        assertEquals(1, calendar.tabSelectedCount)
        assertEquals(1, reserves.tabSelectedCount)
        assertEquals(1, report.tabSelectedCount)

        // Back to CALENDAR
        selectTab(Tab.CALENDAR)
        assertTrue(calendar.visibleState)
        assertFalse(reserves.visibleState)
        assertFalse(report.visibleState)
        assertEquals(2, calendar.tabSelectedCount)
        assertEquals(1, reserves.tabSelectedCount)
        assertEquals(1, report.tabSelectedCount)
    }

    @Test
    fun testDefaultInterfaceImplementations() {
        val minimalDelegate = object : ViewDelegate {
            override fun setup() {}
            override fun observeState(scope: CoroutineScope) {}
            override fun setVisible(visible: Boolean) {}
        }

        // Verify default methods do not throw UnsupportedOperationException
        minimalDelegate.onTabSelected()
        minimalDelegate.cleanup()
    }
}
