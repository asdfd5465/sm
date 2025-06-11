package bankwiser.bankpromotion.material.ui.navigation

import androidx.compose.foundation.layout.padding // Needed for Modifier.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier // <<< IMPORT ADDED HERE
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import bankwiser.bankpromotion.material.auth.AuthViewModel
import bankwiser.bankpromotion.material.ui.screens.auth.LoginScreen
import bankwiser.bankpromotion.material.ui.screens.category.SubCategoryScreen
import bankwiser.bankpromotion.material.ui.screens.home.HomeScreen
import bankwiser.bankpromotion.material.ui.screens.notes.NoteReaderScreen
import bankwiser.bankpromotion.material.ui.screens.onboarding.OnboardingScreen
import bankwiser.bankpromotion.material.ui.screens.search.SearchScreen
import bankwiser.bankpromotion.material.ui.screens.splash.SplashScreen
import bankwiser.bankpromotion.material.ui.screens.topic.TopicContentScreen

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val MAIN_APP_GRAPH = "main_app_graph"
    const val HOME = "home"
    const val SEARCH = "search"
    const val TOPIC_CONTENT = "topic_content/{subCategoryId}/{subCategoryName}"
    const val NOTEREADER = "notereader/{noteId}"

    fun topicContent(subCategoryId: String, subCategoryName: String) = "topic_content/$subCategoryId/$subCategoryName"
    fun noteReader(noteId: String) = "notereader/$noteId"
}

@Composable
fun AppNavigation(authViewModel: AuthViewModel = viewModel()) {
    val navController = rememberNavController()
    val authState by authViewModel.authState.collectAsState()

    LaunchedEffect(authState.user) {
        if (authState.user == null &&
            navController.currentDestination?.route != Routes.LOGIN &&
            navController.currentDestination?.route != Routes.SPLASH &&
            navController.currentDestination?.route != Routes.ONBOARDING
        ) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    val startDestination = if (authState.user != null) Routes.MAIN_APP_GRAPH else Routes.SPLASH

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.SPLASH) {
            SplashScreen(onTimeout = {
                val destination = if (authViewModel.authState.value.user != null) Routes.MAIN_APP_GRAPH else Routes.ONBOARDING
                navController.navigate(destination) { popUpTo(Routes.SPLASH) { inclusive = true } }
            })
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onGetStarted = {
                navController.navigate(Routes.LOGIN) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate(Routes.MAIN_APP_GRAPH) { popUpTo(Routes.LOGIN) { inclusive = true } }
                }
            )
        }
        composable(Routes.MAIN_APP_GRAPH) {
            MainAppScaffold(authViewModel = authViewModel, appNavController = navController) // Pass appNavController
        }
        // Screens accessible from MainAppScaffold's NavHost (without bottom nav)
        // OR directly if not using the nested graph for these
        composable(
            route = Routes.TOPIC_CONTENT,
            arguments = listOf(
                navArgument("subCategoryId") { type = NavType.StringType },
                navArgument("subCategoryName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            TopicContentScreen(
                onNoteClick = { noteId -> navController.navigate(Routes.noteReader(noteId)) },
                onNavigateUp = { navController.navigateUp() },
                subCategoryName = backStackEntry.arguments?.getString("subCategoryName")
            )
        }
        composable(
            route = Routes.NOTEREADER,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType })
        ) {
            NoteReaderScreen(onNavigateUp = { navController.navigateUp() })
        }
    }
}


@Composable
fun MainAppScaffold(authViewModel: AuthViewModel, appNavController: NavHostController) { // Receive appNavController
    val mainNavController = rememberNavController()
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Search
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by mainNavController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            mainNavController.navigate(screen.route) {
                                popUpTo(mainNavController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(mainNavController, startDestination = Routes.HOME, Modifier.padding(innerPadding)) {
            composable(Routes.HOME) {
                HomeScreen(
                    authViewModel = authViewModel,
                    onCategoryClick = { categoryId ->
                        // This should navigate within the appNavController to the SubCategory list
                        appNavController.navigate("subcategories_list/$categoryId")
                    },
                    onSignOut = {
                        // AuthViewModel's state change will trigger navigation in AppNavigation's LaunchedEffect
                    }
                )
            }
            composable(Routes.SEARCH) {
                 SearchScreen(
                    onNavigateUp = { /* Decide if search needs up nav, or if it's a root tab */ },
                    onNoteClick = { noteId -> appNavController.navigate(Routes.noteReader(noteId))} // Use appNavController
                )
            }
            // This route is now part of appNavController, so it should be defined there or
            // appNavController should be passed to this SubCategoryScreen for further navigation.
            // For Phase 4, clicking a category in HomeScreen will directly go to TopicContentScreen.
            // The SubCategoryScreen list is temporarily removed to simplify navigation.
            // The `subcategories_list/{categoryId}` route is effectively handled by HomeScreen
            // which will pass necessary info to `TopicContentScreen` via AppNavController.
            // If you need a separate SubCategory list screen, it should be part of the appNavController graph.
        }
    }
}

sealed class BottomNavItem(var title:String, var icon:androidx.compose.ui.graphics.vector.ImageVector, var route:String){
    object Home : BottomNavItem("Home", Icons.Filled.Home, Routes.HOME)
    object Search : BottomNavItem("Search", Icons.Filled.Search, Routes.SEARCH)
}
