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
package org.kiwix.kiwixmobile.core.search

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MenuItem.OnActionExpandListener
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.core.widget.ContentLoadingProgressBar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.kiwix.kiwixmobile.core.R
import org.kiwix.kiwixmobile.core.base.BaseActivity
import org.kiwix.kiwixmobile.core.base.BaseFragment
import org.kiwix.kiwixmobile.core.databinding.FragmentSearchBinding
import org.kiwix.kiwixmobile.core.extensions.ActivityExtensions.cachedComponent
import org.kiwix.kiwixmobile.core.extensions.coreMainActivity
import org.kiwix.kiwixmobile.core.extensions.viewModel
import org.kiwix.kiwixmobile.core.main.CoreMainActivity
import org.kiwix.kiwixmobile.core.search.adapter.SearchAdapter
import org.kiwix.kiwixmobile.core.search.adapter.SearchDelegate.RecentSearchDelegate
import org.kiwix.kiwixmobile.core.search.adapter.SearchDelegate.ZimSearchResultDelegate
import org.kiwix.kiwixmobile.core.search.adapter.SearchListItem
import org.kiwix.kiwixmobile.core.search.viewmodel.Action
import org.kiwix.kiwixmobile.core.search.viewmodel.Action.ActivityResultReceived
import org.kiwix.kiwixmobile.core.search.viewmodel.Action.ClickedSearchInText
import org.kiwix.kiwixmobile.core.search.viewmodel.Action.ExitedSearch
import org.kiwix.kiwixmobile.core.search.viewmodel.Action.Filter
import org.kiwix.kiwixmobile.core.search.viewmodel.Action.OnItemClick
import org.kiwix.kiwixmobile.core.search.viewmodel.Action.OnItemLongClick
import org.kiwix.kiwixmobile.core.search.viewmodel.Action.OnOpenInNewTabClick
import org.kiwix.kiwixmobile.core.search.viewmodel.SearchOrigin.FromWebView
import org.kiwix.kiwixmobile.core.search.viewmodel.SearchState
import org.kiwix.kiwixmobile.core.search.viewmodel.SearchViewModel
import org.kiwix.kiwixmobile.core.utils.SimpleTextListener
import javax.inject.Inject

const val NAV_ARG_SEARCH_STRING = "searchString"
const val VISIBLE_ITEMS_THRESHOLD = 5

class SearchFragment : BaseFragment() {

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

  private var searchView: SearchView? = null
  private var searchInTextMenuItem: MenuItem? = null
  private var fragmentSearchBinding: FragmentSearchBinding? = null

  private val searchViewModel by lazy { viewModel<SearchViewModel>(viewModelFactory) }
  private var searchAdapter: SearchAdapter? = null
  private var isDataLoading = false
  private var searchState: SearchState? = null

