package app.copyruler.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.copyruler.AppContainer
import app.copyruler.data.AppSettings
import app.copyruler.ui.copy.CopyScreen
import app.copyruler.ui.copy.CopyViewModel
import app.copyruler.ui.editor.EditorScreen
import app.copyruler.ui.editor.EditorViewModel
import app.copyruler.ui.list.DocumentListScreen
import app.copyruler.ui.list.DocumentListViewModel
import app.copyruler.ui.settings.SettingsScreen
import app.copyruler.ui.settings.SettingsViewModel

private const val ARG_DOC_ID = "docId"
private const val ARG_FROM_COPY = "fromCopy"
private const val NO_ID = -1L

private object Routes {
    const val LIST = "documents"
    const val SETTINGS = "settings"
    const val COPY = "copy/{$ARG_DOC_ID}"
    const val EDITOR = "editor?$ARG_DOC_ID={$ARG_DOC_ID}&$ARG_FROM_COPY={$ARG_FROM_COPY}"

    fun copy(id: Long) = "copy/$id"

    fun editor(id: Long? = null, fromCopy: Boolean = false) =
        "editor?$ARG_DOC_ID=${id ?: NO_ID}&$ARG_FROM_COPY=$fromCopy"
}

/** Document list → text editor → copy screen, plus settings. */
@Composable
fun CopyRulerNavHost(container: AppContainer, settings: AppSettings) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.LIST) {
        composable(Routes.LIST) { entry ->
            val viewModel = viewModel { DocumentListViewModel(container.documentRepository, container.imageStore) }
            DocumentListScreen(
                viewModel = viewModel,
                onOpenDocument = { id -> navController.navigateFrom(entry, Routes.copy(id)) },
                onNewText = { navController.navigateFrom(entry, Routes.editor()) },
                onEditText = { id -> navController.navigateFrom(entry, Routes.editor(id)) },
                onOpenSettings = { navController.navigateFrom(entry, Routes.SETTINGS) },
            )
        }

        composable(
            route = Routes.EDITOR,
            arguments = listOf(
                navArgument(ARG_DOC_ID) {
                    type = NavType.LongType
                    defaultValue = NO_ID
                },
                navArgument(ARG_FROM_COPY) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            val documentId = entry.arguments?.getLong(ARG_DOC_ID)?.takeIf { it != NO_ID }
            val fromCopy = entry.arguments?.getBoolean(ARG_FROM_COPY) ?: false
            val viewModel = viewModel { EditorViewModel(documentId, container.documentRepository) }
            EditorScreen(
                viewModel = viewModel,
                onBack = { navController.popFrom(entry) },
                onSaved = { id ->
                    navController.popBackStack()
                    // Opened from the copy screen: just go back to it, it picks up the new text.
                    if (!fromCopy) navController.navigate(Routes.copy(id))
                },
            )
        }

        composable(
            route = Routes.COPY,
            arguments = listOf(navArgument(ARG_DOC_ID) { type = NavType.LongType }),
        ) { entry ->
            val documentId = entry.arguments?.getLong(ARG_DOC_ID) ?: NO_ID
            val viewModel = viewModel {
                CopyViewModel(
                    documentId = documentId,
                    repository = container.documentRepository,
                    imageStore = container.imageStore,
                    textRecognizer = container.textRecognizer,
                    settingsRepository = container.settingsRepository,
                )
            }
            CopyScreen(
                viewModel = viewModel,
                settings = settings,
                onBack = { navController.popFrom(entry) },
                onEditText = { navController.navigateFrom(entry, Routes.editor(documentId, fromCopy = true)) },
                onOpenDocument = { id -> navController.navigateFrom(entry, Routes.copy(id)) },
            )
        }

        composable(Routes.SETTINGS) { entry ->
            val viewModel = viewModel { SettingsViewModel(container.settingsRepository) }
            SettingsScreen(viewModel = viewModel, onBack = { navController.popFrom(entry) })
        }
    }
}

// User taps are ignored while a screen is animating in or out, so a double tap on "back"
// cannot pop two screens (or the last one, which would leave a blank window).

private fun NavBackStackEntry.isResumed() = lifecycle.currentState == Lifecycle.State.RESUMED

private fun NavHostController.popFrom(entry: NavBackStackEntry) {
    if (entry.isResumed()) popBackStack()
}

private fun NavHostController.navigateFrom(entry: NavBackStackEntry, route: String) {
    if (entry.isResumed()) navigate(route)
}
