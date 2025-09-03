/*
 * Kiwix Android
 * Copyright (c) 2024 Kiwix <android.kiwix.org>
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

package org.kiwix.kiwixmobile.webserver

import android.Manifest
import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.kiwix.kiwixmobile.R.drawable
import org.kiwix.kiwixmobile.core.R
import org.kiwix.kiwixmobile.core.base.BaseActivity
import org.kiwix.kiwixmobile.core.base.BaseFragment
import org.kiwix.kiwixmobile.core.extensions.ActivityExtensions.hasNotificationPermission
import org.kiwix.kiwixmobile.core.extensions.ActivityExtensions.isCustomApp
import org.kiwix.kiwixmobile.core.extensions.ActivityExtensions.isManageExternalStoragePermissionGranted
import org.kiwix.kiwixmobile.core.extensions.toast
import org.kiwix.kiwixmobile.core.navigateToAppSettings
import org.kiwix.kiwixmobile.core.navigateToSettings
import org.kiwix.kiwixmobile.core.qr.GenerateQR
import org.kiwix.kiwixmobile.core.reader.ZimFileReader
import org.kiwix.kiwixmobile.core.reader.ZimReaderContainer
import org.kiwix.kiwixmobile.core.ui.components.ContentLoadingProgressBar
import org.kiwix.kiwixmobile.core.ui.components.NavigationIcon
import org.kiwix.kiwixmobile.core.ui.models.IconItem
import org.kiwix.kiwixmobile.core.ui.theme.StartServerGreen
import org.kiwix.kiwixmobile.core.ui.theme.StopServerRed
import org.kiwix.kiwixmobile.core.utils.ConnectivityReporter
import org.kiwix.kiwixmobile.core.utils.ServerUtils
import org.kiwix.kiwixmobile.core.utils.SharedPreferenceUtil
import org.kiwix.kiwixmobile.core.utils.dialog.AlertDialogShower
import org.kiwix.kiwixmobile.core.utils.dialog.DialogHost
import org.kiwix.kiwixmobile.core.utils.dialog.KiwixDialog
import org.kiwix.kiwixmobile.core.utils.dialog.KiwixDialog.StartServer
import org.kiwix.kiwixmobile.core.utils.files.Log
import org.kiwix.kiwixmobile.core.zim_manager.fileselect_view.BooksOnDiskListItem
import org.kiwix.kiwixmobile.core.zim_manager.fileselect_view.BooksOnDiskListItem.BookOnDisk
import org.kiwix.kiwixmobile.core.zim_manager.fileselect_view.SelectionMode
import org.kiwix.kiwixmobile.main.KiwixMainActivity
import org.kiwix.kiwixmobile.webserver.wifi_hotspot.HotspotService
import org.kiwix.kiwixmobile.webserver.wifi_hotspot.HotspotService.Companion.ACTION_CHECK_IP_ADDRESS
import org.kiwix.kiwixmobile.webserver.wifi_hotspot.HotspotService.Companion.ACTION_START_SERVER
import org.kiwix.kiwixmobile.webserver.wifi_hotspot.HotspotService.Companion.ACTION_STOP_SERVER
import javax.inject.Inject

class ZimHostFragment : BaseFragment(), ZimHostCallbacks, ZimHostContract.View {
  @Inject
  internal lateinit var presenter: ZimHostContract.Presenter

  @Inject
  internal lateinit var connectivityReporter: ConnectivityReporter

  @Inject
  internal lateinit var alertDialogShower: AlertDialogShower

  @Inject
  lateinit var sharedPreferenceUtil: SharedPreferenceUtil

  @Inject
  lateinit var zimReaderFactory: ZimFileReader.Factory

  @Inject
  lateinit var zimReaderContainer: ZimReaderContainer

  @Inject
  lateinit var generateQr: GenerateQR

  private var hotspotService: HotspotService? = null
  private var ip: String? = null
  private lateinit var serviceConnection: ServiceConnection
  private var isHotspotServiceRunning = false
  private var serverIpText = mutableStateOf("")
  private var shareIconItem = mutableStateOf(false to {})
  private var qrImageItem: MutableState<Pair<Boolean, IconItem>> =
    mutableStateOf(false to IconItem.Drawable(drawable.ic_storage))
  private var startServerButtonItem =
    mutableStateOf(
      Triple(
        "",
        StartServerGreen
      ) { startServerButtonClick() }
    )
  private var booksList: MutableState<List<BooksOnDiskListItem>> = mutableStateOf(arrayListOf())

  private val selectedBooksPath: ArrayList<String>
    get() {
      return booksList.value
        .filter(BooksOnDiskListItem::isSelected)
        .filterIsInstance<BookOnDisk>()
        .map {
          it.zimReaderSource.toDatabase()
        }
        .onEach { path ->
          Log.v(tag, "ZIM PATH : $path")
        }
        as ArrayList<String>
    }

  private val notificationPermissionListener =
    registerForActivityResult(
      ActivityResultContracts.RequestPermission()
    ) { isGranted ->
      if (isGranted) {
        startServerButtonClick()
      } else {
        if (!ActivityCompat.shouldShowRequestPermissionRationale(
            requireActivity(),
            POST_NOTIFICATIONS
          )
        ) {
          alertDialogShower.show(
            KiwixDialog.NotificationPermissionDialog,
            requireActivity()::navigateToAppSettings
          )
        }
      }
    }

  private var storagePermissionLauncher: ActivityResultLauncher<Array<String>>? =
    registerForActivityResult(
      ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionResult ->
      val isGranted =
        permissionResult.entries.all(
          Map.Entry<String, @kotlin.jvm.JvmSuppressWildcards Boolean>::value
        )
      if (isGranted) {
        startServerButtonClick()
      } else {
        if (!ActivityCompat.shouldShowRequestPermissionRationale(
            requireActivity(),
            Manifest.permission.READ_EXTERNAL_STORAGE
          )
        ) {
          alertDialogShower.show(
            KiwixDialog.ReadPermissionRequired,
            requireActivity()::navigateToAppSettings
          )
        }
      }
    }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? = ComposeView(requireContext()).apply {
    setContent {
      ZimHostScreen(
        serverIpText = serverIpText.value,
        shareIconItem = shareIconItem.value,
        qrImageItem = qrImageItem.value,
        startServerButtonItem = startServerButtonItem.value,
        selectionMode = SelectionMode.MULTI,
        onMultiSelect = { select(it) },
        booksList = booksList.value
      ) {
        NavigationIcon(
          onClick = { activity?.onBackPressedDispatcher?.onBackPressed() }
        )
      }
      DialogHost(alertDialogShower)
    }
  }

  override fun inject(baseActivity: BaseActivity) {
    (baseActivity as KiwixMainActivity).cachedComponent.inject(this)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    presenter.attachView(this)

    serviceConnection =
      object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
          // do nothing
        }

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
          hotspotService = (service as HotspotService.HotspotBinder).service.get()
          hotspotService?.registerCallBack(this@ZimHostFragment)
        }
      }
  }

  private fun startServerButtonClick() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    ) {
      if (requireActivity().hasNotificationPermission(sharedPreferenceUtil)) {
        handleStoragePermissionAndServer()
      } else {
        notificationPermissionListener.launch(POST_NOTIFICATIONS)
      }
    } else {
      handleStoragePermissionAndServer()
    }
  }

  private fun handleStoragePermissionAndServer() {
    // we does not require any permission for playStore variant.
    if (sharedPreferenceUtil.isPlayStoreBuildWithAndroid11OrAbove()) {
      startStopServer()
      return
    }

    if (ContextCompat.checkSelfPermission(
        requireActivity(),
        Manifest.permission.READ_EXTERNAL_STORAGE
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        // ask the storage permission for below Android 13.
        // Since there is no storage permission available in Android 13 and above.
        storagePermissionLauncher?.launch(
          arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
          )
        )
      } else {
        handleManageExternalStoragePermissionAndServer()
      }
    } else {
      handleManageExternalStoragePermissionAndServer()
    }
  }

  private fun handleManageExternalStoragePermissionAndServer() {
    if (!requireActivity().isManageExternalStoragePermissionGranted(sharedPreferenceUtil)) {
      showManageExternalStoragePermissionDialog()
    } else {
      startStopServer()
    }
  }

  private fun startStopServer() {
    when {
      ServerUtils.isServerStarted -> stopServer()
      selectedBooksPath.isNotEmpty() -> {
        when {
          connectivityReporter.checkWifi() -> startWifiDialog()
          connectivityReporter.checkTethering() -> startKiwixHotspot()
          else -> startHotspotManuallyDialog()
        }
      }

      else -> toast(R.string.no_books_selected_toast_message, Toast.LENGTH_SHORT)
    }
  }

  private fun startKiwixHotspot() {
    alertDialogShower.show(StartServer { ContentLoadingProgressBar() })
    requireActivity().startService(createHotspotIntent(ACTION_CHECK_IP_ADDRESS))
  }

  private fun stopServer() {
    requireActivity().startService(
      createHotspotIntent(ACTION_STOP_SERVER)
    ).also {
      isHotspotServiceRunning = false
    }
  }

  private fun select(bookOnDisk: BookOnDisk) {
    val tempBooksList: List<BooksOnDiskListItem> = booksList.value.onEach {
      if (it == bookOnDisk) {
        it.isSelected = !it.isSelected
      }
      it
    }
    // Force recomposition by first setting an empty list before assigning the updated list.
    // This is necessary because modifying an object's property doesn't trigger recomposition,
    // as Compose still considers the list unchanged.
    booksList.value = emptyList()
    booksList.value = tempBooksList
    saveHostedBooks(tempBooksList)
    if (ServerUtils.isServerStarted) {
      startWifiHotspot(true)
    }
  }

  override fun onStart() {
    super.onStart()
    bindService()
  }

  override fun onStop() {
    super.onStop()
    unbindService()
  }

  private fun bindService() {
    requireActivity().bindService(
      Intent(requireActivity(), HotspotService::class.java),
      serviceConnection,
      Context.BIND_AUTO_CREATE
    )
  }

  private fun unbindService() {
    hotspotService?.let {
      requireActivity().unbindService(serviceConnection)
      if (!isHotspotServiceRunning) {
        unRegisterHotspotService()
      }
    }
  }

  override fun onResume() {
    super.onResume()
    lifecycleScope.launch {
      presenter.loadBooks(sharedPreferenceUtil.hostedBooks)
    }
    if (ServerUtils.isServerStarted) {
      ip = ServerUtils.getSocketAddress()
      layoutServerStarted()
    } else {
      layoutServerStopped()
    }
  }

  private fun saveHostedBooks(booksList: List<BooksOnDiskListItem>) {
    sharedPreferenceUtil.hostedBooks =
      booksList.asSequence()
        .filter(BooksOnDiskListItem::isSelected)
        .filterIsInstance<BookOnDisk>()
        .map { it.book.title }
        .toSet()
  }

  private fun layoutServerStarted() {
    serverIpText.value = getString(R.string.server_started_message, ip)
    configureUrlSharingIcon(true)
    configureQrIcon(true)
    startServerButtonItem.value =
      Triple(getString(R.string.stop_server_label), StopServerRed) { startServerButtonClick() }
  }

  private fun configureUrlSharingIcon(shouldShow: Boolean) {
    shareIconItem.value = shouldShow to {
      val urlSharingIntent = Intent(Intent.ACTION_SEND)
      urlSharingIntent.apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, ip)
      }
      startActivity(urlSharingIntent)
    }
  }

  private fun configureQrIcon(shouldShow: Boolean) {
    val qrImage = ip?.let {
      val qr = generateQr.createQR(it)
      IconItem.ImageBitmap(qr.asImageBitmap())
    } ?: IconItem.Drawable(drawable.ic_storage)
    qrImageItem.value = shouldShow to qrImage
  }

  private fun layoutServerStopped() {
    serverIpText.value = getString(R.string.server_textview_default_message)
    configureUrlSharingIcon(false)
    configureQrIcon(false)
    startServerButtonItem.value =
      Triple(getString(R.string.start_server_label), StartServerGreen) { startServerButtonClick() }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    unRegisterHotspotService()
    presenter.detachView(this)
  }

  private fun unRegisterHotspotService() {
    hotspotService?.registerCallBack(null)
    hotspotService = null
  }

  // Advice user to turn on hotspot manually for API<26
  private fun startHotspotManuallyDialog() {
    alertDialogShower.show(
      KiwixDialog.StartHotspotManually,
      ::launchTetheringSettingsScreen,
      ::openWifiSettings,
      {}
    )
  }

  private fun startWifiDialog() {
    alertDialogShower.show(
      KiwixDialog.WiFiOnWhenHostingBooks,
      ::openWifiSettings,
      {},
      ::startKiwixHotspot
    )
  }

  private fun openWifiSettings() {
    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
  }

  private fun createHotspotIntent(action: String): Intent =
    Intent(requireActivity(), HotspotService::class.java).setAction(action)

  override fun onServerStarted(ip: String) {
    // Dismiss dialog when server started.
    alertDialogShower.dismiss()
    this.ip = ip
    layoutServerStarted()
  }

  override fun onServerStopped() {
    layoutServerStopped()
  }

  override fun onServerFailedToStart(errorMessage: Int?) {
    // Dismiss dialog if there is some error in starting the server.
    alertDialogShower.dismiss()
    errorMessage?.let {
      toast(errorMessage)
    }
  }

  private fun launchTetheringSettingsScreen() {
    startActivity(
      Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        component = ComponentName("com.android.settings", "com.android.settings.TetherSettings")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
    )
  }

  @Suppress("NestedBlockDepth")
  override suspend fun addBooks(books: List<BooksOnDiskListItem>) {
    // Check if this is the app module, as custom apps may have multiple package names
    if (!requireActivity().isCustomApp()) {
      booksList.value = books
    } else {
      val updatedBooksList: MutableList<BooksOnDiskListItem> = arrayListOf()
      books.forEach {
        if (it is BookOnDisk) {
          zimReaderContainer.zimFileReader?.let { zimFileReader ->
            val booksOnDiskListItem =
              (BookOnDisk(zimFileReader) as BooksOnDiskListItem)
                .apply {
                  isSelected = true
                }
            updatedBooksList.add(booksOnDiskListItem)
          }
        } else {
          updatedBooksList.add(it)
        }
      }
      booksList.value = updatedBooksList
    }
  }

  private fun startWifiHotspot(restartServer: Boolean) {
    requireActivity().startService(
      createHotspotIntent(ACTION_START_SERVER).putStringArrayListExtra(
        SELECTED_ZIM_PATHS_KEY,
        selectedBooksPath
      ).putExtra(RESTART_SERVER, restartServer)
    ).also {
      isHotspotServiceRunning = true
    }
  }

  private fun showManageExternalStoragePermissionDialog() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      alertDialogShower.show(
        KiwixDialog.ManageExternalFilesPermissionDialog,
        {
          this.activity?.let(FragmentActivity::navigateToSettings)
        }
      )
    }
  }

  override fun onIpAddressValid() {
    startWifiHotspot(false)
  }

  override fun onIpAddressInvalid() {
    alertDialogShower.dismiss()
    toast(R.string.server_failed_message, Toast.LENGTH_SHORT)
  }

  companion object {
    const val SELECTED_ZIM_PATHS_KEY = "selected_zim_paths"
    const val RESTART_SERVER = "restart_server"
  }
}
