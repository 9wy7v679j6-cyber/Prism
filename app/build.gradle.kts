plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.dagger.hilt)
}

android {
  namespace = "com.prism.docs"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.prism.docs"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "0.2.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all { test ->
        val cachedSdkDir =
          file("/home/appuser/.m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7")
        if (cachedSdkDir.exists()) {
          test.systemProperty("robolectric.offline", "true")
          test.systemProperty("robolectric.dependency.dir", cachedSdkDir.absolutePath)
        }
      }
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

composeCompiler {
  stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose_stability_config.conf"))
}

dependencies {
  implementation(project(":core-ui"))
  implementation(project(":core-data"))
  implementation(project(":feature-files"))
  implementation(project(":feature-viewer"))
  implementation(project(":feature-search"))
  implementation(project(":feature-scan"))
  implementation(project(":feature-pdftools"))
  implementation(project(":feature-editor"))

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.profileinstaller)
  implementation(libs.androidx.work.runtime.ktx)

  implementation(libs.androidx.compose.animation)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material3.window.size)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)

  implementation(libs.hilt.android)
  implementation(libs.androidx.hilt.navigation.compose)
  implementation(libs.androidx.hilt.work)
  ksp(libs.hilt.compiler)
  ksp(libs.androidx.hilt.compiler)

  implementation(libs.haze)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)

  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)

  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
