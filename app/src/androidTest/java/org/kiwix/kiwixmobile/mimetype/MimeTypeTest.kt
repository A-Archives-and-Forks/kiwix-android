/*
 * Kiwix Android
 * Copyright (c) 2022 Kiwix <android.kiwix.org>
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

package org.kiwix.kiwixmobile.mimetype

import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.kiwix.kiwixmobile.BaseActivityTest
import org.kiwix.kiwixmobile.core.NightModeConfig
import org.kiwix.kiwixmobile.core.reader.ZimFileReader
import org.kiwix.kiwixmobile.core.utils.SharedPreferenceUtil
import org.kiwix.kiwixmobile.main.KiwixMainActivity
import org.kiwix.kiwixmobile.testutils.TestUtils.closeSystemDialogs
import org.kiwix.kiwixmobile.testutils.TestUtils.isSystemUINotRespondingDialogVisible
import org.kiwix.libzim.Archive
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class MimeTypeTest : BaseActivityTest() {

  @Before
  override fun waitForIdle() {
    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).apply {
      if (isSystemUINotRespondingDialogVisible(this)) {
        closeSystemDialogs(context)
      }
      waitForIdle()
    }
    PreferenceManager.getDefaultSharedPreferences(context).edit {
      putBoolean(SharedPreferenceUtil.PREF_SHOW_INTRO, false)
      putBoolean(SharedPreferenceUtil.PREF_WIFI_ONLY, false)
      putBoolean(SharedPreferenceUtil.PREF_PLAY_STORE_RESTRICTION, false)
      putBoolean(SharedPreferenceUtil.PREF_PLAY_STORE_RESTRICTION, false)
    }
    activityScenario = ActivityScenario.launch(KiwixMainActivity::class.java).apply {
      moveToState(Lifecycle.State.RESUMED)
    }
  }

  @Test
  fun testMimeType() {
    val loadFileStream = MimeTypeTest::class.java.classLoader.getResourceAsStream("testzim.zim")
    val zimFile = File(context.cacheDir, "testzim.zim")
    if (zimFile.exists()) zimFile.delete()
    zimFile.createNewFile()
    loadFileStream.use { inputStream ->
      val outputStream: OutputStream = FileOutputStream(zimFile)
      outputStream.use { it ->
        val buffer = ByteArray(1024)
        var length: Int
        while (inputStream.read(buffer).also { length = it } > 0) {
          it.write(buffer, 0, length)
        }
      }
    }
    val zimFileReader = ZimFileReader(
      zimFile,
      null,
      null,
      Archive(zimFile.canonicalPath),
      NightModeConfig(SharedPreferenceUtil(context), context)
    )
    zimFileReader.getRandomArticleUrl()?.let {
      val mimeType = zimFileReader.getMimeTypeFromUrl(it)
      if (mimeType?.contains("^([^ ]+).*$") == true || mimeType?.contains(";") == true) {
        Assert.fail(
          "Unable to get mime type from zim file. File = " +
            " $zimFile and url of article = $it"
        )
      }
    } ?: kotlin.run {
      Assert.fail("Unable to get article from zim file $zimFile")
    }
    // test mimetypes for some actual url
    Assert.assertEquals(
      "text/html",
      zimFileReader.getMimeTypeFromUrl("https://kiwix.app/A/index.html")
    )
    Assert.assertEquals(
      "text/css",
      zimFileReader.getMimeTypeFromUrl("https://kiwix.app/-/assets/style1.css")
    )
    // test mimetype for invalid url
    Assert.assertEquals(null, zimFileReader.getMimeTypeFromUrl("https://kiwix.app/A/test.html"))
    // dispose the ZimFileReader
    zimFileReader.dispose()
  }
}
