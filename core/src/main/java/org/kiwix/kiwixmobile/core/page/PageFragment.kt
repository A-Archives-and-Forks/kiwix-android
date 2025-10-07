/*
 * Kiwix Android
 * Copyright (c) 2020 Kiwix <android.kiwix.org>
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

package org.kiwix.kiwixmobile.core.page

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.kiwix.kiwixmobile.core.R
import org.kiwix.kiwixmobile.core.base.BaseFragment
import org.kiwix.kiwixmobile.core.base.FragmentActivityExtensions
import org.kiwix.kiwixmobile.core.downloader.downloadManager.ZERO
import org.kiwix.kiwixmobile.core.extensions.update
import org.kiwix.kiwixmobile.core.main.CoreMainActivity
import org.kiwix.kiwixmobile.core.page.adapter.OnItemClickListener
import org.kiwix.kiwixmobile.core.page.adapter.Page
import org.kiwix.kiwixmobile.core.page.notes.viewmodel.NotesState
import org.kiwix.kiwixmobile.core.page.viewmodel.Action
import org.kiwix.kiwixmobile.core.page.viewmodel.PageState
import org.kiwix.kiwixmobile.core.page.viewmodel.PageViewModel
import org.kiwix.kiwixmobile.core.ui.components.NavigationIcon
import org.kiwix.kiwixmobile.core.ui.models.ActionMenuItem
import org.kiwix.kiwixmobile.core.ui.models.IconItem
import org.kiwix.kiwixmobile.core.utils.SharedPreferenceUtil
import org.kiwix.kiwixmobile.core.utils.dialog.AlertDialogShower
import org.kiwix.kiwixmobile.core.utils.dialog.DialogHost
import javax.inject.Inject

const val SEARCH_ICON_TESTING_TAG = "searchIconTestingTag"
const val DELETE_MENU_ICON_TESTING_TAG = "deleteMenuIconTestingTag"

abstract class PageFragment : OnItemClickListener, BaseFragment(), FragmentActivityExtensions {
  abstract val pageViewModel: PageViewModel<*, *>

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

  @Inject lateinit var sharedPreferenceUtil: SharedPreferenceUtil

  @Inject lateinit var alertDialogShower: AlertDialogShower
  private var actionMode: ActionMode? = null
  private val coroutineJobs = mutableListOf<Job>()
  abstract val screenTitle: Int
  abstract val noItemsString: String
  abstract val switchString: String
  abstract val searchQueryHint: String
  abstract val switchIsChecked: Boolean
  abstract val deleteIconTitle: Int
  private val pageState: MutableState<PageState<*>> =
    mutableStateOf(
      NotesState(
        emptyList(),
        true,
        ""
      ),
      policy = referentialEqualityPolicy()
    )

  private val pageScreenState = mutableStateOf(
    // Initial values are empty because this is an abstract class.
    // Before the view is created, the abstract variables have no values.
    // We update this state in `onViewCreated`, once the view is created and the
    // abstract variables are initialized.
    PageFragmentScreenState(
      pageState = pageState.value,
      isSearchActive = false,
      searchQueryHint = "",
      searchText = "",
      searchValueChangedListener = {},
      screenTitle = ZERO,
      noItemsString = "",
      switchString = "",
      switchIsChecked = true,
      switchIsEnabled = true,
      onSwitchCheckedChanged = {},
      deleteIconTitle = ZERO,
      clearSearchButtonClickListener = {}
    )
  )

  private val actionModeCallback: ActionMode.Callback =
    object : ActionMode.Callback {
      override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.menu_context_delete, menu)
        return true
      }

      override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

      override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_context_delete) {
          pageViewModel.actions.tryEmit(Action.UserClickedDeleteSelectedPages)
          return true
        }
        pageViewModel.actions.tryEmit(Action.ExitActionModeMenu)
        return false
      }

      override fun onDestroyActionMode(mode: ActionMode) {
        pageViewModel.actions.tryEmit(Action.ExitActionModeMenu)
        actionMode = null
      }
    }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    pageScreenState.update {
      copy(
        searchQueryHint = this@PageFragment.searchQueryHint,
        searchText = "",
        searchValueChangedListener = { onTextChanged(it) },
        clearSearchButtonClickListener = { onTextChanged("") },
        screenTitle = this@PageFragment.screenTitle,
        noItemsString = this@PageFragment.noItemsString,
        switchString = this@PageFragment.switchString,
        switchIsChecked = this@PageFragment.switchIsChecked,
        onSwitchCheckedChanged = { onSwitchChanged(it).invoke() },
        deleteIconTitle = this@PageFragment.deleteIconTitle
      )
    }
    val activity = requireActivity() as CoreMainActivity
    cancelCoroutineJobs()
    coroutineJobs.apply {
      add(
        pageViewModel
          .effects
          .onEach { it.invokeWith(activity) }
          .launchIn(lifecycleScope)
      )
      add(
        pageViewModel
          .state
          .onEach { render(it) }
          .launchIn(lifecycleScope)
      )
    }
    pageViewModel.alertDialogShower = alertDialogShower
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    return ComposeView(requireContext()).apply {
      setContent {
        PageScreen(
          state = pageScreenState.value,
          itemClickListener = this@PageFragment,
          navigationIcon = {
            NavigationIcon(
              onClick = navigationIconClick()
            )
          },
          actionMenuItems = actionMenuList(
            isSearchActive = pageScreenState.value.isSearchActive,
            onSearchClick = {
              // Set the `isSearchActive` when the search button is clicked.
              pageScreenState.update { copy(isSearchActive = true) }
            },
            onDeleteClick = { pageViewModel.actions.tryEmit(Action.UserClickedDeleteButton) }
          )
        )
        DialogHost(alertDialogShower)
      }
    }
  }

  /**
   * Handles changes to the search text input.
   * - Updates the UI state with the latest search query.
   * - Sends a filter action to the ViewModel to perform search/filtering logic.
   *
   * @param searchText The current text entered in the search bar.
   */
  private fun onTextChanged(searchText: String) {
    pageScreenState.update { copy(searchText = searchText) }
    pageViewModel.actions.tryEmit(Action.Filter(searchText.trim()))
  }

  /**
   * Returns a lambda to handle switch toggle changes.
   * - Updates the UI state to reflect the new checked status.
   * - Sends an action to the ViewModel to handle the toggle event (e.g., show all items or filter).
   *
   * @param isChecked The new checked state of the switch.
   */
  private fun onSwitchChanged(isChecked: Boolean): () -> Unit = {
    pageScreenState.update { copy(switchIsChecked = isChecked) }
    pageViewModel.actions.tryEmit(Action.UserClickedShowAllToggle(isChecked))
  }

  /**
   * Handles the click event for the navigation icon.
   * - If search is active, it deactivates the search mode and clears the search text.
   * - Otherwise, it triggers the default back navigation.
   */
  private fun navigationIconClick(): () -> Unit = {
    if (pageScreenState.value.isSearchActive) {
      pageScreenState.update { copy(isSearchActive = false) }
      onTextChanged("")
    } else {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }
  }

  /**
   * Builds the list of action menu items for the app bar.
   *
   * @param isSearchActive Whether the search mode is currently active.
   * @param onSearchClick Callback to invoke when the search icon is clicked.
   * @param onDeleteClick Callback to invoke when the delete icon is clicked.
   * @return A list of [ActionMenuItem]s to be displayed in the app bar.
   *
   * - Shows the search icon only when search is not active.
   * - Always includes the delete icon, with a content description for accessibility (#3825).
   */
  private fun actionMenuList(
    isSearchActive: Boolean,
    onSearchClick: () -> Unit,
    onDeleteClick: () -> Unit
  ): List<ActionMenuItem> {
    return listOfNotNull(
      when {
        !isSearchActive -> ActionMenuItem(
          icon = IconItem.Drawable(R.drawable.action_search),
          contentDescription = R.string.search_label,
          onClick = onSearchClick,
          testingTag = SEARCH_ICON_TESTING_TAG
        )

        else -> null
      },
      ActionMenuItem(
        icon = IconItem.Vector(Icons.Default.Delete),
        // Adding content description for #3825.
        contentDescription = deleteIconTitle,
        onClick = onDeleteClick,
        testingTag = DELETE_MENU_ICON_TESTING_TAG
      )
    )
  }

  override fun onDestroyView() {
    super.onDestroyView()
    cancelCoroutineJobs()
  }

  private fun cancelCoroutineJobs() {
    coroutineJobs.forEach {
      it.cancel()
    }
    coroutineJobs.clear()
  }

  private fun render(state: PageState<*>) {
    pageScreenState.update {
      copy(switchIsEnabled = !state.isInSelectionState, pageState = state)
    }
    if (state.isInSelectionState) {
      if (actionMode == null) {
        actionMode =
          (requireActivity() as AppCompatActivity).startSupportActionMode(actionModeCallback)
      }
      actionMode?.title = getString(R.string.selected_items, state.numberOfSelectedItems())
    } else {
      actionMode?.finish()
    }
  }

  override fun onItemClick(page: Page) {
    pageViewModel.actions.tryEmit(Action.OnItemClick(page))
  }

  override fun onItemLongClick(page: Page): Boolean =
    pageViewModel.actions.tryEmit(Action.OnItemLongClick(page))
}
