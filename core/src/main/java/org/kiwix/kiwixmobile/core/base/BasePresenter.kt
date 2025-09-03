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
package org.kiwix.kiwixmobile.core.base

import org.kiwix.kiwixmobile.core.base.BaseContract.Presenter
import org.kiwix.kiwixmobile.core.base.BaseContract.View

/**
 * All presenters should inherit from this presenter.
 */
@Suppress("UnnecessaryAbstractClass")
abstract class BasePresenter<T : View<*>?> : Presenter<T> {
  @JvmField
  var view: T? = null

  override fun attachView(view: T) {
    this.view = view
  }

  override fun detachView(view: T) {
    // Detach the view only if it matches the one currently attached.
    // Bug Fix #4409
    if (this.view == view) {
      this.view = null
    }
  }
}
