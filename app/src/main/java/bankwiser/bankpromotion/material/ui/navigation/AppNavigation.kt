package bankwiser.bankpromotion.material.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import bankwiser.bankpromotion.material.auth.AuthViewModel
import bankwiser.bankpromotion.material.ui.screens.auth.LoginScreen
import bankwiser.bankpromotion.material.ui.screens.category.SubCategoryScreen
import bankwiser.bankpromotion.material.ui.screens.home.HomeScreen
import bankwiser.bankpromotion.material.ui.screens.notes.NoteListScreen
import bankwiser.bankpromotion.material.ui.screens.notes.NoteReaderScreen
import bankwiser.bankpromotion.material.ui.screens.onboarding.OnboardingScreen
import bankwiser.bankpromotion.material.ui.screens.splash.SplashScreen

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login" // New Login Route
    const val HOME = "home"
    const val SUBCATEGORIES = "subcategories/{categoryId}"
    const val NOTELIST = "notelist/{subCategoryId}"
    const val NOTEREADER = "notereader/{noteId}"

    fun subcategories(categoryId: String) = "subcategories/$categoryId"
    fun noteList(subCategoryId: String) = "notelist/$subCategoryId"
    fun noteReader(noteId: String) = "notereader/$noteId"
}

@Composable
fun AppNavigation(authViewModel: AuthViewModel = viewModel()) {
    val navController = rememberNavController()
    val authState by authViewModel.authState.collectAsState()

    // Determine the start destination based on auth state
    val startDestination = if (authState.user != null) Routes.HOME else Routes.SPLASH

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.SPLASH) {
            SplashScreen(onTimeout = {
                // If already logged in (e.g. from a previous session), go to Home, else Onboarding
                val destination = if (authViewModel.authState.value.user != null) Routes.HOME else Routes.ONBOARDING
                navController.navigate(destination) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            })
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onGetStarted = {
                navController.navigate(Routes.LOGIN) { // Go to Login after onboarding
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(onCategoryClick = { categoryId ->
                navController.navigate(Routes.subcategories(categoryId))
            })
            // For later: Add a sign-out button on the home screen or profile screen
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
