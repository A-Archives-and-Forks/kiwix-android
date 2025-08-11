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

package org.kiwix.kiwixmobile.core.downloader.downloadManager

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.Error
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.FetchListener
import com.tonyodev.fetch2.Status
import com.tonyodev.fetch2.util.DEFAULT_NOTIFICATION_TIMEOUT_AFTER_RESET
import com.tonyodev.fetch2core.DownloadBlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.kiwix.kiwixmobile.core.CoreApp
import org.kiwix.kiwixmobile.core.Intents
import org.kiwix.kiwixmobile.core.R
import org.kiwix.kiwixmobile.core.R.string
import org.kiwix.kiwixmobile.core.dao.DownloadRoomDao
import org.kiwix.kiwixmobile.core.main.CoreMainActivity
import org.kiwix.kiwixmobile.core.utils.DOWNLOAD_NOTIFICATION_CHANNEL_ID
import javax.inject.Inject

const val THIRTY_TREE = 33
const val DOWNLOAD_SERVICE_NOTIFICATION_ID = 1
const val APP_NAME_KEY = "appNameKey"

@Suppress("InjectDispatcher")
class DownloadMonitorService : Service() {
  private val taskFlow = MutableSharedFlow<suspend () -> Unit>(extraBufferCapacity = Int.MAX_VALUE)
  private var updaterJob: Job? = null
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val notificationManager: NotificationManager by lazy {
    getSystemService(NOTIFICATION_SERVICE) as NotificationManager
  }
  private val downloadNotificationsBuilderMap = mutableMapOf<Int, NotificationCompat.Builder>()

  @Inject
  lateinit var fetch: Fetch

  @Inject
  lateinit var fetchDownloadNotificationManager: FetchDownloadNotificationManager

  @Inject
  lateinit var downloadRoomDao: DownloadRoomDao
  private var appName: String? = "kiwix"

  override fun onCreate() {
    CoreApp.coreComponent
      .coreServiceComponent()
      .service(this)
      .build()
      .inject(this)
    super.onCreate()
    setupUpdater()
    fetch.addListener(fetchListener, true)
    showDownloadServiceForegroundNotification()
  }

  private fun setupUpdater() {
    updaterJob = scope.launch {
      taskFlow.collect { task ->
        runCatching {
          task.invoke()
        }.onFailure { it.printStackTrace() }
      }
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.hasExtra(APP_NAME_KEY) == true) {
      appName = intent.getStringExtra(APP_NAME_KEY)
    }
    if (intent?.action == STOP_DOWNLOAD_SERVICE) {
      stopForegroundServiceForDownloads()
    }
    return START_STICKY
  }

  /**
   * Shows a persistent foreground notification while at least one download is active.
   * The notification remains visible until all downloads are complete or stopped.
   *
   * Keeping this notification active ensures that the DownloadMonitorService
   * stays alive and prevents common issues such as
   * [android.app.ForegroundServiceStartNotAllowedException] that can occur
   * when trying to set a new foreground notification for another download
   * after one has completed while the app is in the background.
   *
   * In short, this method ensures a foreground notification is maintained
   * until there are no active downloads, at which point the service is stopped.
   */
  private fun showDownloadServiceForegroundNotification() {
    fetch.getDownloadsWithStatus(
      listOf(Status.NONE, Status.ADDED, Status.QUEUED, Status.DOWNLOADING, Status.PAUSED)
    ) { activeDownloads ->
      if (activeDownloads.isNotEmpty()) {
        downloadNotificationChannel()
        startForeground(DOWNLOAD_SERVICE_NOTIFICATION_ID, buildForegroundNotification())
      } else {
        // Stop the foreground service if no active downloads.
        stopForegroundServiceForDownloads()
      }
    }
  }

