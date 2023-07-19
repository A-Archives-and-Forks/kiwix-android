/* Kiwix Android
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
 */

package custom

import com.android.build.api.variant.VariantOutput
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.GenericJson
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.AndroidPublisherScopes
import com.google.api.services.androidpublisher.model.ExpansionFile
import com.google.api.services.androidpublisher.model.ExpansionFilesUploadResponse
import com.google.api.services.androidpublisher.model.Track
import com.google.api.services.androidpublisher.model.TrackRelease
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore

@Suppress("DEPRECATION")
fun createPublisher(auth: File): AndroidPublisher {
  val transport = buildTransport()
  val factory = JacksonFactory.getDefaultInstance()

  val credential =
    GoogleCredential.fromStream(auth.inputStream(), transport, factory)
      .createScoped(listOf(AndroidPublisherScopes.ANDROIDPUBLISHER))


  return AndroidPublisher.Builder(transport, JacksonFactory.getDefaultInstance()) {
    credential.initialize(it.setReadTimeout(0))
  }.setApplicationName("kiwixcustom").build()
}

private fun buildTransport(): NetHttpTransport {
  val trustStore: String? = System.getProperty("javax.net.ssl.trustStore", null)
  val trustStorePassword: String? =
    System.getProperty("javax.net.ssl.trustStorePassword", null)

  return if (trustStore == null) {
    GoogleNetHttpTransport.newTrustedTransport()
  } else {
    val ks = KeyStore.getInstance(KeyStore.getDefaultType())
    FileInputStream(trustStore).use { fis ->
      ks.load(fis, trustStorePassword?.toCharArray())
    }
    NetHttpTransport.Builder().trustCertificates(ks).build()
  }
}

class Transaction(
  private val publisher: AndroidPublisher,
  private val packageName: String,
  val editId: String
) {
  fun uploadExpansionTo(
    file: File,
    versionCode: Int?
  ): ExpansionFilesUploadResponse = publisher.edits().expansionfiles()
    .upload(
      packageName,
      editId,
      versionCode,
      "main",
      FileContent("application/octet-stream", file)
    ).execute().prettyPrint()

  @Suppress("DEPRECATION")
  fun attachExpansionTo(expansionCode: Int?, variantOutput: VariantOutput): ExpansionFile =
    publisher.edits().expansionfiles().update(
      packageName,
      editId,
      variantOutput.versionCode.get(),
      "main",
      ExpansionFile().apply { referencesVersion = expansionCode }
    ).execute().prettyPrint()

  fun uploadApk() {
    publisher.edits().apks().upload(
      packageName,
      editId,
      null
    ).execute().prettyPrint()
  }

  fun addToTrackInDraft(variantOutputs: List<VariantOutput>): Track =
    publisher.edits().tracks().update(packageName, editId, "internal", Track().apply {
      releases = listOf(TrackRelease().apply {
        status = "draft"
        name = variantOutputs[0].versionName.get()
        versionCodes = variantOutputs.map { it.versionCode.get().toLong() }
      })
      track = "internal"
    }).execute().prettyPrint()
}

fun AndroidPublisher.transactionWithCommit(packageName: String, func: Transaction.() -> Unit) {
  Transaction(
    this,
    packageName,
    edits().insert(packageName, null).execute().prettyPrint().id
  ).apply {
    func()
    edits().validate(packageName, editId).execute().prettyPrint()
  }.also { edits().commit(packageName, it.editId).execute().prettyPrint() }
}

private fun <T : GenericJson> T.prettyPrint() = also { println(it.toPrettyString()) }
