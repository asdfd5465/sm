package bankwiser.bankpromotion.material.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
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
    const val LOGIN = "login"
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

    // This effect will run when authState.user changes.
    // If user becomes null (signed out), navigate to Login.
    LaunchedEffect(authState.user) {
        if (authState.user == null && navController.currentDestination?.route != Routes.LOGIN && navController.currentDestination?.route != Routes.SPLASH && navController.currentDestination?.route != Routes.ONBOARDING) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val startDestination = if (authState.user != null) Routes.HOME else Routes.SPLASH

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.SPLASH) {
            SplashScreen(onTimeout = {
                val destination = if (authViewModel.authState.value.user != null) Routes.HOME else Routes.ONBOARDING
                navController.navigate(destination) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            })
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onGetStarted = {
                navController.navigate(Routes.LOGIN) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                authViewModel = authViewModel, // Pass the shared AuthViewModel
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                authViewModel = authViewModel, // Pass the shared AuthViewModel
                onCategoryClick = { categoryId ->
                    navController.navigate(Routes.subcategories(categoryId))
                },
                onSignOut = { // This callback is triggered from HomeScreen's sign out button
                    // The LaunchedEffect above will handle navigation when authState.user becomes null
                }
            )
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
