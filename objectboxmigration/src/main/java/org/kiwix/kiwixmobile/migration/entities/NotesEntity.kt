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

package org.kiwix.kiwixmobile.migration.entities

import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import org.kiwix.kiwixmobile.core.page.notes.adapter.NoteListItem
import org.kiwix.kiwixmobile.core.reader.ZimReaderSource

@Entity
data class NotesEntity(
  @Id var id: Long = 0L,
  val zimId: String,
  // keep this to handle previously saved notes
  var zimFilePath: String?,
  @Convert(converter = ZimSourceConverter::class, dbType = String::class)
  var zimReaderSource: ZimReaderSource?,
  val zimUrl: String,
  var noteTitle: String,
  var noteFilePath: String,
  var favicon: String?
) {
  constructor(item: NoteListItem) : this(
    id = item.databaseId,
    zimId = item.zimId,
    // pass null for new notes
    zimFilePath = null,
    zimReaderSource = item.zimReaderSource,
    zimUrl = item.zimUrl,
    noteTitle = item.title,
    noteFilePath = item.noteFilePath,
    favicon = item.favicon
  )
}
