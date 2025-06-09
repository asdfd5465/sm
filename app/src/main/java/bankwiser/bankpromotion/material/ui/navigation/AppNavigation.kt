package bankwiser.bankpromotion.material.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import bankwiser.bankpromotion.material.ui.screens.category.SubCategoryScreen
import bankwiser.bankpromotion.material.ui.screens.home.HomeScreen
import bankwiser.bankpromotion.material.ui.screens.notes.NoteListScreen
import bankwiser.bankpromotion.material.ui.screens.notes.NoteReaderScreen
import bankwiser.bankpromotion.material.ui.screens.onboarding.OnboardingScreen
import bankwiser.bankpromotion.material.ui.screens.splash.SplashScreen

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SUBCATEGORIES = "subcategories/{categoryId}"
    const val NOTELIST = "notelist/{subCategoryId}"
    const val NOTEREADER = "notereader/{noteId}"

    fun subcategories(categoryId: String) = "subcategories/$categoryId"
    fun noteList(subCategoryId: String) = "notelist/$subCategoryId"
    fun noteReader(noteId: String) = "notereader/$noteId"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(onTimeout = {
                navController.navigate(Routes.ONBOARDING) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            })
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onGetStarted = {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }
        composable(Routes.HOME) {
            HomeScreen(onCategoryClick = { categoryId ->
                navController.navigate(Routes.subcategories(categoryId))
            })
        }
        composable(
            route = Routes.SUBCATEGORIES,
            arguments = listOf(navArgument("categoryId") { type = NavType.StringType })
        ) {
            SubCategoryScreen(
                onSubCategoryClick = { subCategoryId ->
                    navController.navigate(Routes.noteList(subCategoryId))
                },
                onNavigateUp = { navController.navigateUp() }
            )
        }
        composable(
            route = Routes.NOTELIST,
            arguments = listOf(navArgument("subCategoryId") { type = NavType.StringType })
        ) {
            NoteListScreen(
                onNoteClick = { noteId ->
                    navController.navigate(Routes.noteReader(noteId))
                },
                onNavigateUp = { navController.navigateUp() }
            )
        }
        composable(
            route = Routes.NOTEREADER,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType })
        ) {
            NoteReaderScreen(
                onNavigateUp = { navController.navigateUp() }
            )
        }
    }
}
