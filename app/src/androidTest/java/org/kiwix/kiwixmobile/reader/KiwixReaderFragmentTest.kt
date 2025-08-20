/*
 * Kiwix Android
 * Copyright (c) 2023 Kiwix <android.kiwix.org>
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

package org.kiwix.kiwixmobile.reader

import android.os.Build
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavOptions
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.accessibility.AccessibilityChecks
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResultUtils.matchesCheck
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResultUtils.matchesViews
import com.google.android.apps.common.testing.accessibility.framework.checks.DuplicateClickableBoundsCheck
import com.google.android.apps.common.testing.accessibility.framework.checks.SpeakableTextPresentCheck
import com.google.android.apps.common.testing.accessibility.framework.checks.TouchTargetSizeCheck
import leakcanary.LeakAssertions
import okhttp3.Request
import okhttp3.ResponseBody
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.anyOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.kiwix.kiwixmobile.BaseActivityTest
import org.kiwix.kiwixmobile.core.main.CoreMainActivity
import org.kiwix.kiwixmobile.core.utils.LanguageUtils.Companion.handleLocaleChange
import org.kiwix.kiwixmobile.core.utils.SharedPreferenceUtil
import org.kiwix.kiwixmobile.core.utils.TestingUtils.COMPOSE_TEST_RULE_ORDER
import org.kiwix.kiwixmobile.core.utils.TestingUtils.RETRY_RULE_ORDER
import org.kiwix.kiwixmobile.main.KiwixMainActivity
import org.kiwix.kiwixmobile.page.bookmarks.bookmarks
import org.kiwix.kiwixmobile.testutils.RetryRule
import org.kiwix.kiwixmobile.testutils.TestUtils
import org.kiwix.kiwixmobile.testutils.TestUtils.closeSystemDialogs
import org.kiwix.kiwixmobile.testutils.TestUtils.getOkkHttpClientForTesting
import org.kiwix.kiwixmobile.testutils.TestUtils.isSystemUINotRespondingDialogVisible
import org.kiwix.kiwixmobile.ui.KiwixDestination
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.URI

class KiwixReaderFragmentTest : BaseActivityTest() {
  @Rule(order = RETRY_RULE_ORDER)
  @JvmField
  val retryRule = RetryRule()

  @get:Rule(order = COMPOSE_TEST_RULE_ORDER)
  val composeTestRule = createComposeRule()

  private lateinit var kiwixMainActivity: KiwixMainActivity

  @Before
  override fun waitForIdle() {
    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).apply {
      if (isSystemUINotRespondingDialogVisible(this)) {
        closeSystemDialogs(context, this)
      }
      waitForIdle()
    }
    PreferenceManager.getDefaultSharedPreferences(context).edit {
      putBoolean(SharedPreferenceUtil.PREF_SHOW_INTRO, false)
      putBoolean(SharedPreferenceUtil.PREF_WIFI_ONLY, false)
      putBoolean(SharedPreferenceUtil.PREF_IS_TEST, true)
      putString(SharedPreferenceUtil.PREF_LANG, "en")
    }
    activityScenario =
      ActivityScenario.launch(KiwixMainActivity::class.java).apply {
        moveToState(Lifecycle.State.RESUMED)
        onActivity {
          handleLocaleChange(
            it,
            "en",
            SharedPreferenceUtil(context)
          )
        }
      }
  }

  init {
    AccessibilityChecks.enable().apply {
      setRunChecksFromRootView(true)
      setSuppressingResultMatcher(
        anyOf(
          allOf(
            matchesCheck(TouchTargetSizeCheck::class.java),
            matchesViews(withContentDescription("More options"))
          ),
          matchesCheck(SpeakableTextPresentCheck::class.java),
          matchesCheck(DuplicateClickableBoundsCheck::class.java)
        )
      )
    }
  }

  @Test
  fun testTabsRestoredAfterNavigatingLeftDrawerScreens() {
    activityScenario.onActivity {
      kiwixMainActivity = it
      kiwixMainActivity.navigate(KiwixDestination.Library.route)
    }
    composeTestRule.waitForIdle()
    val zimFile = getLocalZIMFile()
    openKiwixReaderFragmentWithFile(zimFile)
    reader {
      checkZimFileLoadedSuccessful(composeTestRule)
      // open a new tab
      openSearchWithQuery("Android", zimFile)
      openAndroidArticleInNewTab(composeTestRule)
      checkZimFileLoadedSuccessful(composeTestRule)
      // open bookmark screen.
      bookmarks {
        openBookmarkScreen(kiwixMainActivity as CoreMainActivity, composeTestRule)
        assertBookMarksDisplayed(composeTestRule)
      }
      composeTestRule.waitForIdle()
      pressBack()
      assertTabsRestored(composeTestRule)
    }
  }

  @Test
  fun testTabsRestoredWhenNavigatingToOtherScreenViaBottomAppBar() {
    activityScenario.onActivity {
      kiwixMainActivity = it
      kiwixMainActivity.navigate(KiwixDestination.Library.route)
    }
    composeTestRule.waitForIdle()
    val zimFile = getLocalZIMFile()
    openKiwixReaderFragmentWithFile(zimFile)
    reader {
      checkZimFileLoadedSuccessful(composeTestRule)
      // open a new tab
      openSearchWithQuery("Android", zimFile)
      openAndroidArticleInNewTab(composeTestRule)
      checkZimFileLoadedSuccessful(composeTestRule)
      // open local library screen.
      openLocalLibraryScreenViaBottomAppBar(composeTestRule)
      composeTestRule.waitForIdle()
      // press back to come back to reader screen.
      pressBack()
      assertTabsRestored(composeTestRule)
    }
  }

  @Test
  fun testTabClosedDialog() {
    activityScenario.onActivity {
      kiwixMainActivity = it
      kiwixMainActivity.navigate(KiwixDestination.Library.route)
    }
    composeTestRule.waitForIdle()
    openKiwixReaderFragmentWithFile(getLocalZIMFile())
    composeTestRule.waitForIdle()
    reader {
      checkZimFileLoadedSuccessful(composeTestRule)
      clickOnTabIcon(composeTestRule)
      clickOnClosedAllTabsButton(composeTestRule)
      clickOnUndoButton(composeTestRule)
      assertTabRestored(composeTestRule)
      pressBack()
      checkZimFileLoadedSuccessful(composeTestRule)
    }
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1 && Build.VERSION.SDK_INT != Build.VERSION_CODES.TIRAMISU) {
      // temporary disabled on Android 25
      LeakAssertions.assertNoLeaks()
    }
  }

  @Test
  fun testZimFileRendering() {
    activityScenario.onActivity {
      kiwixMainActivity = it
      kiwixMainActivity.navigate(KiwixDestination.Library.route)
    }
    composeTestRule.waitForIdle()
    val downloadingZimFile = getDownloadingZimFile()
    getOkkHttpClientForTesting().newCall(downloadRequest()).execute().use { response ->
      if (response.isSuccessful) {
        response.body?.let { responseBody ->
          writeZimFileData(responseBody, downloadingZimFile)
        }
      } else {
        throw RuntimeException(
          "Download Failed. Error: ${response.message}\n" +
            " Status Code: ${response.code}"
        )
      }
    }
    openKiwixReaderFragmentWithFile(downloadingZimFile)
    composeTestRule.waitForIdle()
    reader {
      checkZimFileLoadedSuccessful(composeTestRule)
      clickOnTabIcon(composeTestRule)
      clickOnTabIcon(composeTestRule)
      // test the whole welcome page is loaded or not
      assertArticleLoaded("Hydrogène")
      assertArticleLoaded("Automobile")
      assertArticleLoaded("Agriculture")
      assertArticleLoaded("Dioxyde de carbone")
      assertArticleLoaded("Développement durable")
      assertArticleLoaded("Précipitations")
      assertArticleLoaded("Énergie renouvelable")
      assertArticleLoaded("Cyclone tropical")
      assertArticleLoaded("Charbon")
      assertArticleLoaded("Riz")
      assertArticleLoaded("Fromage")
      assertArticleLoaded("Gaz naturel")
      assertArticleLoaded("Transport en commun")
      assertArticleLoaded("Inondation")
      assertArticleLoaded("Ammoniac")
      assertArticleLoaded("Énergie hydroélectrique")
      assertArticleLoaded("Nuage")
      assertArticleLoaded("Essence (hydrocarbure)")
      assertArticleLoaded("Glacier")
      assertArticleLoaded("Ciment")
      assertArticleLoaded("Canicule")
      assertArticleLoaded("Énergie éolienne")
      assertArticleLoaded("Ours blanc")
      assertArticleLoaded("Camion")
      assertArticleLoaded("Glaciation")
      assertArticleLoaded("Engrais")
      assertArticleLoaded("Greenpeace")
      assertArticleLoaded("Déforestation")
      assertArticleLoaded("Bos taurus")
      assertArticleLoaded("Agriculteur")
      assertArticleLoaded("Baleine")
      assertArticleLoaded("Catastrophe naturelle")
      assertArticleLoaded("Tropique")
      assertArticleLoaded("Irrigation")
      assertArticleLoaded("Classification de Köppen")
      assertArticleLoaded("Effet de serre")
      assertArticleLoaded("Géothermie")
      assertArticleLoaded("Combustible fossile")
      assertArticleLoaded("Tourbe")
      assertArticleLoaded("Chanvre")
      assertArticleLoaded("Greta Thunberg")
      assertArticleLoaded("Zone humide")
      assertArticleLoaded("Al Gore")
      assertArticleLoaded("Albédo")
      // click on a article and see it is loaded or not
      clickOnArticle("Transport en commun")
      assertArticleLoaded("transport en commun")
    }
  }

  private fun downloadRequest() =
    Request.Builder()
      .url(
        URI.create(
          "https://download.kiwix.org/zim/wikipedia_fr_climate_change_mini.zim"
        ).toURL()
      ).build()

  private fun getDownloadingZimFile(): File {
    val zimFile = File(context.cacheDir, "klimawandel.zim")
    if (zimFile.exists()) zimFile.delete()
    zimFile.createNewFile()
    return zimFile
  }

  private fun writeZimFileData(responseBody: ResponseBody, file: File) {
    FileOutputStream(file).use { outputStream ->
      responseBody.byteStream().use { inputStream ->
        val buffer = ByteArray(4096)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
          outputStream.write(buffer, 0, bytesRead)
        }
        outputStream.flush()
      }
    }
  }

  private fun getLocalZIMFile(): File {
    val loadFileStream =
      KiwixReaderFragmentTest::class.java.classLoader.getResourceAsStream("testzim.zim")
    val zimFile =
      File(
        context.getExternalFilesDirs(null)[0],
        "testzim.zim"
      )
    if (zimFile.exists()) zimFile.delete()
    zimFile.createNewFile()
    loadFileStream.use { inputStream ->
      val outputStream: OutputStream = FileOutputStream(zimFile)
      outputStream.use { it ->
        val buffer = ByteArray(inputStream.available())
        var length: Int
        while (inputStream.read(buffer).also { length = it } > 0) {
          it.write(buffer, 0, length)
        }
      }
    }
    return zimFile
  }

  private fun openSearchWithQuery(query: String = "", zimFile: File) {
    UiThreadStatement.runOnUiThread {
      if (zimFile.canRead()) {
        kiwixMainActivity.openSearch(searchString = query)
      } else {
        throw RuntimeException(
          "File $zimFile is not readable." +
            " Original File $zimFile is readable = ${zimFile.canRead()}" +
            " Size ${zimFile.length()}"
        )
      }
    }
  }

  private fun openKiwixReaderFragmentWithFile(zimFile: File) {
    UiThreadStatement.runOnUiThread {
      val navOptions = NavOptions.Builder()
        .setPopUpTo(KiwixDestination.Reader.route, false)
        .build()
      kiwixMainActivity.navigate(
        KiwixDestination.Reader.createRoute(zimFileUri = zimFile.toUri().toString()),
        navOptions
      )
    }
  }

  @After
  fun finish() {
    TestUtils.deleteTemporaryFilesOfTestCases(context)
  }
}
