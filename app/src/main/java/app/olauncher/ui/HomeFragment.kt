package app.olauncher.ui

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.DragEvent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.BulletType
import app.olauncher.data.Constants
import app.olauncher.data.JournalEntry
import app.olauncher.data.JournalLog
import app.olauncher.data.JournalPages
import app.olauncher.data.JournalStore
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentHomeBinding
import app.olauncher.helper.CalendarSyncHelper
import app.olauncher.helper.appUsagePermissionGranted
import app.olauncher.helper.dpToPx
import app.olauncher.helper.expandNotificationDrawer
import app.olauncher.helper.getAppIconDrawable
import app.olauncher.helper.getChangedAppTheme
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.getShortcutIconDrawable
import app.olauncher.helper.getUserHandleFromString
import app.olauncher.helper.isPackageInstalled
import app.olauncher.helper.openAlarmApp
import app.olauncher.helper.openCalendar
import app.olauncher.helper.openCameraApp
import app.olauncher.helper.openDialerApp
import app.olauncher.helper.openSearch
import app.olauncher.helper.setBlackAndWhite
import app.olauncher.helper.setPlainWallpaperByTheme
import app.olauncher.helper.showToast
import app.olauncher.listener.OnSwipeTouchListener
import app.olauncher.listener.ViewSwipeTouchListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : BaseFragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager
    private lateinit var homeAppViews: List<ImageView>
    private lateinit var journalStore: JournalStore
    private var journalPagerAdapter: JournalPagerAdapter? = null
    /** Journal entry awaiting calendar sync after the permission prompt. */
    private var pendingCalendarSyncEntryId: String? = null

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val calendarPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.READ_CALENDAR] == true &&
            grants[Manifest.permission.WRITE_CALENDAR] == true
        val entryId = pendingCalendarSyncEntryId
        pendingCalendarSyncEntryId = null
        if (entryId == null) return@registerForActivityResult
        val entry = journalStore.getById(entryId) ?: return@registerForActivityResult
        if (granted) {
            syncEntryToCalendar(entry)
        } else {
            requireContext().showToast(R.string.event_calendar_permission_needed)
        }
    }

    private val homeAppViewIds = listOf(
        R.id.homeApp1, R.id.homeApp2, R.id.homeApp3, R.id.homeApp4, R.id.homeApp5,
        R.id.homeApp6, R.id.homeApp7, R.id.homeApp8, R.id.homeApp9, R.id.homeApp10,
        R.id.homeApp11, R.id.homeApp12, R.id.homeApp13, R.id.homeApp14, R.id.homeApp15,
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        deviceManager = context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        homeAppViews = homeAppViewIds.mapIndexed { index, id ->
            binding.root.findViewById<ImageView>(id).also { it.tag = (index + 1).toString() }
        }
        journalStore = JournalStore(requireContext())
        journalStore.ensureSampleData()
        initJournalPager()
        initObservers()
        setHomeAlignment(prefs.homeAlignment)
        initSwipeTouchListener()
        initClickListeners()
        initHomeAppsSheet()
        initHomeAppDragListeners()
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (binding.appDrawerOverlay.isVisible) {
                        closeAppDrawerOverlay()
                    } else if (binding.journalPager.currentItem != JournalPages.DAILY) {
                        binding.journalPager.setCurrentItem(JournalPages.DAILY, true)
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        populateHomeScreen(false)
        refreshJournal()
        viewModel.isOlauncherDefault()
        if (prefs.showStatusBar) showStatusBar()
        else hideStatusBar()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.lock -> {}
            // Home button for recents feature disabled
            // R.id.recents -> {}
            R.id.clock -> openClockApp()
            R.id.date -> openCalendarApp()
            R.id.setDefaultLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.tvScreenTime -> openScreenTimeDigitalWellbeing()
            R.id.addBulletButton -> showAddBulletDialog()

            else -> {
                try { // Launch app
                    val appLocation = view.tag.toString().toInt()
                    homeAppClicked(appLocation)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun openClockApp() {
        if (prefs.clockAppPackage.isBlank())
            openAlarmApp(requireContext())
        else
            launchApp(
                "Clock",
                prefs.clockAppPackage,
                prefs.clockAppClassName,
                prefs.clockAppUser
            )
    }

    private fun openCalendarApp() {
        if (prefs.calendarAppPackage.isBlank())
            openCalendar(requireContext())
        else
            launchApp(
                "Calendar",
                prefs.calendarAppPackage,
                prefs.calendarAppClassName,
                prefs.calendarAppUser
            )
    }

    override fun onLongClick(view: View): Boolean {
        when {
            view.id in homeAppViewIds -> {
                val location = view.tag.toString().toInt()
                if (location == Constants.HOME_DRAWER_SLOT) {
                    openAppDrawerOverlay(Constants.FLAG_LAUNCH_APP)
                } else {
                    showAppList(location, prefs.getAppName(location).isNotEmpty(), true)
                }
            }
            view.id == R.id.clock -> {
                showAppList(Constants.FLAG_SET_CLOCK_APP)
                prefs.clockAppPackage = ""
                prefs.clockAppClassName = ""
                prefs.clockAppUser = ""
            }

            view.id == R.id.date -> {
                showAppList(Constants.FLAG_SET_CALENDAR_APP)
                prefs.calendarAppPackage = ""
                prefs.calendarAppClassName = ""
                prefs.calendarAppUser = ""
            }

            view.id == R.id.tvScreenTime -> {
                showAppList(Constants.FLAG_SET_SCREEN_TIME_APP)
                prefs.screenTimeAppPackage = ""
                prefs.screenTimeAppClassName = ""
                prefs.screenTimeAppUser = ""
            }

            view.id == R.id.setDefaultLauncher -> {
                prefs.hideSetDefaultLauncher = true
                binding.setDefaultLauncher.visibility = View.GONE
                if (viewModel.isOlauncherDefault.value != true) {
                    requireContext().showToast(R.string.set_as_default_launcher)
                    findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                }
            }
        }
        return true
    }

    private fun initObservers() {
        if (prefs.firstSettingsOpen) {
            binding.firstRunTips.visibility = View.VISIBLE
            binding.setDefaultLauncher.visibility = View.GONE
        } else binding.firstRunTips.visibility = View.GONE

        viewModel.refreshHome.observe(viewLifecycleOwner) {
            populateHomeScreen(it)
        }
        viewModel.isOlauncherDefault.observe(viewLifecycleOwner, Observer {
            if (it != true) {
                if (prefs.dailyWallpaper && prefs.appTheme == AppCompatDelegate.MODE_NIGHT_YES) {
                    prefs.dailyWallpaper = false
                    viewModel.cancelWallpaperWorker()
                }
                prefs.homeBottomAlignment = false
                setHomeAlignment()
            }
            if (binding.firstRunTips.isVisible) return@Observer
            binding.setDefaultLauncher.isVisible = it.not() && prefs.hideSetDefaultLauncher.not()
        })
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) {
            setHomeAlignment(it)
        }
        viewModel.toggleDateTime.observe(viewLifecycleOwner) {
            populateDateTime()
        }
        viewModel.screenTimeValue.observe(viewLifecycleOwner) {
            it?.let { binding.tvScreenTime.text = it }
        }
        // Home button for recents feature disabled
        // viewModel.showRecentApps.observe(viewLifecycleOwner) {
        //     binding.recents.performClick()
        // }
    }

    private fun initSwipeTouchListener() {
        val context = requireContext()
        binding.mainLayout.setOnTouchListener(getSwipeGestureListener(context))
        homeAppViews.forEach { appView ->
            appView.setOnTouchListener(getViewSwipeTouchListener(context, appView))
        }
    }

    private fun initClickListeners() {
        binding.lock.setOnClickListener(this)
        // Home button for recents feature disabled
        // binding.recents.setOnClickListener(this)
        binding.clock.setOnClickListener(this)
        binding.date.setOnClickListener(this)
        binding.clock.setOnLongClickListener(this)
        binding.date.setOnLongClickListener(this)
        binding.setDefaultLauncher.setOnClickListener(this)
        binding.setDefaultLauncher.setOnLongClickListener(this)
        binding.tvScreenTime.setOnClickListener(this)
        binding.tvScreenTime.setOnLongClickListener(this)
        binding.addBulletButton.setOnClickListener(this)

        // These fire only on d-pad/keyboard events; touch is consumed by ViewSwipeTouchListener
        homeAppViews.forEach { appView ->
            appView.setOnClickListener(this)
            appView.setOnLongClickListener(this)
        }
    }

    private fun initJournalPager() {
        val adapter = JournalPagerAdapter(
            store = journalStore,
            onIndex = { showIndexDialog() },
            onToggle = { entry -> toggleJournalEntry(entry) },
            onLongPress = { entry -> deleteJournalEntry(entry) },
        )
        journalPagerAdapter = adapter
        binding.journalPager.adapter = adapter
        binding.journalPager.setCurrentItem(JournalPages.DAILY, false)
        binding.journalPager.offscreenPageLimit = 1
    }

    private fun refreshJournal() {
        journalPagerAdapter?.refresh()
    }

    private fun showIndexDialog() {
        IndexDialog.show(requireContext()) { page ->
            binding.journalPager.setCurrentItem(page, true)
        }
    }

    private fun showAddBulletDialog() {
        AddBulletDialog.show(requireContext()) { text, type, priority ->
            val page = binding.journalPager.currentItem
            val (log, dateKey) = when (page) {
                JournalPages.MONTHLY -> JournalLog.MONTHLY to journalStore.todayKey()
                JournalPages.FUTURE -> JournalLog.FUTURE to journalStore.futureMonthKeys(1).first()
                else -> JournalLog.DAILY to journalStore.todayKey()
            }
            val entry = journalStore.add(text, type, log, dateKey, priority)
            refreshJournal()
            if (type == BulletType.EVENT) {
                requestCalendarSync(entry)
            }
        }
    }

    private fun requestCalendarSync(entry: JournalEntry) {
        if (CalendarSyncHelper.hasCalendarPermissions(requireContext())) {
            syncEntryToCalendar(entry)
            return
        }
        pendingCalendarSyncEntryId = entry.id
        calendarPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR,
            )
        )
    }

    private fun syncEntryToCalendar(entry: JournalEntry) {
        val eventId = CalendarSyncHelper.insertEvent(requireContext(), entry)
        if (eventId != null) {
            journalStore.setCalendarEventId(entry.id, eventId)
            requireContext().showToast(R.string.event_synced_to_calendar)
        } else {
            requireContext().showToast(R.string.event_calendar_sync_failed)
        }
    }

    private fun toggleJournalEntry(entry: JournalEntry) {
        if (entry.type == BulletType.TASK) {
            journalStore.toggleCompleted(entry.id)
            refreshJournal()
        }
    }

    private fun deleteJournalEntry(entry: JournalEntry) {
        if (entry.type == BulletType.EVENT) {
            CalendarSyncHelper.deleteEvent(requireContext(), entry.calendarEventId)
        }
        journalStore.delete(entry.id)
        requireContext().showToast(R.string.entry_deleted)
        refreshJournal()
    }

    private fun setHomeAlignment(horizontalGravity: Int = prefs.homeAlignment) {
        binding.dateTimeLayout.gravity = horizontalGravity
    }

    private fun populateDateTime() {
        binding.dateTimeLayout.isVisible = prefs.dateTimeVisibility != Constants.DateTime.OFF
        binding.clock.isVisible = Constants.DateTime.isTimeVisible(prefs.dateTimeVisibility)
        binding.date.isVisible = Constants.DateTime.isDateVisible(prefs.dateTimeVisibility)

//        var dateText = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
        val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        var dateText = dateFormat.format(Date())

        if (!prefs.showStatusBar) {
            val battery = (requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (battery > 0)
                dateText = getString(R.string.day_battery, dateText, battery)
        }
        binding.date.text = dateText.replace(".,", ",")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun populateScreenTime() {
        if (requireContext().appUsagePermissionGranted().not()) return

        viewModel.getTodaysScreenTime()
        binding.tvScreenTime.visibility = View.VISIBLE

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val horizontalMargin = if (isLandscape) 64.dpToPx() else 10.dpToPx()
        val marginTop = if (isLandscape) {
            if (prefs.dateTimeVisibility == Constants.DateTime.DATE_ONLY) 36.dpToPx() else 56.dpToPx()
        } else {
            if (prefs.dateTimeVisibility == Constants.DateTime.DATE_ONLY) 45.dpToPx() else 72.dpToPx()
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = marginTop
            marginStart = horizontalMargin
            marginEnd = horizontalMargin
            gravity = if (prefs.homeAlignment == Gravity.END) Gravity.START else Gravity.END
        }
        binding.tvScreenTime.layoutParams = params
        binding.tvScreenTime.setPadding(10.dpToPx())
    }

    private fun populateHomeScreen(appCountUpdated: Boolean) {
        if (appCountUpdated) hideHomeApps()
        populateDateTime()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            populateScreenTime()

        val homeAppsNum = prefs.homeAppsNum
        binding.homeAppsBottomSheet.isVisible = homeAppsNum > 0
        if (homeAppsNum == 0) {
            closeAppDrawerOverlay()
            updateAddButtonMargin()
            return
        }

        // Fill all 15 slots; collapsed sheet then hides rows 2–3.
        for (location in 1..Constants.MAX_HOME_APPS) {
            val appView = homeAppViews[location - 1]
            if (location == Constants.HOME_DRAWER_SLOT) {
                showDrawerSlotIcon(appView)
            } else if (!setHomeAppIcon(
                    appView,
                    prefs.getAppName(location),
                    prefs.getAppPackage(location),
                    prefs.getAppActivityClassName(location),
                    prefs.getAppUser(location),
                    prefs.getIsShortcut(location),
                    prefs.getShortcutId(location)
                )
            ) {
                prefs.clearHomeApp(location)
            }
        }
        applyHomeAppsSheetExpanded(prefs.homeAppsSheetExpanded)
    }

    private fun initHomeAppsSheet() {
        val handleGestures = object : OnSwipeTouchListener(requireContext()) {
            override fun onSwipeUp() {
                super.onSwipeUp()
                if (!prefs.homeAppsSheetExpanded) setHomeAppsSheetExpanded(true)
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                if (prefs.homeAppsSheetExpanded) setHomeAppsSheetExpanded(false)
            }

            override fun onClick() {
                super.onClick()
                setHomeAppsSheetExpanded(!prefs.homeAppsSheetExpanded)
            }
        }
        binding.homeAppsSheetHandleArea.setOnTouchListener(handleGestures)
    }

    private fun setHomeAppsSheetExpanded(expanded: Boolean) {
        if (prefs.homeAppsNum <= 0) return
        prefs.homeAppsSheetExpanded = expanded
        applyHomeAppsSheetExpanded(expanded)
    }

    private fun applyHomeAppsSheetExpanded(expanded: Boolean) {
        val visibleCount = if (expanded) Constants.MAX_HOME_APPS else Constants.HOME_APPS_COLLAPSED_COUNT
        homeAppViews.forEachIndexed { index, appView ->
            appView.isVisible = index < visibleCount
        }
        binding.homeAppsSheetHandleArea.contentDescription = getString(
            if (expanded) R.string.collapse_apps_sheet else R.string.expand_apps_sheet
        )
        binding.homeAppsBottomSheet.post {
            updateDrawerOverlayPadding()
            updateAddButtonMargin()
        }
    }

    private fun updateAddButtonMargin() {
        val sheetVisible = binding.homeAppsBottomSheet.isVisible
        val sheetHeight = if (sheetVisible) binding.homeAppsBottomSheet.height else 0
        val fabMargin = if (sheetVisible) {
            (if (sheetHeight > 0) sheetHeight else collapsedSheetFallbackMargin()) + 12.dpToPx()
        } else {
            48.dpToPx()
        }
        val pagerMargin = if (sheetVisible) {
            (if (sheetHeight > 0) sheetHeight else collapsedSheetFallbackMargin()) + 24.dpToPx()
        } else {
            72.dpToPx()
        }
        val params = binding.addBulletButton.layoutParams as FrameLayout.LayoutParams
        params.bottomMargin = fabMargin
        binding.addBulletButton.layoutParams = params
        val pagerParams = binding.journalPager.layoutParams as FrameLayout.LayoutParams
        pagerParams.bottomMargin = pagerMargin
        val topMargin = if (binding.dateTimeLayout.isVisible) {
            if (binding.clock.isVisible) 140.dpToPx() else 100.dpToPx()
        } else {
            48.dpToPx()
        }
        pagerParams.topMargin = topMargin
        binding.journalPager.layoutParams = pagerParams
    }

    private fun collapsedSheetFallbackMargin(): Int =
        if (prefs.homeAppsSheetExpanded) 240.dpToPx() else 110.dpToPx()

    private fun showDrawerSlotIcon(imageView: ImageView) {
        imageView.setImageResource(R.drawable.ic_apps_drawer)
        imageView.setBlackAndWhite(false)
        imageView.imageTintList = android.content.res.ColorStateList.valueOf(
            requireContext().getColorFromAttr(R.attr.primaryColor)
        )
        imageView.contentDescription = getString(R.string.app_drawer)
    }

    private fun setHomeAppIcon(
        imageView: ImageView,
        appName: String,
        packageName: String,
        activityClassName: String?,
        userString: String,
        isShortcut: Boolean,
        shortcutId: String?,
    ): Boolean {
        val userHandle = getUserHandleFromString(requireContext(), userString)
        imageView.contentDescription = appName.ifBlank { getString(R.string.app) }
        imageView.imageTintList = null

        if (isShortcut) {
            val launcherApps = requireContext().getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            }

            try {
                val shortcuts = launcherApps.getShortcuts(query, userHandle)
                if (shortcuts?.any { it.id == shortcutId } == true) {
                    val icon = requireContext().getShortcutIconDrawable(
                        packageName,
                        shortcutId.orEmpty(),
                        userHandle
                    )
                    if (icon != null) {
                        imageView.setImageDrawable(icon)
                        imageView.setBlackAndWhite(true)
                    } else {
                        showEmptyHomeAppSlot(imageView)
                    }
                    return true
                }
                showEmptyHomeAppSlot(imageView)
                return false
            } catch (e: Exception) {
                e.printStackTrace()
                showEmptyHomeAppSlot(imageView)
                return false
            }
        }

        if (packageName.isNotBlank() && isPackageInstalled(requireContext(), packageName, userString)) {
            val icon = requireContext().getAppIconDrawable(
                packageName,
                userHandle,
                activityClassName,
                prefs.iconPackPackage,
            )
            if (icon != null) {
                imageView.setImageDrawable(icon)
                imageView.setBlackAndWhite(true)
            } else {
                showEmptyHomeAppSlot(imageView)
            }
            return true
        }

        showEmptyHomeAppSlot(imageView)
        return packageName.isBlank()
    }

    private fun showEmptyHomeAppSlot(imageView: ImageView) {
        imageView.imageTintList = null
        imageView.setImageResource(R.drawable.ic_home_app_empty)
        imageView.setBlackAndWhite(false)
        imageView.contentDescription = getString(R.string.app)
    }

    private fun hideHomeApps() {
        homeAppViews.forEach {
            it.visibility = View.GONE
            it.setImageDrawable(null)
        }
        binding.homeAppsBottomSheet.isVisible = false
        updateAddButtonMargin()
    }

    private fun launchAppOrShortcut(
        appName: String,
        packageName: String,
        activityClassName: String?,
        shortcutId: String?,
        isShortcut: Boolean,
        userString: String,
        fallback: (() -> Unit)? = null,
    ) {
        if (appName.isEmpty()) {
            showLongPressToast()
            return
        }
        if (isShortcut && !shortcutId.isNullOrEmpty()) {
            launchShortcut(
                packageName = packageName,
                shortcutId = shortcutId,
                shortcutLabel = appName,
                userString = userString
            )
        } else if (packageName.isNotEmpty()) {
            launchApp(
                appName = appName,
                packageName = packageName,
                activityClassName = activityClassName,
                userString = userString
            )
        } else {
            fallback?.invoke()
        }
    }

    private fun launchShortcut(shortcutId: String, packageName: String, shortcutLabel: String, userString: String) {
        viewModel.selectedApp(
            AppModel.PinnedShortcut(
                shortcutId = shortcutId,
                appLabel = shortcutLabel,
                user = getUserHandleFromString(requireContext(), userString),
                key = null,
                appPackage = packageName,
                isNew = false,
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun launchApp(appName: String, packageName: String, activityClassName: String?, userString: String) {
        viewModel.selectedApp(
            AppModel.App(
                appLabel = appName,
                key = null,
                appPackage = packageName,
                activityClassName = activityClassName,
                isNew = false,
                user = getUserHandleFromString(requireContext(), userString)
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun homeAppClicked(location: Int) {
        if (location == Constants.HOME_DRAWER_SLOT) {
            openAppDrawerOverlay(Constants.FLAG_LAUNCH_APP)
            return
        }
        launchAppOrShortcut(
            appName = prefs.getAppName(location),
            packageName = prefs.getAppPackage(location),
            activityClassName = prefs.getAppActivityClassName(location),
            shortcutId = prefs.getShortcutId(location),
            isShortcut = prefs.getIsShortcut(location),
            userString = prefs.getAppUser(location)
        )
    }

    private fun openSwipeRightApp() {
        if (!prefs.swipeRightEnabled) return
        launchAppOrShortcut(
            appName = prefs.appNameSwipeRight,
            packageName = prefs.appPackageSwipeRight,
            activityClassName = prefs.appActivityClassNameRight,
            shortcutId = prefs.shortcutIdSwipeRight,
            isShortcut = prefs.isShortcutSwipeRight,
            userString = prefs.appUserSwipeRight,
            fallback = { openDialerApp(requireContext()) }
        )
    }

    private fun openSwipeLeftApp() {
        if (!prefs.swipeLeftEnabled) return
        launchAppOrShortcut(
            appName = prefs.appNameSwipeLeft,
            packageName = prefs.appPackageSwipeLeft,
            activityClassName = prefs.appActivityClassNameSwipeLeft,
            shortcutId = prefs.shortcutIdSwipeLeft,
            isShortcut = prefs.isShortcutSwipeLeft,
            userString = prefs.appUserSwipeLeft,
            fallback = { openCameraApp(requireContext()) }
        )
    }

    private fun showAppList(flag: Int, rename: Boolean = false, includeHiddenApps: Boolean = false) {
        // Clock/calendar/screen-time and settings still use the nav drawer.
        // Home grid launch + home-slot assignment use the overlay so the dock stays droppable.
        if (flag == Constants.FLAG_LAUNCH_APP || flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_HOME_APP_15) {
            openAppDrawerOverlay(flag, rename, includeHiddenApps)
            return
        }
        viewModel.getAppList(includeHiddenApps)
        try {
            findNavController().navigate(
                R.id.action_mainFragment_to_appListFragment,
                bundleOf(
                    Constants.Key.FLAG to flag,
                    Constants.Key.RENAME to rename
                )
            )
        } catch (e: Exception) {
            findNavController().navigate(
                R.id.appListFragment,
                bundleOf(
                    Constants.Key.FLAG to flag,
                    Constants.Key.RENAME to rename
                )
            )
            e.printStackTrace()
        }
    }

    fun openAppDrawerOverlay(
        flag: Int = Constants.FLAG_LAUNCH_APP,
        rename: Boolean = false,
        includeHiddenApps: Boolean = false,
    ) {
        viewModel.getAppList(includeHiddenApps)
        val drawer = AppDrawerFragment().apply {
            arguments = bundleOf(
                Constants.Key.FLAG to flag,
                Constants.Key.RENAME to rename,
                Constants.Key.OVERLAY to true,
            )
        }
        childFragmentManager.beginTransaction()
            .replace(R.id.appDrawerOverlay, drawer, "home_app_drawer")
            .commitAllowingStateLoss()
        binding.appDrawerOverlay.isVisible = true
        // Expand so all dock slots are available as drop targets.
        if (!prefs.homeAppsSheetExpanded) setHomeAppsSheetExpanded(true)
        updateDrawerOverlayPadding()
    }

    fun closeAppDrawerOverlay() {
        val existing = childFragmentManager.findFragmentByTag("home_app_drawer")
        if (existing != null) {
            childFragmentManager.beginTransaction()
                .remove(existing)
                .commitAllowingStateLoss()
        }
        binding.appDrawerOverlay.isVisible = false
    }

    private fun updateDrawerOverlayPadding() {
        val sheetHeight = binding.homeAppsBottomSheet.height
        if (sheetHeight > 0) {
            binding.appDrawerOverlay.setPadding(0, 0, 0, sheetHeight)
        }
    }

    private fun initHomeAppDragListeners() {
        homeAppViews.forEach { appView ->
            appView.setOnDragListener { view, event ->
                val location = view.tag?.toString()?.toIntOrNull() ?: return@setOnDragListener false
                if (location == Constants.HOME_DRAWER_SLOT) return@setOnDragListener false
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED ->
                        event.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true

                    DragEvent.ACTION_DRAG_ENTERED -> {
                        view.alpha = 0.45f
                        true
                    }

                    DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                        view.alpha = 1f
                        true
                    }

                    DragEvent.ACTION_DROP -> {
                        view.alpha = 1f
                        val appModel = event.localState as? AppModel ?: return@setOnDragListener false
                        if (appModel is AppModel.PrivateSpaceHeader) return@setOnDragListener false
                        viewModel.selectedApp(appModel, location)
                        closeAppDrawerOverlay()
                        true
                    }

                    else -> true
                }
            }
        }
    }

    private fun swipeDownAction() {
        when (prefs.swipeDownAction) {
            Constants.SwipeDownAction.SEARCH -> openSearch(requireContext())
            else -> expandNotificationDrawer(requireContext())
        }
    }

    private fun lockPhone() {
        requireActivity().runOnUiThread {
            try {
                deviceManager.lockNow()
            } catch (e: SecurityException) {
                requireContext().showToast(getString(R.string.please_turn_on_double_tap_to_unlock), Toast.LENGTH_LONG)
                findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
            } catch (e: Exception) {
                requireContext().showToast(getString(R.string.launcher_failed_to_lock_device), Toast.LENGTH_LONG)
                prefs.lockModeOn = false
            }
        }
    }

    private fun showStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            requireActivity().window.insetsController?.show(WindowInsets.Type.statusBars())
        else
            @Suppress("DEPRECATION", "InlinedApi")
            requireActivity().window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
    }

    private fun hideStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            requireActivity().window.insetsController?.hide(WindowInsets.Type.statusBars())
        else {
            @Suppress("DEPRECATION")
            requireActivity().window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN
            }
        }
    }

    private fun changeAppTheme() {
        if (prefs.dailyWallpaper.not()) return
        val changedAppTheme = getChangedAppTheme(requireContext(), prefs.appTheme)
        prefs.appTheme = changedAppTheme
        if (prefs.dailyWallpaper) {
            setPlainWallpaperByTheme(requireContext(), changedAppTheme)
            viewModel.setWallpaperWorker()
        }
        requireActivity().recreate()
    }

    private fun openScreenTimeDigitalWellbeing() {
        if (prefs.screenTimeAppPackage.isNotBlank()) {
            launchApp(
                "Screen Time",
                prefs.screenTimeAppPackage,
                prefs.screenTimeAppClassName,
                prefs.screenTimeAppUser
            )
            return
        }
        val intent = Intent()
        try {
            intent.setClassName(
                Constants.DIGITAL_WELLBEING_PACKAGE_NAME,
                Constants.DIGITAL_WELLBEING_ACTIVITY
            )
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                intent.setClassName(
                    Constants.DIGITAL_WELLBEING_SAMSUNG_PACKAGE_NAME,
                    Constants.DIGITAL_WELLBEING_SAMSUNG_ACTIVITY
                )
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showLongPressToast() = requireContext().showToast(getString(R.string.long_press_to_select_app))

    private fun textOnClick(view: View) = onClick(view)

    private fun textOnLongClick(view: View) = onLongClick(view)

    private fun getSwipeGestureListener(context: Context): View.OnTouchListener {
        return object : OnSwipeTouchListener(context) {
            // Horizontal swipes navigate journal pages via ViewPager2.
            // Left/right app shortcuts remain available from dock icon swipes.

            override fun onSwipeUp() {
                super.onSwipeUp()
                showAppList(Constants.FLAG_LAUNCH_APP)
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                swipeDownAction()
            }

            override fun onLongClick() {
                super.onLongClick()
                try {
                    findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                    viewModel.firstOpen(false)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onDoubleClick() {
                super.onDoubleClick()
                if (!prefs.lockModeOn) return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    binding.lock.performClick()
                else
                    lockPhone()
            }

            override fun onClick() {
                super.onClick()
                viewModel.checkForMessages.call()
            }
        }
    }

    private fun getViewSwipeTouchListener(context: Context, view: View): View.OnTouchListener {
        return object : ViewSwipeTouchListener(context, view) {
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                openSwipeLeftApp()
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                openSwipeRightApp()
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                showAppList(Constants.FLAG_LAUNCH_APP)
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                swipeDownAction()
            }

            override fun onLongClick(view: View) {
                super.onLongClick(view)
                textOnLongClick(view)
            }

            override fun onClick(view: View) {
                super.onClick(view)
                textOnClick(view)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}