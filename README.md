# Prism — Offline-First Document Manager for Android

Prism is a private, 100% on-device Android document manager, universal viewer, OCR scanner, and PDF toolkit built with Kotlin, Jetpack Compose, Hilt, Room, DataStore, and a custom **Liquid Glass** design system (`:core-ui`).

## Module Architecture
- `:app` — Application shell, Hilt root, adaptive `WindowSizeClass` navigation (`SharedTransitionLayout`), onboarding, incoming `VIEW`/`SEND`/`SEND_MULTIPLE` intent routing.
- `:core-ui` — Liquid Glass design system (`GlassSurface`, AGSL edge refraction shader on API 33+, tiered blur/frost/high-contrast fallbacks, collapsible capsule tab bar, navigation rail, toolbar groups, search bar, bottom sheet with detents, context menus, page thumbnail strip, bundled Google Fonts).
- `:core-data` — DataStore preferences, `StoragePermissionManager` (API 30+ All Files Access, API 26–29 legacy storage, SAF persisted folder grants, URI resolver), Room database scaffold, Hilt DI.
- `:feature-files` — Library home, category rails, storage browser, file operations (Phase 1).
- `:feature-viewer` — Unified document viewer shell and per-type renderers (Phase 2).
- `:feature-search` — Room FTS full-text search and background extraction (Phase 3).
- `:feature-scan` — ML Kit Document Scanner + on-device OCR / searchable PDF (Phase 4).
- `:feature-pdftools` — Offline PdfBox-Android PDF toolkit and batch queue (Phase 5).
- `:feature-editor` — PDF annotations and light Office/text editing with atomic safe saving (Phase 6).

## Google Play `MANAGE_EXTERNAL_STORAGE` Policy & Distribution Notes

Google Play strictly regulates the **All Files Access** permission (`android.permission.MANAGE_EXTERNAL_STORAGE`) on Android 11+ (API 30+).

### 1. Google Play Store Declaration (Permitted File-Manager Use Case)
File managers and comprehensive document managers are explicitly recognized as eligible core use cases for All Files Access, **provided** you complete Google Play's permissions declaration before publishing:
1. Open **Google Play Console** -> select **Prism** -> **Policy and programs** -> **App content** -> **All files access**.
2. Select **Core functionality: File management** and explain that Prism indexes, previews, converts, annotates, merges, and organizes arbitrary user documents across internal storage and external SD volumes completely offline.
3. Attach a short screen-recording video demonstrating:
   - First-run onboarding clearly disclosing why All Files Access is requested.
   - Navigating to Android's **All files access** settings page.
   - Browsing and managing documents inside Prism once granted, AND Prism continuing to work cleanly in **Scoped Folder (SAF) Mode** if declined.
4. Keep `android:maxSdkVersion="29"` on `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` so Android 11+ relies solely on either declared All Files Access or zero-permission SAF (`ACTION_OPEN_DOCUMENT_TREE`).

### 2. Built-In Zero-Permission Degraded Mode (No Play Declaration Required)
If you wish to ship a Play Store build without requesting `MANAGE_EXTERNAL_STORAGE` (or when a user declines the permission at runtime), Prism seamlessly operates in **SAF Scoped Mode**:
- Users grant access to individual folders (e.g., `Documents`, `Downloads`, USB-OTG drives) via Android's Storage Access Framework (`OpenDocumentTree`) with persisted URI permissions.
- External "Open with Prism" / "Share to Prism" (`VIEW` / `SEND` / `SEND_MULTIPLE`) intents continue working with zero storage permissions.

### 3. Sideload / Direct APK / F-Droid Distribution Alternative
For full unrestricted All Files Access without Google Play review gates, distribute signed release APKs directly via GitHub Releases, Obtainium, or F-Droid using the standard release build configuration.
