package bankwiser.bankpromotion.material.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
    const val MAIN_APP_GRAPH = "main_app_graph" // Parent route for screens after login
    const val HOME = "home"
    const val SEARCH = "search"
    // Note: SubCategoryScreen will be replaced by TopicContentScreen navigation
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
                popUpTo(navController.graph.id) { inclusive = true } // Clear entire back stack
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
        // Main App Graph with Bottom Navigation
        composable(Routes.MAIN_APP_GRAPH) {
            MainAppScaffold(authViewModel = authViewModel)
        }
        // Screens accessible from MainAppScaffold's NavHost (without bottom nav)
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
fun MainAppScaffold(authViewModel: AuthViewModel) {
    val mainNavController = rememberNavController() // Separate NavController for main content area
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Search
        // Add Profile later
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
                        // For Home, we navigate to SubCategoryScreen which then goes to TopicContent
                        // This part needs to be decided: Does Category click lead to a SubCategory list or directly to a topic (if only one subcat)?
                        // For now, let's assume it goes to a SubCategory list screen first.
                         mainNavController.navigate("subcategories_list/$categoryId") // A temporary route for SubCategory List
                    },
                    onSignOut = {
                        // AuthViewModel's state change will trigger navigation in AppNavigation's LaunchedEffect
                    }
                )
            }
            composable(Routes.SEARCH) {
                 SearchScreen(
                    onNavigateUp = { mainNavController.navigateUp() }, // Or handle differently if Search is a root tab
                    onNoteClick = { noteId -> mainNavController.navigate(Routes.noteReader(noteId))}
                )
            }
             // Temporary SubCategory List Screen - This will navigate to TopicContent
            composable(
                route = "subcategories_list/{categoryId}",
                 arguments = listOf(navArgument("categoryId") { type = NavType.StringType })
            ){
                SubCategoryScreen(
                    onSubCategoryClick = { subCategoryId ->
                         // Here, fetch the subCategoryName if needed before navigating
                         // For simplicity now, we might pass "Topic" or try to get it
                         // Ideally, SubCategory object would be available to get its name
                        mainNavController.navigate(Routes.topicContent(subCategoryId, "Topic Details")) // Pass a placeholder name
                    },
                    onNavigateUp = { mainNavController.navigateUp() }
                )
            }
            // Screens accessible from this NavHost (will be displayed above bottom nav)
            composable(
                route = Routes.TOPIC_CONTENT,
                arguments = listOf(
                    navArgument("subCategoryId") { type = NavType.StringType },
                    navArgument("subCategoryName") {type = NavType.StringType }
                )
            ) { backStackEntry ->
                TopicContentScreen(
                    onNoteClick = { noteId -> mainNavController.navigate(Routes.noteReader(noteId)) },
                    onNavigateUp = { mainNavController.navigateUp() },
                    subCategoryName = backStackEntry.arguments?.getString("subCategoryName")
                )
            }
            composable(
                route = Routes.NOTEREADER,
                arguments = listOf(navArgument("noteId") { type = NavType.StringType })
            ) {
                NoteReaderScreen(onNavigateUp = { mainNavController.navigateUp() })
            }
        }
    }
}

sealed class BottomNavItem(var title:String, var icon:androidx.compose.ui.graphics.vector.ImageVector, var route:String){
    object Home : BottomNavItem("Home", Icons.Filled.Home, Routes.HOME)
    object Search : BottomNavItem("Search", Icons.Filled.Search, Routes.SEARCH)
    // Later: object Profile : BottomNavItem("Profile", Icons.Filled.Person, Routes.PROFILE)
}
