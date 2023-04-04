import plugin.KiwixConfigurationPlugin

buildscript {
  repositories {
    google()
    mavenCentral()
    maven { setUrl("https://jitpack.io") }
  }

  dependencies {
    classpath(Libs.objectbox_gradle_plugin)
    classpath(Libs.butterknife_gradle_plugin)
  }
}
plugins {
  `android-library`
}
plugins.apply(KiwixConfigurationPlugin::class)
apply(plugin = "com.jakewharton.butterknife")
ext {
  set("versionMajor", 3)
  set("versionMinor", 6)
  set("versionPatch", 0)
}

/*
* max version code: 21-0-0-00-00-00
* our template    : UU-D-A-ZZ-YY-XX
* where:
* X = patch version
* Y = minor version
* Z = major version (+ 20 to distinguish from previous, non semantic, versions of the app)
* A = number representing ABI split
* D = number representing density split
* U = unused
*/

fun generateVersionCode() =
  20 * 10000 +
    ext["versionMajor"] as Int * 10000 +
    ext["versionMinor"] as Int * 100 +
    ext["versionPatch"] as Int

android {
  defaultConfig {
    buildConfigField("long", "VERSION_CODE", "${generateVersionCode()}")
  }
  buildTypes {
    getByName("debug") {
      dependencies {
        implementation(Libs.objectbox_kotlin)
        implementation(Libs.objectbox_rxjava)
      }
    }
    getByName("release") {
      isMinifyEnabled = false
      apply(plugin = "io.objectbox")
      dependencies {
        implementation(Libs.objectbox_kotlin)
        implementation(Libs.objectbox_rxjava)
      }
    }
    create("playStore") {
      apply(plugin = "io.objectbox")
      dependencies {
        implementation(Libs.objectbox_kotlin)
        implementation(Libs.objectbox_rxjava)
      }
    }
    create("fdroid") {
    }
  }
}

fun shouldUseLocalVersion() = File(projectDir, "libs").exists()

dependencies {
  // use jdk8 java.time backport, as long app < Build.VERSION_CODES.O
  implementation(Libs.threetenabp)

  // Get kiwixlib online if it is not populated locally
  if (!shouldUseLocalVersion()) {
    api(Libs.kiwixlib)
  } else {
    implementation("com.getkeepsafe.relinker:relinker:1.4.5")
    api(fileTree(mapOf("include" to "*.aar", "dir" to "libs")))
  }

  // Document File
  implementation(Libs.select_folder_document_file)

  // Square
  implementation(Libs.converter_simplexml) {
    exclude(group = "xpp3", module = "xpp3")
    exclude(group = "stax", module = "stax-api")
    exclude(group = "stax", module = "stax")
  }

  // Leak canary
  debugImplementation(Libs.leakcanary_android)

  implementation(Libs.android_arch_lifecycle_extensions)
  implementation(Libs.webkit)
  testImplementation(Libs.kotlinx_coroutines_test)
  implementation(Libs.kotlinx_coroutines_android)
}
