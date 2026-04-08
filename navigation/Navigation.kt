package com.hemax.navigation

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hemax.screens.AuthScreen
import com.hemax.screens.ChatsScreen
import com.hemax.screens.ChatScreen

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Chats : Screen("chats")
    object Chat : Screen("chat/{chatId}") {
        fun pass(chatId: Long) = "chat/$chatId"
    }
    object Settings : Screen("settings")
    object Search : Screen("search")
    object EditProfile : Screen("edit_profile")
}

@Composable
fun HEmaxNavHost(
    startDestination: String = Screen.Auth.route,
    windowSizeClass: WindowSizeClass
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Auth.route) {
            AuthScreen(onAuthenticated = {
                navController.navigate(Screen.Chats.route) {
                    popUpTo(Screen.Auth.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Chats.route) {
            ChatsScreen(
                onChatSelected = { chatId -> navController.navigate(Screen.Chat.pass(chatId)) },
                onSearchClicked = { navController.navigate(Screen.Search.route) },
                onSettingsClicked = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(
            route = Screen.Chat.route,
            arguments = listOf(navArgument("chatId") { type = NavType.LongType })
        ) { backStack ->
            val chatId = backStack.arguments?.getLong("chatId") ?: 0L
            ChatScreen(chatId = chatId)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToEditProfile = { navController.navigate(Screen.EditProfile.route) },
                onLogout = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Search.route) {
            SearchScreen(
                onChatSelected = { chatId -> navController.navigate(Screen.Chat.pass(chatId)) },
                onClose = { navController.popBackStack() }
            )
        }
        composable(Screen.EditProfile.route) {
            EditProfileScreen(onSaved = { navController.popBackStack() })
        }
    }
}
