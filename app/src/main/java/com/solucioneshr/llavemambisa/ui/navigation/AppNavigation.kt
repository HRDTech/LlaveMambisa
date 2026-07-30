package com.solucioneshr.llavemambisa.ui.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.solucioneshr.llavemambisa.di.AppContainer
import com.solucioneshr.llavemambisa.ui.screens.*
import com.solucioneshr.llavemambisa.ui.viewmodel.*

private sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Conversations : Screen("conversations", "Mensajes", Icons.Filled.Forum)
    object Contacts     : Screen("contacts",      "Contactos", Icons.Filled.People)
    object Profile      : Screen("profile",       "Perfil",    Icons.Filled.AccountCircle)
}

private val bottomNavItems = listOf(Screen.Conversations, Screen.Contacts, Screen.Profile)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(container: AppContainer, startChatWith: String? = null) {
    val navController = rememberNavController()

    // Si llegamos desde una notificación con un chat específico, navegamos a él una sola vez
    LaunchedEffect(startChatWith) {
        if (!startChatWith.isNullOrBlank()) {
            navController.navigate("chat/${java.net.URLEncoder.encode(startChatWith, "UTF-8")}")
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = bottomNavItems.any { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->

        NavHost(
            navController = navController,
            startDestination = Screen.Conversations.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition  = { slideInHorizontally { it } + fadeIn() },
            exitTransition   = { slideOutHorizontally { -it } + fadeOut() },
            popEnterTransition  = { slideInHorizontally { -it } + fadeIn() },
            popExitTransition   = { slideOutHorizontally { it } + fadeOut() }
        ) {
            // ---- Pantalla de conversaciones (bottom nav) ----
            composable(Screen.Conversations.route) {
                val vm: ConversationsViewModel = viewModel(factory = ConversationsViewModel.Factory(container.smsRepository))
                ConversationsScreen(
                    viewModel = vm,
                    onOpenChat = { phone ->
                        navController.navigate("chat/${java.net.URLEncoder.encode(phone, "UTF-8")}")
                    },
                    onNewConversation = {
                        navController.navigate(Screen.Contacts.route) {
                            launchSingleTop = true
                        }
                    },
                    smsRepository = container.smsRepository
                )
            }

            // ---- Lista de contactos (bottom nav) ----
            composable(Screen.Contacts.route) {
                val vm: ContactsViewModel = viewModel(
                    factory = ContactsViewModel.Factory(container.smsRepository, container.deviceContactsRepository)
                )
                ContactsScreen(
                    viewModel = vm,
                    onContactClick = { phone ->
                        navController.navigate("chat/${java.net.URLEncoder.encode(phone, "UTF-8")}")
                    },
                    onAddContact = { navController.navigate("add_contact") }
                )
            }

            // ---- Perfil propio (bottom nav) ----
            composable(Screen.Profile.route) {
                val vm: ProfileViewModel = viewModel(
                    factory = ProfileViewModel.Factory(container.keyManager, container.userPreferences, container.smsRepository)
                )
                ProfileScreen(viewModel = vm)
            }

            // ---- Chat con un contacto ----
            composable(
                route = "chat/{phoneNumber}",
                arguments = listOf(navArgument("phoneNumber") { type = NavType.StringType })
            ) { backStackEntry ->
                val rawPhone = backStackEntry.arguments?.getString("phoneNumber") ?: return@composable
                val phone = java.net.URLDecoder.decode(rawPhone, "UTF-8")
                val vm: ChatViewModel = viewModel(factory = ChatViewModel.Factory(container.smsRepository, phone))
                ChatScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }

            // ---- Añadir contacto ----
            composable("add_contact") {
                val vm: AddContactViewModel = viewModel(
                    factory = AddContactViewModel.Factory(container.smsRepository, container.deviceContactsRepository)
                )
                AddContactScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onContactSaved = { phone ->
                        navController.navigate("chat/${java.net.URLEncoder.encode(phone, "UTF-8")}") {
                            popUpTo(Screen.Contacts.route)
                        }
                    }
                )
            }
        }
    }
}

