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

package org.kiwix.kiwixmobile.objectboxmigration.entities

import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import org.kiwix.kiwixmobile.core.page.history.adapter.HistoryListItem
import org.kiwix.kiwixmobile.core.reader.ZimReaderSource

@Entity
data class HistoryEntity(
  @Id var id: Long = 0L,
  val zimId: String,
  val zimName: String,
  // keep this to handle previously saved history
  val zimFilePath: String?,
  @Convert(converter = ZimSourceConverter::class, dbType = String::class)
  var zimReaderSource: ZimReaderSource?,
  val favicon: String?,
  val historyUrl: String,
  val historyTitle: String,
  val dateString: String,
  val timeStamp: Long
) {
  constructor(historyItem: HistoryListItem.HistoryItem) : this(
    historyItem.databaseId,
    historyItem.zimId,
    historyItem.zimName,
    // pass null for new history items
    null,
    historyItem.zimReaderSource,
    historyItem.favicon,
    historyItem.historyUrl,
    historyItem.title,
    historyItem.dateString,
    historyItem.timeStamp
  )
}
