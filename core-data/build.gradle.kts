plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.dagger.hilt)
}

android {
  namespace = "com.prism.docs.core.data"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    minSdk = 26
    consumerProguardFiles("consumer-rules.pro")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  sourceSets {
    getByName("test").assets.directories.add("$projectDir/schemas")
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
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
  arg("room.generateKotlin", "true")
}

tasks.matching { it.name == "mergeDebugUnitTestAssets" }.configureEach {
  dependsOn("kspDebugKotlin")
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.documentfile)
  api(libs.androidx.paging.runtime)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.paging)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.hilt.android)
  implementation(libs.androidx.hilt.work)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  api(libs.kotlinx.serialization.json)

  ksp(libs.androidx.room.compiler)
  ksp(libs.hilt.compiler)
  ksp(libs.androidx.hilt.compiler)

  testImplementation(libs.junit)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.androidx.room.testing)
  testImplementation(libs.androidx.paging.testing)
  testImplementation(libs.androidx.work.testing)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
}
