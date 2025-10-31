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
package org.kiwix.kiwixmobile.help

import android.os.Build
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.IdlingRegistry
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResultUtils.matchesCheck
import com.google.android.apps.common.testing.accessibility.framework.checks.DuplicateClickableBoundsCheck
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import leakcanary.LeakAssertions
import org.hamcrest.Matchers.anyOf
import org.junit.After
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
import org.kiwix.kiwixmobile.utils.KiwixIdlingResource

class HelpFragmentTest : BaseActivityTest() {
  private lateinit var sharedPreferenceUtil: SharedPreferenceUtil

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
    PreferenceManager.getDefaultSharedPreferences(
      InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    ).edit {
      putBoolean(SharedPreferenceUtil.PREF_SCAN_FILE_SYSTEM_DIALOG_SHOWN, true)
      putBoolean(SharedPreferenceUtil.PREF_IS_FIRST_RUN, false)
      putBoolean(SharedPreferenceUtil.PREF_IS_TEST, true)
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
    val accessibilityValidator = AccessibilityValidator().setRunChecksFromRootView(true).apply {
      setSuppressingResultMatcher(
        anyOf(
          matchesCheck(DuplicateClickableBoundsCheck::class.java)
        )
      )
    }
    composeTestRule.enableAccessibilityChecks(accessibilityValidator)
  }

  @Test
  fun verifyHelpActivity() {
    setShowCopyMoveToPublicDirectory(false)
    activityScenario.onActivity {
      it.navigate(KiwixDestination.Help.route)
    }
    help {
      clickOnWhatDoesKiwixDo(composeTestRule)
      assertWhatDoesKiwixDoIsExpanded(composeTestRule)
      clickOnWhatDoesKiwixDo(composeTestRule)
      clickOnWhereIsContent(composeTestRule)
      assertWhereIsContentIsExpanded(composeTestRule)
      clickOnWhereIsContent(composeTestRule)
      clickOnHowToUpdateContent(composeTestRule)
      assertHowToUpdateContentIsExpanded(composeTestRule)
      clickOnHowToUpdateContent(composeTestRule)
      assertWhyCopyMoveFilesToAppPublicDirectoryIsNotVisible(composeTestRule)
    }
    composeTestRule.onRoot().tryPerformAccessibilityChecks()
    LeakAssertions.assertNoLeaks()
  }

  @Test
  fun verifyHelpActivityWithPlayStoreRestriction() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      setShowCopyMoveToPublicDirectory(true)
      activityScenario.onActivity {
        it.navigate(KiwixDestination.Help.route)
      }
      help {
        clickOnWhatDoesKiwixDo(composeTestRule)
        assertWhatDoesKiwixDoIsExpanded(composeTestRule)
        clickOnWhatDoesKiwixDo(composeTestRule)
        clickOnWhereIsContent(composeTestRule)
        assertWhereIsContentIsExpanded(composeTestRule)
        clickOnWhereIsContent(composeTestRule)
        clickOnHowToUpdateContent(composeTestRule)
        assertHowToUpdateContentIsExpanded(composeTestRule)
        clickOnHowToUpdateContent(composeTestRule)
        clickWhyCopyMoveFilesToAppPublicDirectory(composeTestRule)
        assertWhyCopyMoveFilesToAppPublicDirectoryIsExpanded(composeTestRule)
        clickWhyCopyMoveFilesToAppPublicDirectory(composeTestRule)
      }
      composeTestRule.onRoot().tryPerformAccessibilityChecks()
      LeakAssertions.assertNoLeaks()
    }
  }

  private fun setShowCopyMoveToPublicDirectory(showRestriction: Boolean) {
    context.let {
      sharedPreferenceUtil =
        SharedPreferenceUtil(it).apply {
          setIntroShown()
          putPrefWifiOnly(false)
          setIsPlayStoreBuildType(showRestriction)
          prefIsTest = true
          putPrefLanguage("en")
        }
    }
  }

  @After
  fun finish() {
    IdlingRegistry.getInstance().unregister(KiwixIdlingResource.getInstance())
  }
}
