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

package org.kiwix.kiwixmobile.localFileTransfer

import android.Manifest
import android.app.Instrumentation
import android.content.Context
import android.os.Build
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import leakcanary.LeakAssertions
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.kiwix.kiwixmobile.R
import org.kiwix.kiwixmobile.VERY_LONG_WAIT
import org.kiwix.kiwixmobile.core.utils.SharedPreferenceUtil
import org.kiwix.kiwixmobile.main.KiwixMainActivity
import org.kiwix.kiwixmobile.nav.destination.library.library
import org.kiwix.kiwixmobile.testutils.RetryRule
import org.kiwix.kiwixmobile.testutils.TestUtils

class LocalFileTransferTest {
  @Rule
  @JvmField
  var retryRule = RetryRule()

  private lateinit var context: Context
  private lateinit var activityScenario: ActivityScenario<KiwixMainActivity>

  private val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    arrayOf(
      Manifest.permission.POST_NOTIFICATIONS,
      Manifest.permission.NEARBY_WIFI_DEVICES
    )
  } else {
    arrayOf(
      Manifest.permission.READ_EXTERNAL_STORAGE,
      Manifest.permission.WRITE_EXTERNAL_STORAGE,
      Manifest.permission.ACCESS_FINE_LOCATION
    )
  }

  @Rule
  @JvmField
  var permissionRules: GrantPermissionRule =
    GrantPermissionRule.grant(*permissions)

  private val instrumentation: Instrumentation by lazy {
    InstrumentationRegistry.getInstrumentation()
  }

  @Before
  fun setup() {
    context = instrumentation.targetContext.applicationContext
    UiDevice.getInstance(instrumentation).apply {
      if (TestUtils.isSystemUINotRespondingDialogVisible(this)) {
        TestUtils.closeSystemDialogs(context)
      }
      waitForIdle(VERY_LONG_WAIT)
    }
  }

  @Test
  fun localFileTransfer() {
    shouldShowShowCaseFeatureToUser(false)
    activityScenario = ActivityScenario.launch(KiwixMainActivity::class.java).apply {
      moveToState(Lifecycle.State.RESUMED)
    }
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
      activityScenario.onActivity {
        it.navigate(R.id.libraryFragment)
      }
      library {
        assertGetZimNearbyDeviceDisplayed()
        clickFileTransferIcon {
          assertReceiveFileTitleVisible()
          assertSearchDeviceMenuItemVisible()
          clickOnSearchDeviceMenuItem()
          assertLocalFileTransferScreenVisible()
          pressBack()
          assertLocalLibraryVisible()
        }
      }
      LeakAssertions.assertNoLeaks()
    }
  }

  @Test
  fun showCaseFeature() {
    shouldShowShowCaseFeatureToUser(true, isResetShowCaseId = true)
    activityScenario = ActivityScenario.launch(KiwixMainActivity::class.java).apply {
      moveToState(Lifecycle.State.RESUMED)
      onActivity {
        it.navigate(R.id.libraryFragment)
      }
    }
    library {
      assertGetZimNearbyDeviceDisplayed()
      clickFileTransferIcon {
        assertClickNearbyDeviceMessageVisible()
        clickOnGotItButton()
        assertDeviceNameMessageVisible()
        clickOnGotItButton()
        assertNearbyDeviceListMessageVisible()
        clickOnGotItButton()
        assertTransferZimFilesListMessageVisible()
        clickOnGotItButton()
        pressBack()
        assertGetZimNearbyDeviceDisplayed()
      }
    }
    LeakAssertions.assertNoLeaks()
  }

  @Test
  fun testShowCaseFeatureShowOnce() {
    shouldShowShowCaseFeatureToUser(true)
    activityScenario = ActivityScenario.launch(KiwixMainActivity::class.java).apply {
      moveToState(Lifecycle.State.RESUMED)
      onActivity {
        it.navigate(R.id.libraryFragment)
      }
    }
    library {
      // test show case view show once.
      clickFileTransferIcon(LocalFileTransferRobot::assertClickNearbyDeviceMessageNotVisible)
    }
  }

  @After
  fun setIsTestPreference() {
    PreferenceManager.getDefaultSharedPreferences(context).edit {
      putBoolean(SharedPreferenceUtil.PREF_IS_TEST, false)
      putBoolean(SharedPreferenceUtil.PREF_SHOW_SHOWCASE, true)
    }
  }

  private fun shouldShowShowCaseFeatureToUser(
    shouldShowShowCase: Boolean,
    isResetShowCaseId: Boolean = false
  ) {
    PreferenceManager.getDefaultSharedPreferences(context).edit {
      putBoolean(SharedPreferenceUtil.PREF_SHOW_INTRO, false)
      putBoolean(SharedPreferenceUtil.PREF_WIFI_ONLY, false)
      putBoolean(SharedPreferenceUtil.PREF_IS_TEST, true)
      putBoolean(SharedPreferenceUtil.PREF_SHOW_SHOWCASE, shouldShowShowCase)
      putBoolean(SharedPreferenceUtil.PREF_PLAY_STORE_RESTRICTION, false)
    }
    if (isResetShowCaseId) {
      // To clear showCaseID to ensure the showcase view will show.
      uk.co.deanwild.materialshowcaseview.PrefsManager.resetAll(context)
    }
  }
}
