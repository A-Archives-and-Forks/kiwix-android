/*
 * Kiwix Android
 * Copyright (c) 2020 Kiwix <android.kiwix.org>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.kiwix.kiwixmobile.main

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptions
import androidx.navigation.compose.rememberNavController
import eu.mhutti1.utils.storage.StorageDevice
import eu.mhutti1.utils.storage.StorageDeviceUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.kiwix.kiwixmobile.BuildConfig
import org.kiwix.kiwixmobile.R
import org.kiwix.kiwixmobile.core.CoreApp
import org.kiwix.kiwixmobile.core.R.drawable
import org.kiwix.kiwixmobile.core.R.mipmap
import org.kiwix.kiwixmobile.core.R.string
import org.kiwix.kiwixmobile.core.base.BaseActivity
import org.kiwix.kiwixmobile.core.base.FragmentActivityExtensions
import org.kiwix.kiwixmobile.core.dao.LibkiwixBookOnDisk
import org.kiwix.kiwixmobile.core.downloader.downloadManager.DOWNLOAD_NOTIFICATION_TITLE
import org.kiwix.kiwixmobile.core.downloader.downloadManager.HUNDERED
import org.kiwix.kiwixmobile.core.extensions.toast
import org.kiwix.kiwixmobile.core.extensions.update
import org.kiwix.kiwixmobile.core.main.ACTION_NEW_TAB
import org.kiwix.kiwixmobile.core.main.CoreMainActivity
import org.kiwix.kiwixmobile.core.main.DrawerMenuItem
import org.kiwix.kiwixmobile.core.main.LEFT_DRAWER_HELP_ITEM_TESTING_TAG
import org.kiwix.kiwixmobile.core.main.LEFT_DRAWER_SUPPORT_ITEM_TESTING_TAG
import org.kiwix.kiwixmobile.core.main.LEFT_DRAWER_ZIM_HOST_ITEM_TESTING_TAG
import org.kiwix.kiwixmobile.core.main.NEW_TAB_SHORTCUT_ID
import org.kiwix.kiwixmobile.core.main.ZIM_HOST_DEEP_LINK_SCHEME
import org.kiwix.kiwixmobile.core.reader.ZimReaderSource
import org.kiwix.kiwixmobile.core.utils.LanguageUtils.Companion.handleLocaleChange
import org.kiwix.kiwixmobile.core.utils.dialog.DialogHost
import org.kiwix.kiwixmobile.kiwixActivityComponent
import org.kiwix.kiwixmobile.kiwixComponent
import org.kiwix.kiwixmobile.ui.KiwixDestination
import javax.inject.Inject

const val ACTION_GET_CONTENT = "GET_CONTENT"
const val OPENING_ZIM_FILE_DELAY = 300L
const val GET_CONTENT_SHORTCUT_ID = "get_content_shortcut"

class KiwixMainActivity : CoreMainActivity() {
  private var actionMode: ActionMode? = null
  override val cachedComponent by lazy { kiwixActivityComponent }
  override val searchFragmentRoute: String = KiwixDestination.Search.route

  @Inject lateinit var libkiwixBookOnDisk: LibkiwixBookOnDisk

  override val mainActivity: AppCompatActivity by lazy { this }
  override val appName: String by lazy { getString(R.string.app_name) }

  override val bookmarksFragmentRoute: String = KiwixDestination.Bookmarks.route
  override val settingsFragmentRoute: String = KiwixDestination.Settings.route
  override val historyFragmentRoute: String = KiwixDestination.History.route
  override val notesFragmentRoute: String = KiwixDestination.Notes.route
  override val readerFragmentRoute: String = KiwixDestination.Reader.route
  override val helpFragmentRoute: String = KiwixDestination.Help.route
  override val topLevelDestinationsRoute =
    setOf(
      KiwixDestination.Downloads.route,
      KiwixDestination.Library.route,
      KiwixDestination.Reader.route
    )

  private val shouldShowBottomAppBar = mutableStateOf(true)

  private var isIntroScreenVisible: Boolean = false

  private val finishActionModeOnDestinationChange =
    NavController.OnDestinationChangedListener { _, _, _ ->
      actionMode?.finish()
    }
  private val storageDeviceList = arrayListOf<StorageDevice>()
  private val pendingIntentFlow = MutableStateFlow<Intent?>(null)

  @Suppress("InjectDispatcher")
  @OptIn(ExperimentalMaterial3Api::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    cachedComponent.inject(this)
    super.onCreate(savedInstanceState)
    setContent {
      val pendingIntent by pendingIntentFlow.collectAsState()
      navController = rememberNavController()
      leftDrawerState = rememberDrawerState(DrawerValue.Closed)
      uiCoroutineScope = rememberCoroutineScope()
      bottomAppBarScrollBehaviour = BottomAppBarDefaults.exitAlwaysScrollBehavior()
      val startDestination = remember {
        if (sharedPreferenceUtil.showIntro() && !isIntroScreenNotVisible()) {
          KiwixDestination.Intro.route
        } else {
          KiwixDestination.Reader.route
        }
      }
      KiwixMainActivityScreen(
        navController = navController,
        leftDrawerContent = leftDrawerMenu,
        startDestination = startDestination,
        topLevelDestinationsRoute = topLevelDestinationsRoute,
        leftDrawerState = leftDrawerState,
        uiCoroutineScope = uiCoroutineScope,
        enableLeftDrawer = enableLeftDrawer.value,
        shouldShowBottomAppBar = shouldShowBottomAppBar.value,
        bottomAppBarScrollBehaviour = bottomAppBarScrollBehaviour
      )
      LaunchedEffect(navController) {
        navController.addOnDestinationChangedListener(finishActionModeOnDestinationChange)
      }
      val lifecycleOwner = LocalLifecycleOwner.current
      val lifecycle = lifecycleOwner.lifecycle
      LaunchedEffect(navController, pendingIntent) {
        snapshotFlow { pendingIntent }
          .filterNotNull()
          .collectLatest { intent ->
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
              // Wait until fragment manager is fully initialized and view hierarchy is ready
              delay(HUNDERED.toLong())
              handleAllIntents(intent)
              pendingIntentFlow.value = null
            }
          }
      }
      DialogHost(alertDialogShower)
    }
    lifecycleScope.launch {
      migrateInternalToPublicAppDirectory()
    }
    intent?.let {
      pendingIntentFlow.value = it
    }
    // run the migration on background thread to avoid any UI related issues.
    CoroutineScope(Dispatchers.IO).launch {
      if (!sharedPreferenceUtil.prefIsTest) {
        (this as BaseActivity).kiwixComponent
          .provideObjectBoxDataMigrationHandler()
          .migrate()
      }
    }
  }

  private fun handleAllIntents(newIntent: Intent?) {
    newIntent?.let { intent ->
      handleZimFileIntent(intent)
      handleNotificationIntent(intent)
      handleGetContentIntent(intent)
      safelyHandleDeepLink(intent)
    }
  }

  private fun safelyHandleDeepLink(intent: Intent) {
    if (intent.data != null && intent.extras != null) {
      navController.handleDeepLink(intent)
    }
  }

  private suspend fun migrateInternalToPublicAppDirectory() {
    if (!sharedPreferenceUtil.prefIsAppDirectoryMigrated) {
      val storagePath =
        getStorageDeviceList()
          .getOrNull(sharedPreferenceUtil.storagePosition)
          ?.name
      storagePath?.let {
        sharedPreferenceUtil.putPrefStorage(sharedPreferenceUtil.getPublicDirectoryPath(it))
        sharedPreferenceUtil.putPrefAppDirectoryMigrated(true)
      }
    }
  }

  /**
   * Fetches the storage device list once in the main activity and reuses it across all fragments.
   * This is necessary because retrieving the storage device list, especially on devices with large SD cards,
   * is a resource-intensive operation. Performing this operation repeatedly in fragments can negatively
   * affect the user experience, as it takes time and can block the UI.
   *
   * If a fragment is destroyed and we need to retrieve the device list again, performing the operation
   * repeatedly leads to inefficiency. To optimize this, we fetch the storage device list once and reuse
   * it in all fragments, thereby reducing redundant processing and improving performance.
   */
  suspend fun getStorageDeviceList(): List<StorageDevice> {
    if (storageDeviceList.isEmpty()) {
      storageDeviceList.addAll(StorageDeviceUtils.getWritableStorage(this))
    }
    return storageDeviceList
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    leftDrawerMenu.clear()
    leftDrawerMenu.addAll(leftNavigationDrawerMenuItems)
  }

  override fun onStart() {
    super.onStart()
    if (!sharedPreferenceUtil.prefIsTest) {
      sharedPreferenceUtil.setIsPlayStoreBuildType(BuildConfig.IS_PLAYSTORE)
    }
    setDefaultDeviceLanguage()
  }

  private fun setDefaultDeviceLanguage() {
    if (sharedPreferenceUtil.prefDeviceDefaultLanguage.isEmpty()) {
      ConfigurationCompat.getLocales(
        applicationContext.resources.configuration
      )[0]?.language?.let {
        sharedPreferenceUtil.putPrefDeviceDefaultLanguage(it)
        handleLocaleChange(
          this,
          sharedPreferenceUtil.prefLanguage,
          sharedPreferenceUtil
        )
      }
    }
  }

  private fun isIntroScreenNotVisible(): Boolean =
    isIntroScreenVisible.also {
      isIntroScreenVisible = true
    }

  override fun onSupportActionModeStarted(mode: ActionMode) {
    super.onSupportActionModeStarted(mode)
    actionMode = mode
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    pendingIntentFlow.value = intent
    supportFragmentManager.fragments.filterIsInstance<FragmentActivityExtensions>().forEach {
      it.onNewIntent(intent, this)
    }
  }

  private fun handleGetContentIntent(intent: Intent?) {
    if (intent?.action == ACTION_GET_CONTENT) {
      navigate(KiwixDestination.Downloads.route) {
        launchSingleTop = true
        popUpTo(navController.graph.findStartDestination().id)
      }
    }
  }

  private fun handleZimFileIntent(intent: Intent?) {
    intent?.data?.let {
      when (it.scheme) {
        "file",
        "content" -> {
          Handler(Looper.getMainLooper()).postDelayed({
            openLocalLibraryWithZimFilePath("$it")
            clearIntentDataAndAction()
          }, OPENING_ZIM_FILE_DELAY)
        }

        else -> {
          if (it.scheme != ZIM_HOST_DEEP_LINK_SCHEME) {
            toast(R.string.cannot_open_file)
          }
        }
      }
    }
  }

  private fun clearIntentDataAndAction() {
    // if used once then clear it to avoid affecting any other functionality
    // of the application.
    intent.action = null
    intent.data = null
  }

  private fun openLocalLibraryWithZimFilePath(path: String) {
    navigate(KiwixDestination.Library.createRoute(zimFileUri = path))
  }

  private fun handleNotificationIntent(intent: Intent?) {
    if (intent?.hasExtra(DOWNLOAD_NOTIFICATION_TITLE) == true) {
      lifecycleScope.launch {
        delay(OPENING_ZIM_FILE_DELAY)
        intent.getStringExtra(DOWNLOAD_NOTIFICATION_TITLE)?.let {
          libkiwixBookOnDisk.bookMatching(it)?.let { bookOnDiskEntity ->
            openZimFromFilePath(bookOnDiskEntity.zimReaderSource.toDatabase())
          }
        }
      }
    }
  }

  private fun openZimFromFilePath(path: String) {
    navigate(KiwixDestination.Reader.createRoute(zimFileUri = path))
  }

  override val zimHostDrawerMenuItem: DrawerMenuItem? = DrawerMenuItem(
    title = CoreApp.instance.getString(string.menu_wifi_hotspot),
    iconRes = drawable.ic_mobile_screen_share_24px,
    visible = true,
    onClick = { openZimHostFragment() },
    testingTag = LEFT_DRAWER_ZIM_HOST_ITEM_TESTING_TAG
  )

  override val helpDrawerMenuItem: DrawerMenuItem? = DrawerMenuItem(
    title = CoreApp.instance.getString(string.menu_help),
    iconRes = drawable.ic_help_24px,
    visible = true,
    onClick = { openHelpFragment() },
    testingTag = LEFT_DRAWER_HELP_ITEM_TESTING_TAG
  )

  override val supportDrawerMenuItem: DrawerMenuItem? = DrawerMenuItem(
    title = CoreApp.instance.getString(string.menu_support_kiwix),
    iconRes = drawable.ic_support_24px,
    visible = true,
    onClick = { openSupportKiwixExternalLink() },
    testingTag = LEFT_DRAWER_SUPPORT_ITEM_TESTING_TAG
  )

  /**
   * In kiwix app we are not showing the "About app" item so returning null.
   */
  override val aboutAppDrawerMenuItem: DrawerMenuItem? = null

  private fun openZimHostFragment() {
    disableLeftDrawer()
    handleDrawerOnNavigation()
    navigate(KiwixDestination.ZimHost.route)
  }

  override fun getIconResId() = mipmap.ic_launcher

  override fun createApplicationShortcuts() {
    // Remove previously added dynamic shortcuts for old ids if any found.
    removeOutdatedIdShortcuts()
    ShortcutManagerCompat.addDynamicShortcuts(this, dynamicShortcutList())
  }

  override fun openSearch(searchString: String, isOpenedFromTabView: Boolean, isVoice: Boolean) {
    // remove the previous backStack entry with old arguments. Bug Fix #4392
    removeArgumentsOfReaderScreen()
    // Freshly open the search fragment.
    navigate(
      KiwixDestination.Search.createRoute(
        searchString = searchString,
        isOpenedFromTabView = isOpenedFromTabView,
        isVoice = isVoice
      ),
      NavOptions.Builder().setPopUpTo(searchFragmentRoute, inclusive = true).build()
    )
  }

  override fun openPage(
    pageUrl: String,
    zimReaderSource: ZimReaderSource?,
    shouldOpenInNewTab: Boolean
  ) {
    var zimFileUri = ""
    if (zimReaderSource != null) {
      zimFileUri = zimReaderSource.toDatabase()
    }
    val navOptions = NavOptions.Builder()
      .setLaunchSingleTop(true)
      .setPopUpTo(readerFragmentRoute, inclusive = true)
      .build()
    val readerRoute = KiwixDestination.Reader.createRoute(
      zimFileUri = zimFileUri,
      pageUrl = pageUrl,
      shouldOpenInNewTab = shouldOpenInNewTab
    )
    navigate(
      readerRoute,
      navOptions
    )
  }

  override fun hideBottomAppBar() {
    shouldShowBottomAppBar.update { false }
  }

  override fun showBottomAppBar() {
    shouldShowBottomAppBar.update { true }
  }

  /**
   * Handles navigation from the left drawer to the Reader screen.
   *
   * Clears any existing Reader back stack entry (with its arguments)
   * and replaces it with a fresh Reader screen using default arguments.
   * This ensures old arguments are not retained when navigating
   * via the left drawer.
   */
  override fun removeArgumentsOfReaderScreen() {
    if (navController.currentDestination?.route?.startsWith(readerFragmentRoute) == true) {
      navigate(
        readerFragmentRoute,
        NavOptions.Builder()
          .setPopUpTo(KiwixDestination.Reader.route, inclusive = true)
          .build()
      )
    }
  }

  // Outdated shortcut ids(new_tab, get_content)
  // Remove if the application has the outdated shortcuts.
  private fun removeOutdatedIdShortcuts() {
    ShortcutManagerCompat.getDynamicShortcuts(this).forEach {
      if (it.id == "new_tab" || it.id == "get_content") {
        ShortcutManagerCompat.removeDynamicShortcuts(this, arrayListOf(it.id))
      }
    }
  }

  private fun dynamicShortcutList(): List<ShortcutInfoCompat> {
    // Create a shortcut for opening the "New tab"
    val newTabShortcut =
      ShortcutInfoCompat.Builder(this, NEW_TAB_SHORTCUT_ID)
        .setShortLabel(getString(string.new_tab_shortcut_label))
        .setLongLabel(getString(string.new_tab_shortcut_label))
        .setIcon(IconCompat.createWithResource(this, drawable.ic_shortcut_new_tab))
        .setDisabledMessage(getString(string.shortcut_disabled_message))
        .setIntent(
          Intent(this, KiwixMainActivity::class.java).apply {
            action = ACTION_NEW_TAB
          }
        )
        .build()

    // create a shortCut for opening the online fragment.
    val getContentShortcut =
      ShortcutInfoCompat.Builder(this, GET_CONTENT_SHORTCUT_ID)
        .setShortLabel(getString(string.get_content_shortcut_label))
        .setLongLabel(getString(string.get_content_shortcut_label))
        .setIcon(IconCompat.createWithResource(this, drawable.ic_shortcut_get_content))
        .setDisabledMessage(getString(string.shortcut_disabled_message))
        .setIntent(
          Intent(this, KiwixMainActivity::class.java).apply {
            action = ACTION_GET_CONTENT
          }
        )
        .build()

    return listOf(newTabShortcut, getContentShortcut)
  }
}
