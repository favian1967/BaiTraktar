package com.example.baitraktar.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.baitraktar.data.OrdersRepository
import com.example.baitraktar.data.SettingsStore
import com.example.baitraktar.ui.components.KworkBottomBar
import com.example.baitraktar.ui.components.Tab
import com.example.baitraktar.ui.screens.AiReplyScreen
import com.example.baitraktar.ui.screens.FiltersScreen
import com.example.baitraktar.ui.screens.HomeScreen
import com.example.baitraktar.ui.screens.OrderDetailScreen
import com.example.baitraktar.ui.screens.OrdersScreen
import com.example.baitraktar.ui.screens.SettingsScreen
import com.example.baitraktar.ui.screens.UnlockScreen
import com.example.baitraktar.ui.screens.onboarding.OnboardingFlow

/** Screens outside the tab bar. Tab screens are handled by [Tab]. */
sealed interface Route {
    data object Onboarding : Route
    data object Tabs : Route
    data object Filters : Route
    data class OrderDetail(val id: String) : Route
    data class AiReply(val id: String) : Route
}

/** Route ↔ String, so the back stack survives rotation and process death. */
private fun Route.encode(): String = when (this) {
    Route.Onboarding -> "onboarding"
    Route.Tabs -> "tabs"
    Route.Filters -> "filters"
    is Route.OrderDetail -> "detail:$id"
    is Route.AiReply -> "reply:$id"
}

private fun decodeRoute(s: String): Route = when {
    s == "onboarding" -> Route.Onboarding
    s == "filters" -> Route.Filters
    s.startsWith("detail:") -> Route.OrderDetail(s.removePrefix("detail:"))
    s.startsWith("reply:") -> Route.AiReply(s.removePrefix("reply:"))
    else -> Route.Tabs
}

private val RouteStackSaver = listSaver<List<Route>, String>(
    save = { stack -> stack.map { it.encode() } },
    restore = { saved -> saved.map(::decodeRoute) },
)

/** Order links come from the server — a missing browser or a malformed URL must not crash the app. */
private fun openLink(context: Context, link: String) {
    if (link.isBlank()) {
        Toast.makeText(context, "У заказа нет ссылки", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Нет приложения, чтобы открыть ссылку", Toast.LENGTH_SHORT).show()
    }
}

/** After this long in the background a sealed token is dropped from memory and the app locks again. */
private const val RELOCK_AFTER_MS = 60_000L

/** State-based navigation: a saveable back stack of [Route]s plus the selected [Tab]. */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore.get(context) }
    val repository = remember { OrdersRepository(settingsStore) }
    val ordersViewModel: OrdersViewModel = viewModel(
        factory = OrdersViewModel.factory(repository, settingsStore),
    )
    val ordersState by ordersViewModel.state.collectAsState()
    val serverSettings by settingsStore.settings.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // elapsedRealtime, not wall clock: changing the time in system settings must not skip the lock.
        var backgroundedAt = 0L
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> backgroundedAt = SystemClock.elapsedRealtime()
                Lifecycle.Event.ON_START -> {
                    if (backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt > RELOCK_AFTER_MS) {
                        settingsStore.lock()
                    }
                    backgroundedAt = 0L
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var stack by rememberSaveable(stateSaver = RouteStackSaver) {
        mutableStateOf(listOf(if (settingsStore.settings.value.onboardingDone) Route.Tabs else Route.Onboarding))
    }
    var tab by rememberSaveable { mutableStateOf(Tab.SEARCH) }
    val current = stack.last()

    fun push(r: Route) { stack = stack + r }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }
    fun reset(r: Route) { stack = listOf(r) }

    BackHandler(enabled = stack.size > 1 && !serverSettings.isLocked) { pop() }

    if (serverSettings.isLocked && current != Route.Onboarding) {
        UnlockScreen(
            settingsStore = settingsStore,
            onUnlocked = ordersViewModel::refresh,
            onTokenLost = { reason ->
                Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
                ordersViewModel.refresh()
                reset(Route.Tabs)
                tab = Tab.SETTINGS
            },
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                val forward = initialState is Route.Tabs || initialState is Route.Onboarding
                val enter = slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()
                val exit = slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut()
                enter togetherWith exit
            },
            label = "route",
        ) { route ->
            when (route) {
                Route.Onboarding -> OnboardingFlow(onFinish = {
                    settingsStore.setOnboardingDone()
                    reset(Route.Tabs)
                })
                Route.Tabs -> Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        when (tab) {
                            Tab.SEARCH -> HomeScreen(
                                state = ordersState,
                                onRefresh = ordersViewModel::refresh,
                                onResume = ordersViewModel::refreshIfStale,
                                onOpenOrder = { push(Route.OrderDetail(it.id)) },
                                onHideOrder = { ordersViewModel.hideWithUndo(it.id) },
                                onUndoHide = ordersViewModel::undoHide,
                                onSeeAll = { tab = Tab.ORDERS },
                                onOpenSettings = { tab = Tab.SETTINGS },
                            )
                            Tab.ORDERS -> OrdersScreen(
                                orders = ordersState.orders,
                                isLoading = ordersState.isLoading,
                                error = ordersState.error,
                                onRefresh = ordersViewModel::refresh,
                                configured = ordersState.configured,
                                onOpenOrder = { push(Route.OrderDetail(it.id)) },
                                onRetry = ordersViewModel::refresh,
                                onOpenSettings = { tab = Tab.SETTINGS },
                            )
                            Tab.SETTINGS -> SettingsScreen(
                                settingsStore = settingsStore,
                                repository = repository,
                                onOpenFilters = { push(Route.Filters) },
                                onSettingsSaved = ordersViewModel::refresh,
                            )
                        }
                    }
                    KworkBottomBar(selected = tab, onSelect = { tab = it })
                }
                Route.Filters -> FiltersScreen(repository = repository, onBack = ::pop)
                is Route.OrderDetail -> {
                    val order = ordersViewModel.order(route.id)
                    if (order != null) {
                        OrderDetailScreen(
                            order = order,
                            onBack = ::pop,
                            onOpenKwork = { openLink(context, order.link) },
                            onHide = {
                                ordersViewModel.markAction(order.id, "dismissed")
                                pop()
                            },
                            onAiReply = { push(Route.AiReply(route.id)) },
                        )
                    } else {
                        // Order vanished after a refresh — leave the screen, but not from inside composition.
                        LaunchedEffect(route) { pop() }
                    }
                }
                is Route.AiReply -> {
                    val order = ordersViewModel.order(route.id)
                    if (order != null) {
                        AiReplyScreen(
                            order = order,
                            onBack = ::pop,
                            onSend = {
                                ordersViewModel.markAction(order.id, "responded")
                                reset(Route.Tabs)
                            },
                            isRefreshing = ordersState.isLoading,
                            onRefresh = ordersViewModel::refresh,
                            onOpenKwork = { openLink(context, order.link) },
                        )
                    } else {
                        // Order vanished after a refresh — leave the screen, but not from inside composition.
                        LaunchedEffect(route) { pop() }
                    }
                }
            }
        }
    }
}
