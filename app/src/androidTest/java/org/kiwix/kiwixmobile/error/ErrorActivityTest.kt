/*
 * Kiwix Android
 * Copyright (c) 2025 Kiwix <android.kiwix.org>
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

package org.kiwix.kiwixmobile.error

import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.kiwix.kiwixmobile.BaseActivityTest
import org.kiwix.kiwixmobile.core.utils.LanguageUtils.Companion.handleLocaleChange
import org.kiwix.kiwixmobile.core.utils.SharedPreferenceUtil
import org.kiwix.kiwixmobile.core.utils.TestingUtils.COMPOSE_TEST_RULE_ORDER
import org.kiwix.kiwixmobile.core.utils.TestingUtils.RETRY_RULE_ORDER
import org.kiwix.kiwixmobile.main.KiwixMainActivity
import org.kiwix.kiwixmobile.testutils.RetryRule
import org.kiwix.kiwixmobile.testutils.TestUtils.closeSystemDialogs
import org.kiwix.kiwixmobile.testutils.TestUtils.isSystemUINotRespondingDialogVisible
import org.kiwix.kiwixmobile.ui.KiwixDestination

class ErrorActivityTest : BaseActivityTest() {
  @Rule(order = RETRY_RULE_ORDER)
  @JvmField
  val retryRule = RetryRule()

  @get:Rule(order = COMPOSE_TEST_RULE_ORDER)
  val composeTestRule = createComposeRule()

  @Before
  override fun waitForIdle() {
    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).apply {
      if (isSystemUINotRespondingDialogVisible(this)) {
        closeSystemDialogs(context, this)
      }
      waitForIdle()
    }
    PreferenceManager.getDefaultSharedPreferences(context)
      .edit {
        putBoolean(SharedPreferenceUtil.PREF_SHOW_INTRO, false)
        putBoolean(SharedPreferenceUtil.PREF_IS_TEST, true)
        putBoolean(SharedPreferenceUtil.PREF_WIFI_ONLY, false)
        putString(SharedPreferenceUtil.PREF_LANG, "en")
        putBoolean(SharedPreferenceUtil.PREF_SCAN_FILE_SYSTEM_DIALOG_SHOWN, true)
        putBoolean(SharedPreferenceUtil.PREF_IS_FIRST_RUN, false)
        putLong(
          SharedPreferenceUtil.PREF_LAST_DONATION_POPUP_SHOWN_IN_MILLISECONDS,
          System.currentTimeMillis()
        )
      }
    activityScenario =
      ActivityScenario.launch(KiwixMainActivity::class.java).apply {
        moveToState(Lifecycle.State.RESUMED)
        onActivity {
          handleLocaleChange(
            it,
            "en",
            SharedPreferenceUtil(context).apply {
              lastDonationPopupShownInMilliSeconds = System.currentTimeMillis()
            }
          )
        }
      }
    composeTestRule.enableAccessibilityChecks()
  }

  @Test
  fun verifyErrorActivity() {
    activityScenario.onActivity {
      it.navigate(KiwixDestination.Help.route)
    }
    errorActivity {
      assertSendDiagnosticReportDisplayed(composeTestRule)
      clickOnSendDiagnosticReport(composeTestRule)
      assertErrorActivityDisplayed(composeTestRule)
      // Click on "No, Thanks" button to see it's functionality working or not.
      clickOnNoThanksButton(composeTestRule)
      // Handle the app restart explicitly. Since test case does not handle the app restart.
      activityScenario = ActivityScenario.launch(KiwixMainActivity::class.java).onActivity {
        it.navigate(KiwixDestination.Help.route)
      }
      // Assert HelpFragment is visible or not after clicking on the "No, Thanks" button.
      assertSendDiagnosticReportDisplayed(composeTestRule)
      // Again click on "Send diagnostic report" button to open the ErrorActivity.
      clickOnSendDiagnosticReport(composeTestRule)
      assertErrorActivityDisplayed(composeTestRule)
      // Check diagnostic details are displayed or not.
      assertDetailsIncludedInErrorReportDisplayed(composeTestRule)
      // Click on "Send details" button.
      clickOnSendDetailsButton(composeTestRule)
    }
    composeTestRule.onRoot().tryPerformAccessibilityChecks()
  }
}
