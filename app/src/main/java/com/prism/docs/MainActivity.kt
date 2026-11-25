package com.prism.docs

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.compose.material3.MaterialTheme
import androidx.hilt.navigation.compose.hiltViewModel
import com.prism.docs.core.data.model.DocumentCategory
import com.prism.docs.core.data.model.IncomingDocumentRequest
import com.prism.docs.core.data.model.ThemeMode
import com.prism.docs.core.ui.glass.DriftingBlobsBackdrop
import com.prism.docs.core.ui.glass.GlassFloatingTabBar
import com.prism.docs.core.ui.glass.GlassNavigationRail
import com.prism.docs.core.ui.glass.GlassSheetDetent
import com.prism.docs.core.ui.glass.GlassTabItem
import com.prism.docs.core.ui.glass.PrismBlobsDark
import com.prism.docs.core.ui.glass.PrismBlobsLight
import com.prism.docs.core.ui.glass.ProvideGlassMotion
import com.prism.docs.core.ui.glass.rememberTabBarNestedScrollConnection
import com.prism.docs.core.ui.theme.LocalGlassEnvironment
import com.prism.docs.core.ui.theme.LocalHazeState
import com.prism.docs.core.ui.theme.PrismTheme
import com.prism.docs.feature.files.CollectionDetailScreen
import com.prism.docs.feature.files.DuplicateFinderScreen
import com.prism.docs.feature.files.FilesHomeScreen
import com.prism.docs.feature.files.FilesViewModel
import com.prism.docs.feature.files.StorageAnalyzerScreen
import com.prism.docs.feature.editor.EditorContractScreen
import com.prism.docs.feature.pdftools.PdfToolsHubScreen
import com.prism.docs.feature.scan.ScanHubScreen
import com.prism.docs.feature.search.SearchScreen
import com.prism.docs.feature.viewer.ViewerShellScreen
import com.prism.docs.navigation.CollectionDetailRoute
import com.prism.docs.navigation.DuplicateFinderRoute
import com.prism.docs.navigation.EditorRoute
import com.prism.docs.navigation.LibraryRoute
import com.prism.docs.navigation.OnboardingRoute
import com.prism.docs.navigation.PdfToolsRoute
import com.prism.docs.navigation.ScanRoute
import com.prism.docs.navigation.SearchRoute
import com.prism.docs.navigation.StorageAnalyzerRoute
import com.prism.docs.navigation.ViewerRoute
import com.prism.docs.onboarding.PrismOnboardingScreen
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  private val viewModel: PrismViewModel by viewModels()

  @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    requestHighestRefreshRate()
    viewModel.handleIncomingIntent(intent)

    setContent {
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()
      val windowSizeClass = calculateWindowSizeClass(this)

      val systemDark = isSystemInDarkTheme()
      val isDarkTheme =
        when (uiState.preferences.themeMode) {
          ThemeMode.SYSTEM -> systemDark
          ThemeMode.LIGHT -> false
          ThemeMode.DARK -> true
        }

      LaunchedEffect(isDarkTheme) {
        val scrim = Color.Transparent.toArgb()
        enableEdgeToEdge(
          statusBarStyle =
            if (isDarkTheme) SystemBarStyle.dark(scrim) else SystemBarStyle.light(scrim, scrim),
          navigationBarStyle =
            if (isDarkTheme) SystemBarStyle.dark(scrim) else SystemBarStyle.light(scrim, scrim),
        )
      }

      PrismTheme(
        darkTheme = isDarkTheme,
        dynamicColor = uiState.preferences.useDynamicColor,
        blurScale = uiState.preferences.glassIntensity.blurScale,
        tintScale = uiState.preferences.glassIntensity.tintScale,
        reduceTransparency = uiState.preferences.reduceTransparency,
        reduceMotion = uiState.preferences.reduceMotion,
      ) {
        ProvideGlassMotion(appReduceMotion = uiState.preferences.reduceMotion) {
          if (uiState.isLoaded) {
            PrismRootShell(
              uiState = uiState,
              windowWidthSizeClass = windowSizeClass.widthSizeClass,
              viewModel = viewModel,
            )
          } else {
            DriftingBlobsBackdrop(
              modifier = Modifier.fillMaxSize(),
              base = if (isDarkTheme) MaterialTheme.colorScheme.background else Color(0xFFE8EDF9),
              blobs = if (isDarkTheme) PrismBlobsDark else PrismBlobsLight,
              blobAlpha = if (uiState.preferences.reduceTransparency) 0f else if (isDarkTheme) 0.82f else 1f,
            )
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    viewModel.handleIncomingIntent(intent)
  }

  private fun requestHighestRefreshRate() {
    runCatching {
      val activeDisplay =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display
        else @Suppress("DEPRECATION") windowManager.defaultDisplay
      val currentMode = activeDisplay?.mode ?: return
      val bestMode =
        activeDisplay.supportedModes
          .filter {
            it.physicalWidth == currentMode.physicalWidth &&
              it.physicalHeight == currentMode.physicalHeight
          }
          .maxByOrNull { it.refreshRate }
          ?: return
      if (bestMode.modeId != currentMode.modeId) {
        val params = window.attributes
        params.preferredDisplayModeId = bestMode.modeId
        window.attributes = params
      }
    }
  }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PrismRootShell(
  uiState: PrismAppUiState,
  windowWidthSizeClass: WindowWidthSizeClass,
  viewModel: PrismViewModel,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val navController = rememberNavController()
  val hazeState = rememberHazeState()

  var isTabBarCollapsed by rememberSaveable { mutableStateOf(false) }
  var isBottomSearchExpanded by rememberSaveable { mutableStateOf(false) }
  var bottomSearchQuery by rememberSaveable { mutableStateOf("") }
  var showGlassSettings by rememberSaveable { mutableStateOf(false) }
  var settingsDetent by remember { mutableStateOf(GlassSheetDetent.HALF) }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshStorageAccess()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val allFilesSettingsLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      viewModel.refreshStorageAccess()
    }

  val legacyStoragePermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      viewModel.refreshStorageAccess()
    }

  val safTreePickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      if (uri != null) {
        viewModel.onSafTreePicked(uri)
      }
    }

  val requestAllFilesAccess: () -> Unit = {
    val manager = viewModel.storagePermissionManager
    if (manager.requiresLegacyStoragePermissions()) {
      legacyStoragePermissionLauncher.launch(manager.legacyStoragePermissions())
    } else {
      allFilesSettingsLauncher.launch(manager.buildAllFilesAccessIntent())
    }
  }

  val openSafPicker: () -> Unit = { safTreePickerLauncher.launch(null) }

  LaunchedEffect(uiState.pendingIncomingDocument) {
    val incoming = uiState.pendingIncomingDocument ?: return@LaunchedEffect
    navController.navigate(
      ViewerRoute(
        uriString = incoming.uriString,
        displayName = incoming.displayName,
        mimeType = incoming.mimeType,
        sizeBytes = incoming.sizeBytes,
        sourceAction = incoming.sourceAction,
      )
    )
    viewModel.consumeIncomingDocument()
  }

  val tabs = remember {
    listOf(
      GlassTabItem(
        id = "library",
        label = "Library",
        icon = Icons.Outlined.Folder,
        selectedIcon = Icons.Rounded.Folder,
      ),
      GlassTabItem(
        id = "scan",
        label = "Scan",
        icon = Icons.Outlined.DocumentScanner,
        selectedIcon = Icons.Rounded.DocumentScanner,
      ),
      GlassTabItem(
        id = "pdftools",
        label = "Tools",
        icon = Icons.Outlined.FilterNone,
        selectedIcon = Icons.Rounded.FilterNone,
      ),
    )
  }

  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentDest = backStackEntry?.destination
  val isTopLevelDestination =
    currentDest != null &&
      (currentDest.hasRoute<LibraryRoute>() ||
        currentDest.hasRoute<ScanRoute>() ||
        currentDest.hasRoute<PdfToolsRoute>())

  val selectedTabId =
    when {
      currentDest?.hasRoute<ScanRoute>() == true -> "scan"
      currentDest?.hasRoute<PdfToolsRoute>() == true -> "pdftools"
      else -> "library"
    }

  val selectTab: (String) -> Unit = { tabId ->
    isTabBarCollapsed = false
    isBottomSearchExpanded = false
    val target: Any =
      when (tabId) {
        "scan" -> ScanRoute
        "pdftools" -> PdfToolsRoute
        else -> LibraryRoute
      }
    navController.navigate(target) {
      popUpTo(LibraryRoute) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  val openBottomSearch: () -> Unit = {
    isTabBarCollapsed = false
    if (selectedTabId != "library") {
      navController.navigate(LibraryRoute) {
        popUpTo(LibraryRoute) { saveState = true }
        launchSingleTop = true
        restoreState = true
      }
    }
    isBottomSearchExpanded = true
  }

  val closeBottomSearch: () -> Unit = {
    isBottomSearchExpanded = false
    bottomSearchQuery = ""
  }

  BackHandler(enabled = isBottomSearchExpanded) {
    closeBottomSearch()
  }

  val useRailLayout = windowWidthSizeClass != WindowWidthSizeClass.Compact
  val startDestination: Any =
    if (uiState.preferences.onboardingCompleted) LibraryRoute else OnboardingRoute

  LaunchedEffect(isTopLevelDestination) {
    if (!isTopLevelDestination) {
      isTabBarCollapsed = false
      isBottomSearchExpanded = false
    }
  }

  val tabBarNestedScroll =
    rememberTabBarNestedScrollConnection(
      isCollapsed = { isTabBarCollapsed },
      onCollapsedChanged = { isTabBarCollapsed = it },
    )

  val env = LocalGlassEnvironment.current
  val backdropBase = if (env.isDark) MaterialTheme.colorScheme.background else Color(0xFFE8EDF9)
  val backdropBlobs = if (env.isDark) PrismBlobsDark else PrismBlobsLight
  val backdropAlpha = if (env.reduceTransparency) 0f else if (env.isDark) 0.82f else 1f

  CompositionLocalProvider(LocalHazeState provides hazeState) {
    SharedTransitionLayout {
      Box(modifier = Modifier.fillMaxSize()) {
        DriftingBlobsBackdrop(
          modifier = Modifier.fillMaxSize().hazeSource(state = hazeState, zIndex = 0f),
          base = backdropBase,
          blobs = backdropBlobs,
          blobAlpha = backdropAlpha,
        )

        Row(modifier = Modifier.fillMaxSize()) {
          if (useRailLayout && isTopLevelDestination) {
            GlassNavigationRail(
              tabs = tabs,
              selectedId = selectedTabId,
              onSelectTab = selectTab,
              onSearchClick = { navController.navigate(SearchRoute) },
            )
          }

          Box(
            modifier =
              Modifier.weight(1f)
                .fillMaxSize()
                .then(
                  if (!useRailLayout && isTopLevelDestination) {
                    Modifier.nestedScroll(tabBarNestedScroll)
                  } else {
                    Modifier
                  }
                )
          ) {
            NavHost(
              navController = navController,
              startDestination = startDestination,
              enterTransition = { fadeIn(animationSpec = tween(durationMillis = 160)) },
              exitTransition = { fadeOut(animationSpec = tween(durationMillis = 120)) },
              popEnterTransition = { fadeIn(animationSpec = tween(durationMillis = 160)) },
              popExitTransition = { fadeOut(animationSpec = tween(durationMillis = 120)) },
              modifier = Modifier.fillMaxSize(),
            ) {
              composable<OnboardingRoute> {
                PrismOnboardingScreen(
                  storageAccessState = uiState.storageAccessState,
                  onGrantAllFilesAccess = requestAllFilesAccess,
                  onPickSafFolder = openSafPicker,
                  onContinueToApp = {
                    viewModel.completeOnboarding()
                    navController.navigate(LibraryRoute) {
                      popUpTo(OnboardingRoute) { inclusive = true }
                    }
                  },
                )
              }

              composable<LibraryRoute> { entry ->
                val filesViewModel: FilesViewModel = hiltViewModel()
                val filesUiState by filesViewModel.uiState.collectAsStateWithLifecycle()
                val applyAnalyzerFilter by
                  entry.savedStateHandle
                    .getStateFlow("analyzer_filter_apply", false)
                    .collectAsStateWithLifecycle()

                var lastPushedBottomQuery by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(bottomSearchQuery) {
                  if (lastPushedBottomQuery != bottomSearchQuery) {
                    lastPushedBottomQuery = bottomSearchQuery
                    filesViewModel.setNamePrefix(bottomSearchQuery)
                  }
                }
                LaunchedEffect(filesUiState.query.namePrefix) {
                  val vmPrefix = filesUiState.query.namePrefix.orEmpty()
                  if (vmPrefix.isEmpty() && bottomSearchQuery.isNotEmpty() && lastPushedBottomQuery == bottomSearchQuery) {
                    bottomSearchQuery = ""
                    lastPushedBottomQuery = ""
                  }
                }

                LaunchedEffect(applyAnalyzerFilter) {
                  if (applyAnalyzerFilter) {
                    val catName = entry.savedStateHandle.get<String>("analyzer_filter_category")
                    val otherOnly = entry.savedStateHandle.get<Boolean>("analyzer_filter_other") ?: false
                    val cat = catName?.let { runCatching { DocumentCategory.valueOf(it) }.getOrNull() }
                    filesViewModel.applyAnalyzerCategoryFilter(cat, otherOnly)
                    entry.savedStateHandle["analyzer_filter_apply"] = false
                  }
                }

                FilesHomeScreen(
                  preferences = uiState.preferences,
                  storageAccessState = uiState.storageAccessState,
                  onRequestAllFilesAccess = requestAllFilesAccess,
                  onAddSafFolder = openSafPicker,
                  onRemoveSafFolder = viewModel::removeSafFolder,
                  onDismissAccessBanner = viewModel::dismissFullAccessBanner,
                  onOpenGlassSettings = {
                    settingsDetent = GlassSheetDetent.HALF
                    showGlassSettings = true
                  },
                  onOpenSearch = {
                    if (useRailLayout) navController.navigate(SearchRoute) else openBottomSearch()
                  },
                  onOpenDocument = { doc ->
                    navController.navigate(
                      ViewerRoute(
                        uriString = doc.uriString,
                        displayName = doc.displayName,
                        mimeType = doc.mimeType,
                        sizeBytes = doc.sizeBytes,
                        sourceAction = doc.sourceAction,
                      )
                    )
                  },
                  onTabBarCollapsedChange = { isTabBarCollapsed = it },
                  onOpenDuplicateFinder = { navController.navigate(DuplicateFinderRoute) },
                  onOpenStorageAnalyzer = { navController.navigate(StorageAnalyzerRoute) },
                  onOpenCollectionDetail = { colId ->
                    navController.navigate(CollectionDetailRoute(colId))
                  },
                  isDebugBuild = BuildConfig.DEBUG,
                  hasBottomTabBar = !useRailLayout,
                  viewModel = filesViewModel,
                )
              }

              composable<DuplicateFinderRoute> {
                DuplicateFinderScreen(
                  onNavigateBack = { navController.popBackStack() },
                  onOpenDocument = { doc ->
                    navController.navigate(
                      ViewerRoute(
                        uriString = doc.uriString,
                        displayName = doc.displayName,
                        mimeType = doc.mimeType,
                        sizeBytes = doc.sizeBytes,
                        sourceAction = doc.sourceAction,
                      )
                    )
                  },
                )
              }

              composable<StorageAnalyzerRoute> {
                StorageAnalyzerScreen(
                  onNavigateBack = { navController.popBackStack() },
                  onFilterLibraryByCategory = { category, otherOnly ->
                    runCatching {
                      navController.getBackStackEntry<LibraryRoute>().savedStateHandle.apply {
                        set("analyzer_filter_category", category?.name)
                        set("analyzer_filter_other", otherOnly)
                        set("analyzer_filter_apply", true)
                      }
                    }
                    navController.popBackStack()
                  },
                  onOpenDocument = { doc ->
                    navController.navigate(
                      ViewerRoute(
                        uriString = doc.uriString,
                        displayName = doc.displayName,
                        mimeType = doc.mimeType,
                        sizeBytes = doc.sizeBytes,
                        sourceAction = doc.sourceAction,
                      )
                    )
                  },
                )
              }

              composable<CollectionDetailRoute> {
                CollectionDetailScreen(
                  onNavigateBack = { navController.popBackStack() },
                  onOpenDocument = { doc ->
                    navController.navigate(
                      ViewerRoute(
                        uriString = doc.uriString,
                        displayName = doc.displayName,
                        mimeType = doc.mimeType,
                        sizeBytes = doc.sizeBytes,
                        sourceAction = doc.sourceAction,
                      )
                    )
                  },
                )
              }

              composable<ScanRoute> {
                ScanHubScreen(
                  onTabBarCollapsedChange = { isTabBarCollapsed = it },
                )
              }

              composable<PdfToolsRoute> {
                PdfToolsHubScreen(
                  onTabBarCollapsedChange = { isTabBarCollapsed = it },
                )
              }

              composable<SearchRoute> {
                SearchScreen(
                  onNavigateBack = { navController.popBackStack() },
                )
              }

              composable<ViewerRoute> { entry ->
                val route = entry.toRoute<ViewerRoute>()
                val doc =
                  route.uriString?.let { uri ->
                    IncomingDocumentRequest(
                      uriString = uri,
                      displayName = route.displayName ?: "Document",
                      mimeType = route.mimeType ?: "application/octet-stream",
                      sizeBytes = route.sizeBytes,
                      sourceAction = route.sourceAction,
                    )
                  }
                ViewerShellScreen(
                  document = doc,
                  onNavigateBack = { navController.popBackStack() },
                  onOpenExternalChooser = { uriStr, mime ->
                    runCatching {
                      val openIntent =
                        Intent(Intent.ACTION_VIEW).apply {
                          setDataAndType(Uri.parse(uriStr), mime)
                          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                      context.startActivity(Intent.createChooser(openIntent, "Open with"))
                    }
                  },
                  onOpenEditorRoute = { navController.navigate(EditorRoute) },
                )
              }

              composable<EditorRoute> {
                EditorContractScreen(
                  onNavigateBack = { navController.popBackStack() },
                )
              }
            }

            if (!useRailLayout && isTopLevelDestination) {
              GlassFloatingTabBar(
                tabs = tabs,
                selectedId = selectedTabId,
                onSelectTab = selectTab,
                onSearchClick = openBottomSearch,
                isCollapsed = isTabBarCollapsed,
                onCollapsedChange = { isTabBarCollapsed = it },
                isSearchExpanded = isBottomSearchExpanded,
                searchQuery = bottomSearchQuery,
                onSearchQueryChange = { bottomSearchQuery = it },
                onSearchExpandedChange = { expanded ->
                  if (expanded) openBottomSearch() else closeBottomSearch()
                },
                onSearchSubmit = {},
                modifier = Modifier.align(Alignment.BottomCenter),
              )
            }
          }
        }

        GlassSettingsSheet(
          visible = showGlassSettings,
          detent = settingsDetent,
          onDetentChange = { settingsDetent = it },
          onDismiss = { showGlassSettings = false },
          preferences = uiState.preferences,
          onThemeModeChange = viewModel::setThemeMode,
          onDynamicColorChange = viewModel::setUseDynamicColor,
          onGlassIntensityChange = viewModel::setGlassIntensity,
          onReduceTransparencyChange = viewModel::setReduceTransparency,
          onReduceMotionChange = viewModel::setReduceMotion,
        )
      }
    }
  }
}
