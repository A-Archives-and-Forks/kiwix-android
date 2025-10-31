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

package org.kiwix.kiwixmobile.core.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import org.kiwix.kiwixmobile.core.R
import org.kiwix.kiwixmobile.core.ui.components.KiwixAppBar
import org.kiwix.kiwixmobile.core.ui.components.KiwixSnackbarHost
import org.kiwix.kiwixmobile.core.ui.models.ActionMenuItem
import org.kiwix.kiwixmobile.core.ui.theme.KiwixDialogTheme
import org.kiwix.kiwixmobile.core.utils.ComposeDimens.FIVE_DP
import org.kiwix.kiwixmobile.core.utils.ComposeDimens.FOUR_DP
import org.kiwix.kiwixmobile.core.utils.ComposeDimens.MINIMUM_HEIGHT_OF_NOTE_TEXT_FILED
import org.kiwix.kiwixmobile.core.utils.ComposeDimens.TEN_DP
import org.kiwix.kiwixmobile.core.utils.ComposeDimens.TWENTY_DP

const val ADD_NOTE_TEXT_FILED_TESTING_TAG = "addNoteTextFiledTestingTag"
const val SAVE_MENU_BUTTON_TESTING_TAG = "saveMenuButtonTestingTag"
const val SHARE_MENU_BUTTON_TESTING_TAG = "shareMenuButtonTestingTag"
const val DELETE_MENU_BUTTON_TESTING_TAG = "deleteMenuButtonTestingTag"

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("ComposableLambdaParameterNaming")
@Composable
fun AddNoteDialogScreen(
  articleTitle: String,
  noteText: TextFieldValue,
  actionMenuItems: List<ActionMenuItem>,
  onTextChange: (TextFieldValue) -> Unit,
  snackBarHostState: SnackbarHostState,
  navigationIcon: @Composable () -> Unit
) {
  KiwixDialogTheme {
    Scaffold(
      snackbarHost = { KiwixSnackbarHost(snackbarHostState = snackBarHostState) },
      topBar = {
        KiwixAppBar(
          title = stringResource(R.string.note),
          navigationIcon = navigationIcon,
          actionMenuItems = actionMenuItems
        )
      }
    ) { paddingValues ->
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color.Transparent)
          .imePadding()
          .padding(paddingValues),
      ) {
        ArticleTitleText(articleTitle)
        HorizontalDivider(
          modifier = Modifier.padding(vertical = FIVE_DP),
          color = MaterialTheme.colorScheme.onSurface
        )
        NoteTextField(
          noteText = noteText,
          onTextChange = onTextChange
        )
      }
    }
  }
}

@Composable
private fun ArticleTitleText(articleTitle: String) {
  Text(
    text = articleTitle,
    maxLines = 3,
    style = MaterialTheme.typography.bodyLarge,
    modifier = Modifier.padding(top = TEN_DP, start = TWENTY_DP, end = TWENTY_DP)
  )
}

@Composable
private fun NoteTextField(
  noteText: TextFieldValue,
  onTextChange: (TextFieldValue) -> Unit
) {
  TextField(
    value = noteText,
    onValueChange = { onTextChange(it) },
    maxLines = 6,
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = MINIMUM_HEIGHT_OF_NOTE_TEXT_FILED)
      .padding(bottom = TEN_DP)
      .padding(horizontal = FOUR_DP)
      .testTag(ADD_NOTE_TEXT_FILED_TESTING_TAG),
    placeholder = { Text(text = stringResource(R.string.note)) },
    singleLine = false,
    shape = RectangleShape,
    keyboardOptions = KeyboardOptions(
      autoCorrectEnabled = true,
      capitalization = KeyboardCapitalization.Sentences,
      keyboardType = KeyboardType.Text
    ),
    textStyle = MaterialTheme.typography.bodyLarge,
    colors = TextFieldDefaults.colors(
      focusedContainerColor = Color.Transparent,
      unfocusedContainerColor = Color.Transparent,
      disabledContainerColor = Color.Transparent,
      errorContainerColor = Color.Transparent,
      focusedIndicatorColor = Color.Transparent,
      unfocusedIndicatorColor = Color.Transparent
    )
  )
}
