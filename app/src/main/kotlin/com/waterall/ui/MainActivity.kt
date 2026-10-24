package com.waterall.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.waterall.R
import com.waterall.databinding.ActivityMainBinding
import com.waterall.domain.model.BarangayZone
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Main application controller for BWD HydroTrack.
 *
 * Responsibilities are strictly focused on top-level navigation routing,
 * process death restoration, and Android lifecycle coordination per AGENTS.md.
 * View binding and screen-specific business logic are cleanly delegated to:
 * - [CalendarViewDelegate] (Barangay Rotation Calendar, Countdown, and Consensus)
 * - [ReservesViewDelegate] (Container Inventory, Runout Buffer, and Conservation Targets)
 * - [ReportViewDelegate] (Crowdsourced Tap Flow & Pressure Verification)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var userPreferences: UserPreferences
    private lateinit var viewModel: MainViewModel
    private var uiCoroutineJob: Job? = null

    // Screen View Delegates
    private lateinit var calendarDelegate: CalendarViewDelegate
    private lateinit var reservesDelegate: ReservesViewDelegate
    private lateinit var reportDelegate: ReportViewDelegate

    // Mock Baguio Water District Rotation Data
    internal val zones: List<BarangayZone> = MainViewModel.DEFAULT_ZONES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this, MainViewModel.Factory)[MainViewModel::class.java]
        userPreferences = UserPreferences(this)

        // Hydrate from UserPreferences whenever ViewModel is freshly created.
        // We check !viewModel.isInitialized instead of savedInstanceState == null so that
        // when Android recreates the Activity after process death (where savedInstanceState != null
        // but ViewModelProvider returns a fresh zero-initialized ViewModel), user preferences are
        // properly restored rather than skipped and subsequently overwritten with zero defaults.
        if (!viewModel.isInitialized) {
            viewModel.hydrateFromPreferences(userPreferences, zones)
        }

        initDelegates()
        setupBottomNavigation()
    }

    private fun initDelegates() {
        calendarDelegate = CalendarViewDelegate(
            activity = this,
            binding = binding,
            viewModel = viewModel,
            userPreferences = userPreferences,
            zones = zones,
            onQuickReportClicked = { switchTab(Tab.REPORT) },
            onScheduleChanged = { reservesDelegate.autoPopulateScheduleHours() }
        )

        reservesDelegate = ReservesViewDelegate(
            activity = this,
            binding = binding,
            viewModel = viewModel,
            userPreferences = userPreferences
        )

        reportDelegate = ReportViewDelegate(
            activity = this,
            binding = binding,
            viewModel = viewModel,
            userPreferences = userPreferences,
            onReportSubmitted = {
                calendarDelegate.updateConsensusCard()
                switchTab(Tab.CALENDAR)
            }
        )

        calendarDelegate.setup()
        reservesDelegate.setup()
        reportDelegate.setup()
    }

    private fun setupBottomNavigation() {
        binding.navCalendar.setOnClickListener { switchTab(Tab.CALENDAR) }
        binding.navReserves.setOnClickListener { switchTab(Tab.RESERVES) }
        binding.navReport.setOnClickListener { switchTab(Tab.REPORT) }
        binding.btnBack.setOnClickListener { switchTab(Tab.CALENDAR) }
    }

    private fun switchTab(tab: Tab) {
        viewModel.setCurrentTab(tab)
    }

    private fun applyTab(tab: Tab) {
        calendarDelegate.setVisible(tab == Tab.CALENDAR)
        reservesDelegate.setVisible(tab == Tab.RESERVES)
        reportDelegate.setVisible(tab == Tab.REPORT)

        binding.btnBack.visibility = if (tab == Tab.CALENDAR) View.GONE else View.VISIBLE

        binding.tvScreenTitle.text = when (tab) {
            Tab.CALENDAR -> getString(R.string.title_hydro_track)
            Tab.RESERVES -> "Reserves"
            Tab.REPORT -> "Report"
        }

        when (tab) {
            Tab.CALENDAR -> calendarDelegate.onTabSelected()
            Tab.RESERVES -> reservesDelegate.onTabSelected()
            Tab.REPORT -> reportDelegate.onTabSelected()
        }

        // Highlight selected nav tab
        val activeColor = getColor(R.color.ink)
        val inactiveColor = getColor(R.color.ink_faint)

        binding.tvNavCalendar.setTextColor(if (tab == Tab.CALENDAR) activeColor else inactiveColor)
        binding.tvNavReserves.setTextColor(if (tab == Tab.RESERVES) activeColor else inactiveColor)
        binding.tvNavReport.setTextColor(if (tab == Tab.REPORT) activeColor else inactiveColor)
    }

    private fun observeViewModelState() {
        uiCoroutineJob?.cancel()
        uiCoroutineJob = lifecycleScope.launch {
            launch {
                viewModel.currentTab.collect { tab ->
                    applyTab(tab)
                }
            }
            calendarDelegate.observeState(this)
            reservesDelegate.observeState(this)
            reportDelegate.observeState(this)
        }
    }

    /**
     * Retains container counts, user mode, fill level, and daily consumption to lightweight SharedPreferences.
     */
    private fun persistUserPreferences() {
        if (!::userPreferences.isInitialized || !::viewModel.isInitialized || !viewModel.isInitialized) return
        reservesDelegate.persistPreferences()
        userPreferences.userMode = viewModel.userMode.value
    }

    /**
     * Safely unbinds all CountDownTimer instances, background coroutines, and delegates per PRINCIPLES.md:37-38.
     */
    private fun stopTimersAndCoroutines() {
        calendarDelegate.cleanup()
        reservesDelegate.cleanup()
        reportDelegate.cleanup()
        uiCoroutineJob?.cancel()
        uiCoroutineJob = null
    }

    override fun onStart() {
        super.onStart()
        observeViewModelState()
    }

    override fun onStop() {
        super.onStop()
        persistUserPreferences()
        stopTimersAndCoroutines()
    }

    override fun onDestroy() {
        super.onDestroy()
        persistUserPreferences()
        stopTimersAndCoroutines()
    }
}
