package bankwiser.bankpromotion.material.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController // <<< IMPORT ADDED HERE
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
    // Route for the temporary SubCategory list screen inside MainAppScaffold
    const val SUB_CATEGORY_LIST = "subcategories_list/{categoryId}"
    const val TOPIC_CONTENT = "topic_content/{subCategoryId}/{subCategoryName}"
    const val NOTEREADER = "notereader/{noteId}"

    fun subCategoryList(categoryId: String) = "subcategories_list/$categoryId"
    fun topicContent(subCategoryId: String, subCategoryName: String) = "topic_content/$subCategoryId/$subCategoryName"
    fun noteReader(noteId: String) = "notereader/$noteId"
}

@Composable
fun AppNavigation(authViewModel: AuthViewModel = viewModel()) {
    val appNavController = rememberNavController() // This is the top-level NavController
    val authState by authViewModel.authState.collectAsState()

    LaunchedEffect(authState.user) {
        if (authState.user == null &&
            appNavController.currentDestination?.route != Routes.LOGIN &&
            appNavController.currentDestination?.route != Routes.SPLASH &&
            appNavController.currentDestination?.route != Routes.ONBOARDING
        ) {
            appNavController.navigate(Routes.LOGIN) {
                popUpTo(appNavController.graph.id) { inclusive = true }
            }
        }
    }

    val startDestination = if (authState.user != null) Routes.MAIN_APP_GRAPH else Routes.SPLASH

    NavHost(navController = appNavController, startDestination = startDestination) {
        composable(Routes.SPLASH) {
            SplashScreen(onTimeout = {
                val destination = if (authViewModel.authState.value.user != null) Routes.MAIN_APP_GRAPH else Routes.ONBOARDING
                appNavController.navigate(destination) { popUpTo(Routes.SPLASH) { inclusive = true } }
            })
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onGetStarted = {
                appNavController.navigate(Routes.LOGIN) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = {
                    appNavController.navigate(Routes.MAIN_APP_GRAPH) { popUpTo(Routes.LOGIN) { inclusive = true } }
                }
            )
        }
        composable(Routes.MAIN_APP_GRAPH) {
            MainAppScaffold(authViewModel = authViewModel, appNavController = appNavController)
        }
        // These routes are navigated to by appNavController from within MainAppScaffold's nested NavHost items
         composable(
            route = Routes.SUB_CATEGORY_LIST,
            arguments = listOf(navArgument("categoryId") { type = NavType.StringType })
        ){
            SubCategoryScreen( // This screen will use appNavController for its next navigation
                onSubCategoryClick = { subCategoryId ->
                    // Here, we need to get subCategoryName. For now, a placeholder.
                    // This navigation should be done by the appNavController passed to SubCategoryScreen
                    // or by having SubCategoryScreen accept appNavController as a parameter.
                    // For Phase 4, let's make SubCategoryScreen navigate directly.
                    appNavController.navigate(Routes.topicContent(subCategoryId, "Topic Details"))
                },
                onNavigateUp = { appNavController.navigateUp() }
            )
        }
        composable(
            route = Routes.TOPIC_CONTENT,
            arguments = listOf(
                navArgument("subCategoryId") { type = NavType.StringType },
                navArgument("subCategoryName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            TopicContentScreen(
                onNoteClick = { noteId -> appNavController.navigate(Routes.noteReader(noteId)) },
                onNavigateUp = { appNavController.navigateUp() },
                subCategoryName = backStackEntry.arguments?.getString("subCategoryName")
            )
        }
        composable(
            route = Routes.NOTEREADER,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType })
        ) {
            NoteReaderScreen(onNavigateUp = { appNavController.navigateUp() })
        }
    }
}


@Composable
fun MainAppScaffold(authViewModel: AuthViewModel, appNavController: NavHostController) {
    val bottomBarNavController = rememberNavController() // Separate NavController for Bottom Bar items
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Search
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by bottomBarNavController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            bottomBarNavController.navigate(screen.route) {
                                popUpTo(bottomBarNavController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        // NavHost for the content area controlled by the Bottom Navigation Bar
        NavHost(bottomBarNavController, startDestination = Routes.HOME, Modifier.padding(innerPadding)) {
            composable(Routes.HOME) {
                HomeScreen(
                    authViewModel = authViewModel,
                    onCategoryClick = { categoryId ->
                        // When a category is clicked, use the appNavController to go to the SubCategory list
                        appNavController.navigate(Routes.subCategoryList(categoryId))
                    },
                    onSignOut = {
                        // AuthViewModel's state change triggers navigation via LaunchedEffect in AppNavigation
                    }
                )
            }
            composable(Routes.SEARCH) {
                 SearchScreen(
                    onNavigateUp = { /* Search is a root tab, up navigation might not be standard */ },
                    onNoteClick = { noteId -> appNavController.navigate(Routes.noteReader(noteId))}
                )
            }
            // Note: TOPIC_CONTENT and NOTEREADER are NOT part of this bottomBarNavController's graph.
            // They are part of the appNavController's graph and will be displayed over this entire scaffold.
        }
    }
}

sealed class BottomNavItem(var title:String, var icon:androidx.compose.ui.graphics.vector.ImageVector, var route:String){
    object Home : BottomNavItem("Home", Icons.Filled.Home, Routes.HOME)
    object Search : BottomNavItem("Search", Icons.Filled.Search, Routes.SEARCH)
}
