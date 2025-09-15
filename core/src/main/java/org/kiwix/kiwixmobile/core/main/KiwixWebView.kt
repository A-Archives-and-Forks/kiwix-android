/*
 * Kiwix Android
 * Copyright (c) 2019 Kiwix <android.kiwix.org>
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
package org.kiwix.kiwixmobile.core.main

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.ContextMenu
import android.view.ViewGroup
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kiwix.kiwixmobile.core.BuildConfig
import org.kiwix.kiwixmobile.core.CoreApp.Companion.coreComponent
import org.kiwix.kiwixmobile.core.CoreApp.Companion.instance
import org.kiwix.kiwixmobile.core.R
import org.kiwix.kiwixmobile.core.extensions.closeFullScreenMode
import org.kiwix.kiwixmobile.core.extensions.showFullScreenMode
import org.kiwix.kiwixmobile.core.extensions.toast
import org.kiwix.kiwixmobile.core.reader.ZimReaderContainer
import org.kiwix.kiwixmobile.core.utils.LanguageUtils.Companion.getCurrentLocale
import org.kiwix.kiwixmobile.core.utils.SharedPreferenceUtil
import org.kiwix.kiwixmobile.core.utils.files.FileUtils
import org.kiwix.kiwixmobile.core.utils.files.Log
import org.kiwix.videowebview.VideoEnabledWebChromeClient.ToggledFullscreenCallback
import org.kiwix.videowebview.VideoEnabledWebView
import javax.inject.Inject

private const val INITIAL_SCALE = 100

@SuppressLint("ViewConstructor")
@Suppress("LongParameterList")
open class KiwixWebView @SuppressLint("SetJavaScriptEnabled") constructor(
  context: Context,
  private val callback: WebViewCallback,
  attrs: AttributeSet,
  videoView: ViewGroup?,
  private val coreWebViewClient: CoreWebViewClient,
  val sharedPreferenceUtil: SharedPreferenceUtil
) : VideoEnabledWebView(context, attrs) {
  @Inject
  lateinit var zimReaderContainer: ZimReaderContainer

  private var textZoomJob: Job? = null

  private fun setWindowVisibility(isFullScreen: Boolean) {
    (context as Activity).window.apply {
      if (isFullScreen) {
        showFullScreenMode(this)
      } else {
        closeFullScreenMode(this)
      }
    }
  }

  init {
    if (BuildConfig.DEBUG) {
      WebView.setWebContentsDebuggingEnabled(true)
    }
    coreComponent.inject(this)
    // Set the user agent to the current locale so it can be read with navigator.userAgent
    settings.apply {
      userAgentString = "${getCurrentLocale(context)}"
      domStorageEnabled = true
      javaScriptEnabled = true
      loadWithOverviewMode = true
      useWideViewPort = true
      builtInZoomControls = true
      displayZoomControls = false
      @Suppress("DEPRECATION")
      allowUniversalAccessFromFileURLs = true
    }
    setInitialScale(INITIAL_SCALE)
    clearCache(true)
    webViewClient = coreWebViewClient
    webChromeClient =
      KiwixWebChromeClient(callback, videoView, this).apply {
        setOnToggledFullscreen(
          object : ToggledFullscreenCallback {
            override fun toggledFullscreen(fullscreen: Boolean) {
              setWindowVisibility(fullscreen)
              callback.onFullscreenVideoToggled(fullscreen)
            }
          }
        )
      }
  }

  override fun performLongClick(): Boolean {
    val result = hitTestResult
    if (result.type == HitTestResult.SRC_ANCHOR_TYPE) {
      result.extra?.let {
        if (!coreWebViewClient.handleUnsupportedFiles(it)) {
          callback.webViewLongClick(it)
        }
      }
      return true
    }
    return super.performLongClick()
  }

  override fun onCreateContextMenu(menu: ContextMenu) {
    super.onCreateContextMenu(menu)
    val result = hitTestResult
    if (result.type == HitTestResult.IMAGE_TYPE ||
      result.type == HitTestResult.SRC_IMAGE_ANCHOR_TYPE
    ) {
      val saveMenu =
        menu.add(0, 1, 0, resources.getString(R.string.save_media))
      saveMenu.setOnMenuItemClickListener {
        val msg = SaveHandler(zimReaderContainer, sharedPreferenceUtil).obtainMessage()
        requestFocusNodeHref(msg)
        true
      }
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    // cancel any previous running job.
    textZoomJob?.cancel()
    textZoomJob = sharedPreferenceUtil.textZooms
      .onEach { settings.textZoom = it }
      .launchIn(CoroutineScope(SupervisorJob() + Dispatchers.Main))
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    textZoomJob?.cancel()
    textZoomJob = null
  }

  override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
    super.onScrollChanged(l, t, oldl, oldt)
    val windowHeight = if (measuredHeight > 0) measuredHeight else 1
    val pages = contentHeight / windowHeight
    val page = t / windowHeight
    callback.webViewPageChanged(page, pages)
  }

  internal class SaveHandler(
    private val zimReaderContainer: ZimReaderContainer,
    private val sharedPreferenceUtil: SharedPreferenceUtil
  ) :
    Handler(Looper.getMainLooper()) {
    @SuppressWarnings("InjectDispatcher")
    override fun handleMessage(msg: Message) {
      val url = msg.data.getString("url", null)
      val src = msg.data.getString("src", null)
      if (url == null && src == null) return
      CoroutineScope(Dispatchers.IO).launch {
        val savedFile =
          FileUtils.downloadFileFromUrl(url, src, zimReaderContainer, sharedPreferenceUtil)

        withContext(Dispatchers.Main) {
          savedFile?.let {
            instance.toast(instance.getString(R.string.save_media_saved, it.name))
            Log.e("savedFile", "handleMessage: ${savedFile.isFile} ${savedFile.path}")
          } ?: run {
            instance.toast(R.string.save_media_error)
          }
        }
      }
    }
  }
}
