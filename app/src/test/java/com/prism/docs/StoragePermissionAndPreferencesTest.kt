package com.prism.docs

import android.content.Context
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.prism.docs.core.data.model.GlassIntensity
import com.prism.docs.core.data.model.SafFolderGrant
import com.prism.docs.core.data.model.StorageAccessLevel
import com.prism.docs.core.data.model.ThemeMode
import com.prism.docs.core.data.preferences.PrismPreferencesRepository
import com.prism.docs.core.data.storage.StoragePermissionManager
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class StoragePermissionAndPreferencesTest {

  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  @Test
  fun appNameResourceMatchesPrism() {
    assertEquals("Prism", context.getString(R.string.app_name))
  }

  @Test
  fun storagePermissionManagerBuildsApi30AllFilesIntentAndResolvesFileUri() {
    val manager = StoragePermissionManager(context)
    assertFalse(manager.requiresLegacyStoragePermissions())
    assertEquals(2, manager.legacyStoragePermissions().size)

    val intent = manager.buildAllFilesAccessIntent()
    assertTrue(
      intent.action == Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION ||
        intent.action == Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
    )

    // Revoked SAF grants should be filtered out when computing access state
    val staleGrant =
      SafFolderGrant(
        uriString = "content://com.android.externalstorage.documents/tree/primary%3ADocuments",
        displayName = "Documents",
        grantedAtEpochMs = 1_700_000_000_000L,
      )
    val state = manager.computeAccessState(listOf(staleGrant))
    assertEquals(StorageAccessLevel.LIMITED_NONE, state.level)
    assertTrue(state.safFolders.isEmpty())

    // Resolve real local file:// URI metadata safely
    val tempPdf = File(context.cacheDir, "quarterly_report.pdf").apply {
      writeBytes(ByteArray(2048) { 0x25 })
    }
    try {
      val resolved =
        manager.resolveIncomingDocument(
          uri = Uri.fromFile(tempPdf),
          explicitMimeType = null,
          sourceAction = "VIEW",
        )
      assertEquals("quarterly_report.pdf", resolved.displayName)
      assertEquals(2048L, resolved.sizeBytes)
      assertEquals("application/pdf", resolved.mimeType)
      assertEquals("VIEW", resolved.sourceAction)
    } finally {
      tempPdf.delete()
    }
  }

  @Test
  fun preferencesRepositoryPersistsThemeGlassAccessibilityAndSafGrants() = runTest {
    val repo = PrismPreferencesRepository(context)

    assertEquals(ThemeMode.LIGHT, repo.preferencesFlow.first().themeMode)

    repo.setOnboardingCompleted(true)
    repo.setThemeMode(ThemeMode.DARK)
    repo.setUseDynamicColor(false)
    repo.setGlassIntensity(GlassIntensity.RICH)
    repo.setReduceTransparency(true)
    repo.setReduceMotion(true)
    repo.setFullAccessBannerDismissed(true)

    val grant =
      SafFolderGrant(
        uriString = "content://com.android.externalstorage.documents/tree/primary%3AWork",
        displayName = "Work",
        grantedAtEpochMs = 1_750_000_000_000L,
      )
    repo.addSafFolderGrant(grant)

    val loaded = repo.preferencesFlow.first()
    assertTrue(loaded.onboardingCompleted)
    assertEquals(ThemeMode.DARK, loaded.themeMode)
    assertFalse(loaded.useDynamicColor)
    assertEquals(GlassIntensity.RICH, loaded.glassIntensity)
    assertTrue(loaded.reduceTransparency)
    assertTrue(loaded.reduceMotion)
    assertTrue(loaded.fullAccessBannerDismissed)
    assertEquals(listOf(grant), loaded.safFolderGrants)

    repo.removeSafFolderGrant(grant.uriString)
    val afterRemove = repo.preferencesFlow.first()
    assertTrue(afterRemove.safFolderGrants.isEmpty())
  }
}