  override fun inject(baseActivity: BaseActivity) {
    baseActivity.cachedComponent.inject(this)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    fragmentSearchBinding = FragmentSearchBinding.inflate(inflater, container, false)
    setupMenu()
    return fragmentSearchBinding?.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    searchAdapter = SearchAdapter(
      RecentSearchDelegate(::onItemClick, ::onItemClickNewTab) {
        searchViewModel.actions.trySend(OnItemLongClick(it)).isSuccess
      },
      ZimSearchResultDelegate(::onItemClick, ::onItemClickNewTab)
    )
    setupToolbar(view)
    fragmentSearchBinding?.searchList?.run {
      adapter = searchAdapter
      layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
      setHasFixedSize(true)
      // Add scroll listener to detect when the last item is reached
      addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
          super.onScrolled(recyclerView, dx, dy)

          val layoutManager = recyclerView.layoutManager as LinearLayoutManager
          val totalItemCount = layoutManager.itemCount
          val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
          // Check if the user is about to reach the last item
          if (!isDataLoading && totalItemCount <= lastVisibleItem + VISIBLE_ITEMS_THRESHOLD) {
            // Load more data when the last item is almost visible
            loadMoreSearchResult()
          }
        }
      })
    }
    lifecycleScope.launchWhenCreated {
      searchViewModel.effects.collect { it.invokeWith(this@SearchFragment.coreMainActivity) }
    }
    handleBackPress()
  }

  /**
   * Loads more search results if available.
   * If data is currently being loaded, the method returns early and does not initiate another load request.
   * The loading process sets the `isDataLoading` flag to true to prevent multiple load requests at once.
   * The method fetches more search results based on the current `searchState` and the `safeStartIndex`,
   * which represents the current item count in the `searchAdapter`.
   * If there are no more results to fetch, the method sets `isDataLoading` to true to avoid unnecessary data loading.
   * If there are new results available, it checks for duplicate entries, especially for search history items.
   * Non-duplicate results are appended to the existing dataset in the `searchAdapter`.
   * If new data (non-duplicate items) is loaded successfully, the method sets `isDataLoading` to false.
   * If there are no new non-duplicate results, the method sets `isDataLoading` to true
   * to avoid unnecessary data loading, as there are no more items available.
   */
  private fun loadMoreSearchResult() {
    if (isDataLoading) return
    val safeStartIndex = searchAdapter?.itemCount ?: 0
    isDataLoading = true
    fragmentSearchBinding?.loadingMoreDataIndicator?.isShowing(true)

    CoroutineScope(Dispatchers.IO).launch {
      val fetchMoreSearchResults = searchState?.getVisibleResults(safeStartIndex)
      CoroutineScope(Dispatchers.Main).launch {
        fragmentSearchBinding?.loadingMoreDataIndicator?.isShowing(false)
        isDataLoading = when {
          fetchMoreSearchResults == null -> true
          fetchMoreSearchResults.isEmpty() -> false
          else -> {
            val nonDuplicateResults = fetchMoreSearchResults.filter { newItem ->
              searchAdapter?.items?.none { it == newItem } ?: true
            }

            if (nonDuplicateResults.isNotEmpty()) {
              searchAdapter?.addData(nonDuplicateResults)
              false
            } else {
              true
            }
          }
        }
      }
    }
  }

  private fun handleBackPress() {
    activity?.onBackPressedDispatcher?.addCallback(
      viewLifecycleOwner,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          goBack()
        }
      }
    )
  }

  private fun setupToolbar(view: View) {
    view.post {
      with(requireActivity() as CoreMainActivity) {
        setSupportActionBar(view.findViewById(R.id.toolbar))
        supportActionBar?.apply {
          setHomeButtonEnabled(true)
          title = getString(R.string.menu_search_in_text)
        }
      }
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    searchState = null
    activity?.intent?.action = null
    searchView = null
    searchInTextMenuItem = null
    searchAdapter = null
    fragmentSearchBinding = null
  }

  private fun goBack() {
    val readerFragmentResId = (activity as CoreMainActivity).readerFragmentResId
    findNavController().popBackStack(readerFragmentResId, false)
  }

  private fun setupMenu() {
    (requireActivity() as MenuHost).addMenuProvider(
      object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
          menuInflater.inflate(R.menu.menu_search, menu)
          val searchMenuItem = menu.findItem(R.id.menu_search)
          searchMenuItem.expandActionView()
          searchView = searchMenuItem.actionView as SearchView
          searchView?.setOnQueryTextListener(
            SimpleTextListener {
              if (it.isNotEmpty()) {
                searchViewModel.actions.trySend(Filter(it)).isSuccess
              }
            }
          )
          searchMenuItem.setOnActionExpandListener(object : OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem) = false

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
              searchViewModel.actions.trySend(ExitedSearch).isSuccess
              return false
            }
          })
          searchInTextMenuItem = menu.findItem(R.id.menu_searchintext)
          searchInTextMenuItem?.setOnMenuItemClickListener {
            searchViewModel.actions.trySend(ClickedSearchInText).isSuccess
            true
          }
          lifecycleScope.launchWhenCreated {
            searchViewModel.state.collect { render(it) }
          }
          val searchStringFromArguments = arguments?.getString(NAV_ARG_SEARCH_STRING)
          if (searchStringFromArguments != null) {
            searchView?.setQuery(searchStringFromArguments, false)
          }
          searchViewModel.actions.trySend(Action.CreatedWithArguments(arguments)).isSuccess
        }

        override fun onMenuItemSelected(menuItem: MenuItem) = true
      },
      viewLifecycleOwner,
      Lifecycle.State.RESUMED
    )
  }

  private suspend fun render(state: SearchState) {
    isDataLoading = false
    searchState = state
    searchInTextMenuItem?.isVisible = state.searchOrigin == FromWebView
    searchInTextMenuItem?.isEnabled = state.searchTerm.isNotBlank()
    fragmentSearchBinding?.searchLoadingIndicator?.isShowing(state.isLoading)
    val searchResult = state.getVisibleResults(0)
    fragmentSearchBinding?.searchNoResults?.isVisible =
      searchResult?.isEmpty() == true
    searchResult?.let {
      searchAdapter?.items = it
    }
  }

  private fun onItemClick(it: SearchListItem) {
    searchViewModel.actions.trySend(OnItemClick(it)).isSuccess
  }

  private fun onItemClickNewTab(it: SearchListItem) {
    searchViewModel.actions.trySend(OnOpenInNewTabClick(it)).isSuccess
  }

  @Suppress("DEPRECATION")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    searchViewModel.actions.trySend(ActivityResultReceived(requestCode, resultCode, data)).isSuccess
  }
}

private fun ContentLoadingProgressBar.isShowing(show: Boolean) {
  if (show) {
    show()
  } else {
    hide()
  }
}
