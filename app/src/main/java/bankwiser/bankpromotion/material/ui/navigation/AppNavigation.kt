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
import androidx.navigation.navArgument
import bankwiser.bankpromotion.material.ui.screens.auth.LoginScreen
import bankwiser.bankpromotion.material.ui.screens.category.SubCategoryScreen
import bankwiser.bankpromotion.material.ui.screens.home.HomeScreen
import bankwiser.bankpromotion.material.ui.screens.notes.NoteListScreen
import bankwiser.bankpromotion.material.ui.screens.notes.NoteReaderScreen
import bankwiser.bankpromotion.material.ui.screens.onboarding.OnboardingScreen
import bankwiser.bankpromotion.material.ui.screens.splash.SplashScreen
import bankwiser.bankpromotion.material.ui.viewmodel.AuthViewModel

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
fun AppNavigation(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    val authState by authViewModel.authState.collectAsState()
    val startDestination = if (authState.isLoggedIn) Routes.HOME else Routes.SPLASH

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.SPLASH) {
            SplashScreen(onTimeout = {
                navController.navigate(Routes.ONBOARDING) {
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
            // Navigate to Home if user successfully logs in
            LaunchedEffect(authState) {
                if(authState.isLoggedIn) {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            }
            LoginScreen(
                isLoading = authState.isLoading,
                onSignInClick = onSignInClick
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onCategoryClick = { categoryId ->
                    navController.navigate(Routes.subcategories(categoryId))
                },
                onSignOutClick = onSignOutClick
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
