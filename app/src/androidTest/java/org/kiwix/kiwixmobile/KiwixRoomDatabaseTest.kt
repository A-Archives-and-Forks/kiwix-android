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

package org.kiwix.kiwixmobile

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.kiwix.kiwixmobile.core.dao.RecentSearchRoomDao
import org.kiwix.kiwixmobile.core.dao.entities.RecentSearchRoomEntity
import org.kiwix.kiwixmobile.core.data.KiwixRoomDatabase

@RunWith(AndroidJUnit4::class)
class KiwixRoomDatabaseTest {
  private lateinit var recentSearchRoomDao: RecentSearchRoomDao
  private lateinit var db: KiwixRoomDatabase

  @After
  fun teardown() {
    db.close()
  }

  @Test
  fun insertAndGetRecentSearches() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, KiwixRoomDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val zimId = "34388L"
    val searchTerm = "title 1"
    recentSearchRoomDao = db.recentSearchRoomDao()
    val recentSearch = RecentSearchRoomEntity(zimId = zimId, searchTerm = searchTerm)
    recentSearchRoomDao.saveSearch(recentSearch.searchTerm, recentSearch.zimId)
    val recentSearches = recentSearchRoomDao.search(zimId).first()
    assertEquals(recentSearches.size, 1)
    assertEquals(recentSearch.searchTerm, recentSearches.first().searchTerm)
    assertEquals(recentSearch.zimId, recentSearches.first().zimId)
  }

  @Test
  fun deleteSearchString() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, KiwixRoomDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val zimId = "34388L"
    val searchTerm = "title 1"
    val recentSearch = RecentSearchRoomEntity(zimId = zimId, searchTerm = searchTerm)
    recentSearchRoomDao.saveSearch(recentSearch.searchTerm, recentSearch.zimId)
    var recentSearches = recentSearchRoomDao.search(searchTerm).first()
    assertEquals(recentSearches.size, 1)
    recentSearchRoomDao.deleteSearchString(searchTerm)
    recentSearches = recentSearchRoomDao.search(searchTerm).first()
    assertEquals(recentSearches.size, 0)
  }

  @Test
  fun deleteSearchHistory() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, KiwixRoomDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val zimId = "34388L"
    val searchTerm = "title 1"
    val searchTerm2 = "title 2"
    val recentSearch1 = RecentSearchRoomEntity(zimId = zimId, searchTerm = searchTerm)
    val recentSearch2 = RecentSearchRoomEntity(zimId = zimId, searchTerm = searchTerm2)
    recentSearchRoomDao.saveSearch(recentSearch1.searchTerm, recentSearch1.zimId)
    recentSearchRoomDao.saveSearch(recentSearch2.searchTerm, recentSearch2.zimId)
    var recentSearches = recentSearchRoomDao.search(zimId).first()
    assertEquals(recentSearches.size, 2)
    recentSearchRoomDao.deleteSearchHistory()
    recentSearches = recentSearchRoomDao.search(searchTerm).first()
    assertEquals(recentSearches.size, 0)
  }
}
