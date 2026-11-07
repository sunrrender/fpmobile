package ru.funpaymobile.ui.nav

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.funpaymobile.AppViewModel
import ru.funpaymobile.UiState
import ru.funpaymobile.ui.components.ErrorView
import ru.funpaymobile.ui.components.LoadingView
import ru.funpaymobile.ui.screens.AboutScreen
import ru.funpaymobile.ui.screens.ChatDetailScreen
import ru.funpaymobile.ui.screens.ChatsScreen
import ru.funpaymobile.ui.screens.RoomChatScreen
import ru.funpaymobile.ui.screens.HistoryScreen
import ru.funpaymobile.ui.screens.HomeScreen
import ru.funpaymobile.ui.screens.LoginScreen
import ru.funpaymobile.ui.screens.NewLotScreen
import ru.funpaymobile.ui.screens.LotDetailScreen
import ru.funpaymobile.ui.screens.RealChatScreen
import ru.funpaymobile.ui.screens.RealMarketScreen
import ru.funpaymobile.ui.screens.LotsScreen
import ru.funpaymobile.ui.screens.MarketScreen
import ru.funpaymobile.ui.screens.MoreScreen
import ru.funpaymobile.ui.screens.OfferDetailScreen
import ru.funpaymobile.ui.screens.OrderDetailScreen
import ru.funpaymobile.ui.screens.OrdersScreen
import ru.funpaymobile.ui.screens.ProfileScreen
import ru.funpaymobile.ui.screens.UserProfileScreen
import ru.funpaymobile.ui.screens.SettingsScreen
import ru.funpaymobile.ui.theme.Accent
import ru.funpaymobile.ui.theme.Bg
import ru.funpaymobile.ui.theme.Muted
import ru.funpaymobile.ui.theme.Surface1

private enum class Tab(val route: String, val title: String, val icon: ImageVector) {
    Home("home", "Главная", Icons.Filled.Home),
    Market("market", "Площадка", Icons.Filled.Storefront),
    Orders("orders", "Заказы", Icons.AutoMirrored.Filled.ReceiptLong),
    Chats("chats", "Чаты", Icons.AutoMirrored.Filled.Chat),
    More("more", "Ещё", Icons.Filled.Menu)
}

private fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val ui by vm.ui.collectAsState()
    val settings by vm.settings.collectAsState()
    val chatMessages by vm.chatMessages.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val isReal = (ui as? UiState.Ready)?.data?.isDemo == false
    androidx.compose.runtime.LaunchedEffect(isReal) {
        if (isReal) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                while (true) {
                    kotlinx.coroutines.delay(60_000)
                    vm.refreshSilently()
                }
            }
        }
    }
    val local by vm.localMessages.collectAsState()
    val localOrders by vm.localOrders.collectAsState()
    val localChats by vm.localChats.collectAsState()

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = Tab.entries.any { it.route == route }
    val anim = settings.animations

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = Surface1) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { if (route != tab.route) nav.goTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Accent,
                                selectedTextColor = Accent,
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted,
                                indicatorColor = Accent.copy(alpha = 0.18f)
                            )
                        )
                    }
                }
            }
        }
    ) { inner ->
        val base = Modifier.fillMaxSize().padding(inner).statusBarsPadding()
        Box(if (showBar) base else base.navigationBarsPadding()) {
            when (val state = ui) {
                UiState.Loading -> LoadingView()
                is UiState.Error -> ErrorView(
                    state.message,
                    onRetry = vm::load,
                    onDemo = if (state.hasSavedKey) vm::useDemo else null,
                    onLogout = if (state.hasSavedKey) vm::disconnect else null
                )
                is UiState.Ready -> {
                    val data = remember(state.data, localOrders, localChats) {
                        if (state.data.isDemo) {
                            state.data.copy(
                                orders = localOrders + state.data.orders,
                                chats = localChats + state.data.chats
                            )
                        } else {
                            state.data
                        }
                    }
                    val openChatWith: (String) -> Unit = { name -> nav.navigate("chat/${vm.ensureChat(name)}") }
                    // Реальный режим: личный диалог с пользователем (существующий из списка или новый users-A-B).
                    val openDm: (Long?, String) -> Unit = { uid, name ->
                        val existing = data.chats.firstOrNull { it.name == name }
                        val me = data.profile.userId
                        if (existing != null) {
                            nav.navigate("chat/${existing.id}")
                        } else if (uid != null && me != 0L) {
                            val node = "users-${minOf(uid, me)}-${maxOf(uid, me)}"
                            vm.registerDm(node, name)
                            nav.navigate("chat/$node")
                        } else {
                            android.widget.Toast.makeText(context, "Не удалось определить собеседника", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                    NavHost(
                        navController = nav,
                        startDestination = Tab.Home.route,
                        enterTransition = {
                            if (anim) fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 14 } else EnterTransition.None
                        },
                        exitTransition = { if (anim) fadeOut(tween(160)) else ExitTransition.None },
                        popEnterTransition = { if (anim) fadeIn(tween(220)) else EnterTransition.None },
                        popExitTransition = { if (anim) fadeOut(tween(160)) else ExitTransition.None }
                    ) {
                        composable("home") {
                            HomeScreen(
                                data = data,
                                hideBalance = settings.hideBalance,
                                onOrders = { nav.goTab("orders") },
                                onChats = { nav.goTab("chats") },
                                onLots = { nav.navigate("lots") },
                                onMarket = { nav.goTab("market") }
                            )
                        }
                        composable("market") {
                            if (!data.isDemo) RealMarketScreen(
                                loadGames = vm::marketGames,
                                loadOffers = vm::marketOffers,
                                loadOfferInfo = vm::offerInfo,
                                loadForms = vm::offerForms,
                                buy = vm::buy,
                                confirmOrder = vm::buyConfirm,
                                loadBuyFields = vm::buyFields,
                                loadProfile = vm::userProfile,
                                onMessageSeller = { o -> openDm(o.sellerId, o.sellerName) }
                            ) else MarketScreen(
                                data = data,
                                onOpen = { nav.navigate("offer/$it") },
                                onOpenRoom = { nav.navigate("room/${Uri.encode(it)}") }
                            )
                        }
                        composable("room/{cat}") { e ->
                            RoomChatScreen(
                                data = data,
                                localMessages = local,
                                category = e.arguments?.getString("cat"),
                                onSend = vm::addLocalMessage,
                                onBack = { nav.popBackStack() }
                            )
                        }
                        composable("offer/{id}") { e ->
                            OfferDetailScreen(
                                data = data,
                                id = e.arguments?.getString("id"),
                                onBack = { nav.popBackStack() },
                                onCreateOrder = { offer, qty ->
                                    val orderId = vm.createOrder(offer, qty)
                                    nav.navigate("order/$orderId")
                                },
                                onMessage = { name -> nav.navigate("chat/${vm.ensureChat(name)}") }
                            )
                        }
                        composable("orders") { OrdersScreen(data) { nav.navigate("order/${it.removePrefix("#")}") } }
                        composable("order/{id}") { e ->
                            OrderDetailScreen(
                                data = data,
                                id = e.arguments?.getString("id"),
                                onBack = { nav.popBackStack() },
                                onMessage = { order -> if (data.isDemo) openChatWith(order.buyer) else openDm(order.counterpartId, order.buyer) },
                                loadInfo = if (data.isDemo) null else vm::orderInfo
                            )
                        }
                        composable("chats") {
                            ChatsScreen(
                                data = data,
                                onOpen = { nav.navigate("chat/$it") },
                                onOpenRoom = { nav.navigate("room/${Uri.encode(it)}") },
                                onRefresh = vm::refreshSilently
                            )
                        }
                        composable("chat/{id}") { e ->
                            if (data.isDemo) {
                                ChatDetailScreen(
                                    data = data,
                                    localMessages = local,
                                    id = e.arguments?.getString("id"),
                                    onSend = vm::addLocalMessage,
                                    onBack = { nav.popBackStack() }
                                )
                            } else {
                                RealChatScreen(
                                    data = data,
                                    id = e.arguments?.getString("id"),
                                    title = e.arguments?.getString("id")?.let(vm::dmTitle),
                                    messages = chatMessages,
                                    refresh = vm::refreshChat,
                                    send = vm::sendChat,
                                    onBack = { nav.popBackStack() }
                                )
                            }
                        }
                        composable("lots") {
                            LotsScreen(
                                data = data,
                                onBack = { nav.popBackStack() },
                                onNew = if (data.isDemo) null else ({ nav.navigate("newlot") })
                            ) { nav.navigate("lot/$it") }
                        }
                        composable("newlot") {
                            NewLotScreen(
                                loadGames = vm::marketGames,
                                loadStep = vm::lotStep,
                                search = vm::lotSearch,
                                publish = vm::publishLot,
                                onBack = { nav.popBackStack() },
                                onPublished = { nav.popBackStack() }
                            )
                        }
                        composable("lot/{id}") { e ->
                            LotDetailScreen(data, e.arguments?.getString("id")) { nav.popBackStack() }
                        }
                        composable("more") {
                            MoreScreen(
                                data = data,
                                onProfile = { nav.navigate("profile") },
                                onLogin = { nav.navigate("login") },
                                onLots = { nav.navigate("lots") },
                                onHistory = { nav.navigate("history") },
                                onSettings = { nav.navigate("settings") },
                                onAbout = { nav.navigate("about") }
                            )
                        }
                        composable("profile") {
                            ProfileScreen(
                                data = data,
                                onBack = { nav.popBackStack() },
                                onLogin = { nav.navigate("login") },
                                onLogout = { vm.disconnect(); nav.popBackStack() },
                                onMyReviews = { nav.navigate("myreviews") }
                            )
                        }
                        composable("myreviews") {
                            UserProfileScreen(
                                userId = data.profile.userId,
                                own = true,
                                loadProfile = vm::userProfile,
                                sendReply = vm::replyReview,
                                onMessage = null,
                                onBack = { nav.popBackStack() }
                            )
                        }
                        composable("login") {
                            LoginScreen(
                                secureStorageAvailable = vm.secureStorageAvailable,
                                onConnect = vm::connect,
                                onDone = { nav.popBackStack() },
                                onBack = { nav.popBackStack() }
                            )
                        }
                        composable("history") { HistoryScreen(data) { nav.popBackStack() } }
                        composable("settings") {
                            SettingsScreen(
                                settings = settings,
                                onHideBalance = vm::setHideBalance,
                                onAnimations = vm::setAnimations,
                                onBack = { nav.popBackStack() }
                            )
                        }
                        composable("about") { AboutScreen(isDemo = data.isDemo) { nav.popBackStack() } }
                    }
                }
            }
        }
    }
}
