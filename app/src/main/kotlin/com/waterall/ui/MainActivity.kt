package com.waterall.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.waterall.R
import com.waterall.databinding.ActivityMainBinding
import com.waterall.domain.model.BarangayZone
import com.waterall.ui.InputSyncHelper
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

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                getString(R.string.notification_permission_denied_toast),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

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

        HydroTrackNotificationHelper.ensureNotificationChannel(this)
        requestNotificationPermissionIfNeeded()

        initDelegates()
        setupBottomNavigation()
        setupBrandTagToggle()
        handleIntentExtras(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentExtras(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !HydroTrackNotificationHelper.hasNotificationPermission(this)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun handleIntentExtras(intent: Intent?) {
        if (intent == null) return
        val targetZone = StatusCommunicationFormatter.resolveTargetZone(
            zoneId = intent.getStringExtra(StatusCommunicationFormatter.EXTRA_ZONE_ID),
            zones = zones
        )
        if (targetZone != null && targetZone.id != viewModel.selectedZone.value.id) {
            viewModel.setSelectedZone(targetZone)
            userPreferences.selectedZoneId = targetZone.id
        }

        val targetTab = StatusCommunicationFormatter.resolveTargetTab(
            rawTabName = intent.getStringExtra(StatusCommunicationFormatter.EXTRA_TARGET_TAB)
        )
        if (targetTab != null) {
            switchTab(targetTab)
        }
    }

    private fun setupBrandTagToggle() {
        binding.tvBrandSdg.setOnClickListener {
            val currentNight = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val nextMode = if (currentNight == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            } else {
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            }
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(nextMode)
        }
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

        binding.ivNavCalendar.setColorFilter(if (tab == Tab.CALENDAR) activeColor else inactiveColor)
        binding.ivNavReserves.setColorFilter(if (tab == Tab.RESERVES) activeColor else inactiveColor)
        binding.ivNavReport.setColorFilter(if (tab == Tab.REPORT) activeColor else inactiveColor)
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
     * Retains container counts, fill level, and daily consumption to lightweight SharedPreferences.
     */
    private fun persistUserPreferences() {
        if (!::userPreferences.isInitialized || !::viewModel.isInitialized || !viewModel.isInitialized) return
        reservesDelegate.persistPreferences()
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

    /**
     * Synchronizes a container count [EditText] with [count] using [InputSyncHelper.shouldUpdateContainerCount]
     * and sets cursor position to the end of the text to prevent cursor jump/resets.
     */
    internal fun updateContainerCountUi(editText: EditText, count: Int) {
        if (InputSyncHelper.shouldUpdateContainerCount(editText.text, count)) {
            editText.setText(count.toString())
            editText.setSelection(editText.text.length)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        persistUserPreferences()
        stopTimersAndCoroutines()
    }
}
