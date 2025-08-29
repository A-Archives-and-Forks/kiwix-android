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

package org.kiwix.kiwixmobile.di.components

import dagger.Component
import org.kiwix.kiwixmobile.core.data.ObjectBoxDataMigrationHandler
import org.kiwix.kiwixmobile.core.di.components.CoreComponent
import org.kiwix.kiwixmobile.migration.di.module.MigrationModule
import org.kiwix.kiwixmobile.di.KiwixScope
import org.kiwix.kiwixmobile.di.components.ServiceComponent.Builder
import org.kiwix.kiwixmobile.di.modules.JNIModule
import org.kiwix.kiwixmobile.di.modules.KiwixModule
import org.kiwix.kiwixmobile.di.modules.KiwixViewModelModule
import org.kiwix.kiwixmobile.migration.di.module.DatabaseModule
import org.kiwix.kiwixmobile.storage.StorageSelectDialog
import org.kiwix.kiwixmobile.zimManager.OnlineLibraryManager

@KiwixScope
@Component(
  dependencies = [CoreComponent::class],
  modules = [
    KiwixViewModelModule::class,
    KiwixModule::class,
    JNIModule::class,
    MigrationModule::class,
    DatabaseModule::class
  ]
)
interface KiwixComponent {
  fun activityComponentBuilder(): KiwixActivityComponent.Builder
  fun serviceComponent(): Builder
  fun inject(storageSelectDialog: StorageSelectDialog)
  fun providesOnlineLibraryManager(): OnlineLibraryManager
  fun provideObjectBoxDataMigrationHandler(): ObjectBoxDataMigrationHandler
}
