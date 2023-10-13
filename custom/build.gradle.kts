import com.android.build.gradle.api.ApkVariantOutput
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.internal.dsl.ProductFlavor
import custom.CustomApps
import custom.createPublisher
import custom.transactionWithCommit
import plugin.KiwixConfigurationPlugin
import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import java.util.Base64
import java.io.FileOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody

plugins {
  android
}

plugins.apply(KiwixConfigurationPlugin::class)

android {
  defaultConfig {
    applicationId = "org.kiwix"
  }

  flavorDimensions += "default"
  productFlavors.apply {
    CustomApps.createDynamically(project.file("src"), this)
    all {
      File("$projectDir/src", "$name/$name.zim").let {
        createDownloadTask(it)
        createPublishApkWithExpansionTask(it, applicationVariants)
      }
    }
  }
  splits {
    abi {
      isUniversalApk = false
    }
  }
}

fun ProductFlavor.createDownloadTask(file: File): Task {
  return tasks.create(
    "download${
    name.replaceFirstChar {
      if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else "$it"
    }
    }Zim"
  ) {
    group = "Downloading"
    doLast {
      if (!file.exists()) {
        file.createNewFile()

        OkHttpClient().newCall(fetchRequest()).execute().use { response ->
          if (response.isSuccessful) {
            response.body?.let { responseBody ->
              writeZimFileData(responseBody, file)
            }
          } else {
            throw RuntimeException(
              "Download Failed. Error: ${response.message}\n" +
                " Status Code: ${response.code}"
            )
          }
        }
      }
    }
  }
}

fun ProductFlavor.fetchRequest(): Request {
  val urlString = buildConfigFields["ZIM_URL"]!!.value.replace("\"", "")
  return if (urlString.isAuthenticationUrl) {
    Request.Builder()
      .url(URI.create(urlString.removeAuthenticationFromUrl).toURL())
      .header(
        "Authorization",
        "Basic " +
          Base64.getEncoder().encodeToString(System.getenv(urlString.secretKey).toByteArray())
      )
      .build()
  } else {
    Request.Builder()
      .url(URI.create(urlString).toURL())
      .build()
  }
}

fun writeZimFileData(responseBody: ResponseBody, file: File) {
  FileOutputStream(file).use { outputStream ->
    responseBody.byteStream().use { inputStream ->
      val buffer = ByteArray(4096)
      var bytesRead: Int
      while (inputStream.read(buffer).also { bytesRead = it } != -1) {
        outputStream.write(buffer, 0, bytesRead)
      }
      outputStream.flush()
    }
  }
}

val String.decodeUrl: String
  get() = URLDecoder.decode(this, "UTF-8")
val String.isAuthenticationUrl: Boolean
  get() = decodeUrl.trim().matches(Regex("https://[^@]+@.*\\.zim"))

val String.secretKey: String
  get() = decodeUrl.substringAfter("{{", "")
    .substringBefore("}}", "")
    .trim()

val String.removeAuthenticationFromUrl: String
  get() = decodeUrl.trim()
    .replace(Regex("\\{\\{\\s*[^}]+\\s*\\}\\}@"), "")

fun ProductFlavor.createPublishApkWithExpansionTask(
  file: File,
  applicationVariants: DomainObjectSet<ApplicationVariant>
): Task {
  val capitalizedName =
    name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else "$it" }
  return tasks.create("publish${capitalizedName}ReleaseApkWithExpansionFile") {
    group = "publishing"
    description = "Uploads $capitalizedName to the Play Console with an Expansion file"
    doLast {
      val packageName = "org.kiwix$applicationIdSuffix"
      println("packageName $packageName")
      createPublisher(File(rootDir, "playstore.json"))
        .transactionWithCommit(packageName) {
          val variants =
            applicationVariants.releaseVariantsFor(this@createPublishApkWithExpansionTask)
          variants.forEach(::uploadApk)
          uploadExpansionTo(file, variants[0].versionCodeOverride)
          variants.drop(1).forEach { attachExpansionTo(variants[0].versionCodeOverride, it) }
          addToTrackInDraft(variants)
        }
    }
  }
}

@Suppress("DEPRECATION")
fun DomainObjectSet<ApplicationVariant>.releaseVariantsFor(productFlavor: ProductFlavor) =
  find { it.name.equals("${productFlavor.name}Release", true) }!!
    .outputs.filterIsInstance<ApkVariantOutput>().sortedBy { it.versionCodeOverride }

afterEvaluate {
  tasks.filter { it.name.contains("ReleaseApkWithExpansionFile") }.forEach {
    val flavorName =
      it.name.substringAfter("publish").substringBefore("ReleaseApkWithExpansionFile")
    it.dependsOn.add(tasks.getByName("download${flavorName}Zim"))
    it.dependsOn.add(tasks.getByName("assemble${flavorName}Release"))
  }
}