  private fun buildForegroundNotification(): Notification =
    NotificationCompat.Builder(this, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
      .setContentTitle(appName)
      .setContentText(getString(string.download_notification_channel_description))
      .setSmallIcon(R.mipmap.ic_launcher)
      .setWhen(System.currentTimeMillis())
      .build()

  private fun cancelNotificationForId(downloadId: Int) {
    notificationManager.cancel(downloadId)
  }

  private val fetchListener = object : FetchListener {
    override fun onAdded(download: Download) {
      // Do nothing
    }

    override fun onCancelled(download: Download) {
      delete(download)
    }

    override fun onCompleted(download: Download) {
      update(download, true)
    }

    override fun onDeleted(download: Download) {
      delete(download)
    }

    override fun onDownloadBlockUpdated(
      download: Download,
      downloadBlock: DownloadBlock,
      totalBlocks: Int
    ) {
      update(download)
    }

    override fun onError(download: Download, error: Error, throwable: Throwable?) {
      update(download, true)
    }

    override fun onPaused(download: Download) {
      update(download)
    }

    override fun onProgress(
      download: Download,
      etaInMilliSeconds: Long,
      downloadedBytesPerSecond: Long
    ) {
      update(download)
    }

    override fun onQueued(download: Download, waitingOnNetwork: Boolean) {
      update(download)
    }

    override fun onRemoved(download: Download) {
      delete(download)
    }

    override fun onResumed(download: Download) {
      update(download)
    }

    override fun onStarted(
      download: Download,
      downloadBlocks: List<DownloadBlock>,
      totalBlocks: Int
    ) {
      update(download)
    }

    override fun onWaitingNetwork(download: Download) {
      update(download)
    }

    private fun update(
      download: Download,
      updateForeGroundService: Boolean = false
    ) {
      taskFlow.tryEmit {
        downloadRoomDao.update(download)
        if (download.status == Status.COMPLETED) {
          downloadRoomDao.getEntityForDownloadId(download.id.toLong())?.let {
            showDownloadCompletedNotification(download)
            // to move these downloads in LibkiwixBookOnDisk.
            @Suppress("IgnoredReturnValue")
            downloadRoomDao.downloads().first()
          }
        }
        // If someone pause the Download then post a notification since fetch removes the
        // notification for ongoing download when pause so we needs to show our custom notification.
        if (download.isPaused()) {
          fetchDownloadNotificationManager.showDownloadPauseNotification(fetch, download)
        }
        if (updateForeGroundService) {
          stopForegroundServiceIfNoActiveDownloads(fetch)
        }
      }
    }

    private fun delete(download: Download) {
      taskFlow.tryEmit {
        downloadRoomDao.delete(download)
        stopForegroundServiceIfNoActiveDownloads(fetch)
      }
    }
  }

  private fun stopForegroundServiceIfNoActiveDownloads(fetch: Fetch) {
    taskFlow.tryEmit {
      // Check if there are any ongoing downloads.
      // If the list is empty, it means no other downloads are running,
      // so we need to promote this download to a foreground service.
      fetch.getDownloadsWithStatus(
        listOf(Status.NONE, Status.ADDED, Status.QUEUED, Status.DOWNLOADING)
      ) { activeDownloads ->
        if (activeDownloads.isEmpty()) {
          stopForegroundServiceForDownloads()
        }
      }
    }
  }

  private fun showDownloadCompletedNotification(download: Download) {
    val notificationBuilder = getNotificationBuilder(download.id)
    val notificationTitle =
      downloadRoomDao.getEntityForFileName(getDownloadNotificationTitle(download))?.title
        ?: download.file
    notificationBuilder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .setSmallIcon(android.R.drawable.stat_sys_download_done)
      .setContentTitle(notificationTitle)
      .setContentText(getString(string.complete))
      .setOngoing(false)
      .setGroup(download.id.toString())
      .setGroupSummary(false)
      .setProgress(ZERO, ZERO, false)
      .setTimeoutAfter(DEFAULT_NOTIFICATION_TIMEOUT_AFTER_RESET)
      .setContentIntent(getPendingIntentForDownloadedNotification(download))
      .setAutoCancel(true)
    // Assigning a new ID to the notification because the same ID is used for the foreground
    // notification. If we use the same ID, changing the foreground notification for another
    // ongoing download cancels the previous notification for that id, preventing the download
    // complete notification from being displayed.
    val downloadCompleteNotificationId = download.id + THIRTY_TREE
    // Cancel the complete download notification if already shown due to the application's
    // lifecycle fetch. See #4237 for more details.
    cancelNotificationForId(download.id - THIRTY_TREE)
    // Cancel the fetch related any notification if present.
    cancelNotificationForId(download.id)
    notificationManager.notify(downloadCompleteNotificationId, notificationBuilder.build())
  }

  private fun getPendingIntentForDownloadedNotification(download: Download): PendingIntent {
    val internal =
      Intents.internal(CoreMainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(DOWNLOAD_NOTIFICATION_TITLE, getDownloadNotificationTitle(download))
      }
    return PendingIntent.getActivity(
      this,
      download.id,
      internal,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
  }

  private fun downloadNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      if (notificationManager.getNotificationChannel(DOWNLOAD_NOTIFICATION_CHANNEL_ID) == null) {
        notificationManager.createNotificationChannel(createChannel())
      }
    }
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun createChannel() =
    NotificationChannel(
      DOWNLOAD_NOTIFICATION_CHANNEL_ID,
      getString(string.download_notification_channel_name),
      NotificationManager.IMPORTANCE_HIGH
    ).apply {
      description = getString(string.download_notification_channel_description)
      setSound(null, null)
      enableVibration(false)
    }

  @SuppressLint("RestrictedApi")
  private fun getNotificationBuilder(notificationId: Int): NotificationCompat.Builder {
    synchronized(downloadNotificationsBuilderMap) {
      val notificationBuilder =
        downloadNotificationsBuilderMap[notificationId]
          ?: NotificationCompat.Builder(this, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
      downloadNotificationsBuilderMap[notificationId] = notificationBuilder
      notificationBuilder
        .setGroup("$notificationId")
        .setStyle(null)
        .setProgress(ZERO, ZERO, false)
        .setContentTitle(null)
        .setContentText(null)
        .setContentIntent(null)
        .setGroupSummary(false)
        .setTimeoutAfter(DEFAULT_NOTIFICATION_TIMEOUT_AFTER_RESET)
        .setOngoing(false)
        .setOnlyAlertOnce(true)
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .mActions.clear()
      return@getNotificationBuilder notificationBuilder
    }
  }

  private fun getDownloadNotificationTitle(download: Download): String =
    fetchDownloadNotificationManager.getDownloadNotificationTitle(download)

  /**
   * Stops the foreground service, disposes of resources, and removes the Fetch listener.
   */
  private fun stopForegroundServiceForDownloads() {
    updaterJob?.cancel()
    fetch.removeListener(fetchListener)
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  companion object {
    const val STOP_DOWNLOAD_SERVICE = "stop_download_service"
  }
}
