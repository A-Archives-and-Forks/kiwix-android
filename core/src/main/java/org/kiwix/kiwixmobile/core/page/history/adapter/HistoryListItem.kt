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
package org.kiwix.kiwixmobile.core.page.history.adapter

import org.kiwix.kiwixmobile.core.dao.entities.HistoryRoomEntity
import org.kiwix.kiwixmobile.core.page.adapter.Page
import org.kiwix.kiwixmobile.core.page.adapter.PageRelated
import org.kiwix.kiwixmobile.core.reader.ZimFileReader
import org.kiwix.kiwixmobile.core.reader.ZimReaderSource

sealed class HistoryListItem : PageRelated {
  data class HistoryItem constructor(
    val databaseId: Long = 0L,
    override val zimId: String,
    val zimName: String,
    override val zimReaderSource: ZimReaderSource?,
    override val favicon: String?,
    val historyUrl: String,
    override val title: String,
    val dateString: String,
    val timeStamp: Long,
    override var isSelected: Boolean = false,
    override val id: Long = databaseId,
    override val url: String = historyUrl
  ) : HistoryListItem(), Page {
    constructor(
      url: String,
      title: String,
      dateString: String,
      timeStamp: Long,
      zimFileReader: ZimFileReader
    ) : this(
      zimId = zimFileReader.id,
      zimName = zimFileReader.name,
      zimReaderSource = zimFileReader.zimReaderSource,
      favicon = zimFileReader.favicon,
      historyUrl = url,
      title = title,
      dateString = dateString,
      timeStamp = timeStamp
    )

    constructor(historyRoomEntity: HistoryRoomEntity) : this(
      historyRoomEntity.id,
      historyRoomEntity.zimId,
      historyRoomEntity.zimName,
      historyRoomEntity.zimReaderSource,
      historyRoomEntity.favicon,
      historyRoomEntity.historyUrl,
      historyRoomEntity.historyTitle,
      historyRoomEntity.dateString,
      historyRoomEntity.timeStamp,
      false
    )
  }

  data class DateItem(
    val dateString: String,
    override val id: Long = dateString.hashCode().toLong()
  ) : HistoryListItem()
}
