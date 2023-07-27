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

package org.kiwix.kiwixmobile.search

import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers.withId
import applyWithViewHierarchyPrinting
import com.adevinta.android.barista.interaction.BaristaSleepInteractions
import org.kiwix.kiwixmobile.BaseRobot
import org.kiwix.kiwixmobile.Findable.ViewId
import org.kiwix.kiwixmobile.core.R
import org.kiwix.kiwixmobile.testutils.TestUtils

fun search(func: SearchRobot.() -> Unit) = SearchRobot().applyWithViewHierarchyPrinting(func)

class SearchRobot : BaseRobot() {

  fun clickOnSearchItemInSearchList() {
    BaristaSleepInteractions.sleep(TestUtils.TEST_PAUSE_MS_FOR_SEARCH_TEST.toLong())
    isVisible(ViewId(R.id.search_list))
    onView(withId(R.id.search_list)).perform(
      actionOnItemAtPosition<RecyclerView.ViewHolder>(
        0,
        click()
      )
    )
  }

  fun checkZimFileSearchSuccessful(readerFragment: Int) {
    BaristaSleepInteractions.sleep(TestUtils.TEST_PAUSE_MS_FOR_SEARCH_TEST.toLong())
    isVisible(ViewId(readerFragment))
  }
}
