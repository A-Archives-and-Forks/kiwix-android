import com.android.build.gradle.internal.dsl.ProductFlavor
import custom.CustomApps
import custom.createPublisher
import custom.transactionWithCommit
import plugin.KiwixConfigurationPlugin
import java.io.FileNotFoundException
import java.net.URI
import java.net.URL

plugins {
  android
}

plugins.apply(KiwixConfigurationPlugin::class)

android {
  defaultConfig {
    applicationId = "org.kiwix"
  }

  flavorDimensions("default")
  productFlavors.apply {
    CustomApps.createDynamically(project.file("src"), this)
    all {
      File("$projectDir/src", "$name/$name.zim").let {
        createDownloadTask(it)
        createPublishBundleWithExpansionTask(it)
      }
    }
  }

  bundle {
    language {
      // This is disabled so that the App Bundle does NOT split the APK for each language.
      // We're gonna use the same APK for all languages.
      enableSplit = false
    }
  }
}

fun ProductFlavor.createDownloadTask(file: File): Task {
  return tasks.create("download${name.capitalize()}Zim") {
    group = "Downloading"
    doLast {
      if (!file.exists()) {
        file.createNewFile()
        URL(fetchUrl()).openStream().use {
          it.copyTo(file.outputStream())
        }
      }
    }
  }
}

fun ProductFlavor.fetchUrl(): String {
  return URI.create(buildConfigFields["ZIM_URL"]!!.value.replace("\"", "")).toURL()
    .openConnection()
    .apply {
      connect()
      getInputStream()
    }.let {
      it.getHeaderField("Location")?.replace("https", "http") ?: it.url.toString()
    }
}

fun ProductFlavor.createPublishBundleWithExpansionTask(
  file: File
): Task {
  val capitalizedName = name.capitalize()
  return tasks.create("publish${capitalizedName}ReleaseBundleWithExpansionFile") {
    group = "publishing"
    description = "Uploads $capitalizedName to the Play Console with an Expansion file"
    doLast {
      val packageName = "org.kiwix$applicationIdSuffix"
      println("packageName $packageName")
      createPublisher(File(rootDir, "playstore.json"))
        .transactionWithCommit(packageName) {
          val generatedBundleFile =
            File(
              "$buildDir/outputs/bundle/${capitalizedName.toLowerCase()}" +
                "Release/custom-${capitalizedName.toLowerCase()}-release.aab"
            )
          if (generatedBundleFile.exists()) {
            val versionCode = uploadBundleAndReturnVersionCode(generatedBundleFile)
            uploadExpansionTo(file, versionCode)
            attachExpansionTo(versionCode)
            addToTrackInDraft(versionCode, versionName)
          } else {
            throw FileNotFoundException("Unable to find generated aab file")
          }
        }
    }
  }
}

afterEvaluate {
  tasks.filter { it.name.contains("ReleaseBundleWithExpansionFile") }.forEach {
    val flavorName =
      it.name.substringAfter("publish").substringBefore("ReleaseBundleWithExpansionFile")
    it.dependsOn.add(tasks.getByName("download${flavorName}Zim"))
    it.dependsOn.add(tasks.getByName("bundle${flavorName}Release"))
  }
}
