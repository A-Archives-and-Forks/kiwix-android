package org.kiwix.kiwixmobile.core.page.history.viewmodel.effects

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.kiwix.kiwixmobile.core.base.SideEffect
import org.kiwix.kiwixmobile.core.dao.HistoryRoomDao
import org.kiwix.kiwixmobile.core.main.CoreMainActivity
import org.kiwix.kiwixmobile.core.page.historyItem
import org.kiwix.kiwixmobile.core.page.historyState
import org.kiwix.kiwixmobile.core.page.viewmodel.effects.DeletePageItems
import org.kiwix.kiwixmobile.core.utils.dialog.DialogShower
import org.kiwix.kiwixmobile.core.utils.dialog.KiwixDialog.DeleteAllHistory
import org.kiwix.kiwixmobile.core.utils.dialog.KiwixDialog.DeleteSelectedHistory

internal class ShowDeleteHistoryDialogTest {
  val effects = mockk<MutableSharedFlow<SideEffect<*>>>(relaxed = true)
  private val historyRoomDao = mockk<HistoryRoomDao>()
  val activity = mockk<CoreMainActivity>()
  private val dialogShower = mockk<DialogShower>(relaxed = true)
  private val viewModelScope = CoroutineScope(Dispatchers.IO)

  @Test
  fun `invoke with shows dialog that offers ConfirmDelete action`() =
    runBlocking {
      val showDeleteHistoryDialog =
        ShowDeleteHistoryDialog(
          effects,
          historyState(),
          historyRoomDao,
          viewModelScope,
          dialogShower
        )
      mockkActivityInjection(showDeleteHistoryDialog)
      val lambdaSlot = slot<() -> Unit>()
      showDeleteHistoryDialog.invokeWith(activity)
      verify { dialogShower.show(any(), capture(lambdaSlot)) }
      lambdaSlot.captured.invoke()
      verify { effects.tryEmit(DeletePageItems(historyState(), historyRoomDao, viewModelScope)) }
    }

  @Test
  fun `invoke with selected item shows dialog with delete selected items title`() =
    runBlocking {
      val showDeleteHistoryDialog =
        ShowDeleteHistoryDialog(
          effects,
          historyState(listOf(historyItem(isSelected = true, zimReaderSource = mockk()))),
          historyRoomDao,
          viewModelScope,
          dialogShower
        )
      mockkActivityInjection(showDeleteHistoryDialog)
      showDeleteHistoryDialog.invokeWith(activity)
      verify { dialogShower.show(DeleteSelectedHistory, any()) }
    }

  @Test
  fun `invoke with no selected items shows dialog with delete all items title`() =
    runBlocking {
      val showDeleteHistoryDialog =
        ShowDeleteHistoryDialog(
          effects,
          historyState(),
          historyRoomDao,
          viewModelScope,
          dialogShower
        )
      mockkActivityInjection(showDeleteHistoryDialog)
      showDeleteHistoryDialog.invokeWith(activity)
      verify { dialogShower.show(DeleteAllHistory, any()) }
    }

  private fun mockkActivityInjection(showDeleteHistoryDialog: ShowDeleteHistoryDialog) {
    every { activity.cachedComponent.inject(showDeleteHistoryDialog) } answers {
      showDeleteHistoryDialog.dialogShower = dialogShower
      Unit
    }
  }
}
