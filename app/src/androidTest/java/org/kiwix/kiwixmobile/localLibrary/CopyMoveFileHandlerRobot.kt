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

package org.kiwix.kiwixmobile.localLibrary

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.web.sugar.Web
import androidx.test.espresso.web.webdriver.DriverAtoms
import androidx.test.espresso.web.webdriver.Locator
import applyWithViewHierarchyPrinting
import org.kiwix.kiwixmobile.BaseRobot
import org.kiwix.kiwixmobile.core.R
import org.kiwix.kiwixmobile.core.ui.components.STORAGE_DEVICE_ITEM_TESTING_TAG
import org.kiwix.kiwixmobile.core.utils.dialog.ALERT_DIALOG_CONFIRM_BUTTON_TESTING_TAG
import org.kiwix.kiwixmobile.core.utils.dialog.ALERT_DIALOG_DISMISS_BUTTON_TESTING_TAG
import org.kiwix.kiwixmobile.core.utils.dialog.ALERT_DIALOG_MESSAGE_TEXT_TESTING_TAG
import org.kiwix.kiwixmobile.nav.destination.library.local.NO_FILE_TEXT_TESTING_TAG
import org.kiwix.kiwixmobile.storage.STORAGE_SELECTION_DIALOG_TITLE_TESTING_TAG
import org.kiwix.kiwixmobile.testutils.TestUtils.TEST_PAUSE_MS_FOR_DOWNLOAD_TEST
import org.kiwix.kiwixmobile.testutils.TestUtils.testFlakyView
import org.kiwix.kiwixmobile.testutils.TestUtils.waitUntilTimeout

fun copyMoveFileHandler(func: CopyMoveFileHandlerRobot.() -> Unit) =
  CopyMoveFileHandlerRobot().applyWithViewHierarchyPrinting(func)

class CopyMoveFileHandlerRobot : BaseRobot() {
  fun assertCopyMoveDialogDisplayed(composeTestRule: ComposeContentTestRule) {
    composeTestRule.apply {
      waitUntil(TEST_PAUSE_MS_FOR_DOWNLOAD_TEST.toLong()) {
        onNodeWithTag(ALERT_DIALOG_MESSAGE_TEXT_TESTING_TAG).isDisplayed()
      }
      onNodeWithTag(ALERT_DIALOG_MESSAGE_TEXT_TESTING_TAG)
        .assertTextEquals(context.getString(R.string.copy_move_files_dialog_description))
    }
  }

  fun assertCopyMoveDialogNotDisplayed(composeTestRule: ComposeContentTestRule) {
    testFlakyView({
      composeTestRule.apply {
        waitUntilTimeout()
        onNodeWithTag(ALERT_DIALOG_MESSAGE_TEXT_TESTING_TAG)
          .assertDoesNotExist()
      }
    })
  }

  fun assertStorageSelectionDialogDisplayed(composeTestRule: ComposeContentTestRule) {
    testFlakyView({
      composeTestRule.apply {
        waitUntilTimeout()
        onNodeWithTag(STORAGE_SELECTION_DIALOG_TITLE_TESTING_TAG)
          .assertTextEquals(context.getString(R.string.choose_storage_to_copy_move_zim_file))
      }
    })
  }

  fun clickOnInternalStorage(composeTestRule: ComposeContentTestRule) {
    testFlakyView({
      composeTestRule.apply {
        waitUntilTimeout()
        onAllNodesWithTag(STORAGE_DEVICE_ITEM_TESTING_TAG)[0].performClick()
      }
    })
  }

  fun clickOnCopy(composeTestRule: ComposeContentTestRule) {
    testFlakyView({
      composeTestRule.apply {
        waitUntilTimeout()
        onNodeWithTag(ALERT_DIALOG_CONFIRM_BUTTON_TESTING_TAG)
          .assertTextEquals(context.getString(R.string.action_copy).uppercase())
          .performClick()
      }
    })
  }

  fun clickOnMove(composeTestRule: ComposeContentTestRule) {
    testFlakyView({
      composeTestRule.apply {
        waitForIdle()
        onNodeWithTag(ALERT_DIALOG_DISMISS_BUTTON_TESTING_TAG)
          .assertTextEquals(context.getString(R.string.move).uppercase())
          .performClick()
      }
    })
  }

  fun clickOnYesButton(composeTestRule: ComposeContentTestRule) {
    testFlakyView({
      composeTestRule.apply {
        waitForIdle()
        onNodeWithTag(ALERT_DIALOG_CONFIRM_BUTTON_TESTING_TAG).performClick()
      }
    })
  }

  fun assertZimFileCopiedAndShowingIntoTheReader(composeTestRule: ComposeContentTestRule) {
    testFlakyView({
      composeTestRule.waitUntilTimeout()
      composeTestRule.mainClock.advanceTimeByFrame()
      Web.onWebView()
        .withElement(
          DriverAtoms.findElement(
            Locator.XPATH,
            "//*[contains(text(), 'Android_(operating_system)')]"
          )
        )
    })
  }

  fun assertZimFileAddedInTheLocalLibrary(composeTestRule: ComposeContentTestRule) {
    testFlakyView({
      try {
        composeTestRule.apply {
          waitUntilTimeout(TEST_PAUSE_MS_FOR_DOWNLOAD_TEST.toLong())
          mainClock.advanceTimeByFrame()
          composeTestRule.onNodeWithTag(NO_FILE_TEXT_TESTING_TAG).assertIsDisplayed()
          throw RuntimeException("ZimFile not added in the local library")
        }
      } catch (_: AssertionError) {
        // do nothing zim file is added in the local library
      }
    })
  }

  fun assertFileCopyMoveErrorDialogDisplayed(composeTestRule: ComposeContentTestRule) {
    composeTestRule.apply {
      waitUntil(TEST_PAUSE_MS_FOR_DOWNLOAD_TEST.toLong()) {
        onNodeWithTag(ALERT_DIALOG_MESSAGE_TEXT_TESTING_TAG).isDisplayed()
      }
      // We are checking for text contains as ZIM file name is dynamic.
      onNodeWithTag(ALERT_DIALOG_MESSAGE_TEXT_TESTING_TAG)
        .assertTextContains(
          "Do you want to continue with other ZIM files?",
          substring = true,
          ignoreCase = true
        )
    }
  }
}
