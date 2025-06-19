package bankwiser.bankpromotion.material.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person // For Profile Icon
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
import androidx.navigation.NavHostController
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
import bankwiser.bankpromotion.material.ui.screens.profile.ProfileScreen // Import ProfileScreen
import bankwiser.bankpromotion.material.ui.screens.search.SearchScreen
import bankwiser.bankpromotion.material.ui.screens.splash.SplashScreen
import bankwiser.bankpromotion.material.ui.screens.topic.TopicContentScreen
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val MAIN_APP_GRAPH = "main_app_graph"
    const val HOME = "home"
    const val SEARCH = "search"
    const val PROFILE = "profile" // New Profile Route
    const val SUB_CATEGORY_LIST = "subcategories_list/{categoryId}"
    const val TOPIC_CONTENT = "topic_content/{subCategoryId}/{subCategoryName}"
    const val NOTEREADER = "notereader/{noteId}"

    // Placeholder routes for ProfileScreen navigation actions
    const val MANAGE_DOWNLOADS = "manage_downloads"
    const val HELP_FEEDBACK = "help_feedback"
    const val RATE_APP = "rate_app_external" // Indicate it might be an external link
    const val TERMS_PRIVACY = "terms_privacy"


    fun subCategoryList(categoryId: String) = "subcategories_list/$categoryId"
    fun topicContent(subCategoryId: String, subCategoryName: String): String {
        val encodedName = URLEncoder.encode(subCategoryName, StandardCharsets.UTF_8.toString())
        return "topic_content/$subCategoryId/$encodedName"
    }
    fun noteReader(noteId: String) = "notereader/$noteId"
}

@Composable
fun AppNavigation(authViewModel: AuthViewModel = viewModel()) {
    val appNavController = rememberNavController()
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
        composable(
            route = Routes.SUB_CATEGORY_LIST,
            arguments = listOf(navArgument("categoryId") { type = NavType.StringType })
        ){
            SubCategoryScreen(
                onSubCategoryClick = { subCategoryId, subCategoryName ->
                    appNavController.navigate(Routes.topicContent(subCategoryId, subCategoryName))
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
            val subCategoryName = backStackEntry.arguments?.getString("subCategoryName") ?: "Topic"
            TopicContentScreen(
                onNoteClick = { noteId -> appNavController.navigate(Routes.noteReader(noteId)) },
                onNavigateUp = { appNavController.navigateUp() },
                subCategoryName = subCategoryName
            )
        }
        composable(
            route = Routes.NOTEREADER,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType })
        ) {
            NoteReaderScreen(onNavigateUp = { appNavController.navigateUp() })
        }

        // Placeholder composables for ProfileScreen navigation actions
        composable(Routes.MANAGE_DOWNLOADS) { Text("Manage Downloads Screen (TODO)") }
        composable(Routes.HELP_FEEDBACK) { Text("Help & Feedback Screen (TODO)") }
        composable(Routes.TERMS_PRIVACY) { Text("Terms & Privacy Screen (TODO)") }
        // RATE_APP would likely launch an external intent (Play Store)
    }
}


@Composable
fun MainAppScaffold(authViewModel: AuthViewModel, appNavController: NavHostController) {
    val bottomBarNavController = rememberNavController()
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Search,
        BottomNavItem.Profile // Added Profile
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
        NavHost(bottomBarNavController, startDestination = Routes.HOME, Modifier.padding(innerPadding)) {
            composable(Routes.HOME) {
                HomeScreen(
                    authViewModel = authViewModel,
                    onCategoryClick = { categoryId ->
                        appNavController.navigate(Routes.subCategoryList(categoryId))
                    },
                    onSignOut = { /* Handled by LaunchedEffect in AppNavigation */ }
                )
            }
            composable(Routes.SEARCH) {
                 SearchScreen(
                    onNavigateUp = { /* Root tab */ },
                    onNoteClick = { noteId -> appNavController.navigate(Routes.noteReader(noteId))}
                )
            }
            composable(Routes.PROFILE) { // Added ProfileScreen route
                ProfileScreen(
                    authViewModel = authViewModel, // Pass authViewModel for sign out
                    onNavigateToLogin = { // This would be handled by AppNavigation's LaunchedEffect
                        // No direct navigation needed here as authState change triggers it
                    },
                    onManageDownloads = { appNavController.navigate(Routes.MANAGE_DOWNLOADS) },
                    onHelpAndFeedback = { appNavController.navigate(Routes.HELP_FEEDBACK) },
                    onRateApp = { /* TODO: Implement Play Store rating intent */ },
                    onTermsAndPrivacy = { appNavController.navigate(Routes.TERMS_PRIVACY) }
                )
            }
        }
    }
}

sealed class BottomNavItem(var title:String, var icon:androidx.compose.ui.graphics.vector.ImageVector, var route:String){
    object Home : BottomNavItem("Home", Icons.Filled.Home, Routes.HOME)
    object Search : BottomNavItem("Search", Icons.Filled.Search, Routes.SEARCH)
    object Profile : BottomNavItem("Profile", Icons.Filled.Person, Routes.PROFILE) // Added Profile
}
