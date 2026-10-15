package com.homebasket.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.homebasket.core.designsystem.LocalBackdrop
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.homebasket.R
import com.homebasket.core.designsystem.LocalMessenger
import com.homebasket.core.designsystem.Messenger
import com.homebasket.feature.cooking.CookingScreen
import com.homebasket.feature.cooking.DayPlanScreen
import com.homebasket.feature.cooking.RecipeDetailScreen
import com.homebasket.feature.cooking.RecipeEditScreen
import com.homebasket.feature.cooking.RecipesScreen
import com.homebasket.feature.cooking.WeekPlanScreen
import com.homebasket.feature.home.HomeScreen
import com.homebasket.feature.inventory.AddStockScreen
import com.homebasket.feature.inventory.ExpiringScreen
import com.homebasket.feature.inventory.FridgeScreen
import com.homebasket.feature.inventory.HistoryScreen
import com.homebasket.feature.inventory.InventoryScreen
import com.homebasket.feature.inventory.VegetablesScreen
import com.homebasket.feature.inventory.WasteScreen
import com.homebasket.feature.products.ProductDetailScreen
import com.homebasket.feature.products.ProductEditScreen
import com.homebasket.feature.products.ProductListScreen
import com.homebasket.feature.reports.BudgetScreen
import com.homebasket.feature.reports.DocumentsScreen
import com.homebasket.feature.reports.ReportsScreen
import com.homebasket.feature.scan.BillScanScreen
import com.homebasket.feature.scan.ScanScreen
import com.homebasket.feature.scan.VoiceScreen
import com.homebasket.feature.settings.BackupScreen
import com.homebasket.feature.settings.CategoriesScreen
import com.homebasket.feature.settings.HouseholdScreen
import com.homebasket.feature.settings.LocationsScreen
import com.homebasket.feature.settings.NotificationsScreen
import com.homebasket.feature.settings.OnboardingScreen
import com.homebasket.feature.settings.SettingsScreen
import com.homebasket.feature.shopping.ListDetailScreen
import com.homebasket.feature.shopping.MonthlyScreen
import com.homebasket.feature.shopping.PurchaseDetailScreen
import com.homebasket.feature.shopping.PurchasesScreen
import com.homebasket.feature.shopping.RecordPurchaseScreen
import com.homebasket.feature.shopping.ShoppingModeScreen
import com.homebasket.feature.shopping.ShoppingScreen
import com.homebasket.feature.shopping.TomorrowScreen

object Routes {
    const val HOME = "home"
    const val INVENTORY = "inventory"
    const val COOKING = "cooking"
    const val SHOPPING = "shopping"
    const val REPORTS = "reports"

    const val ONBOARDING = "onboarding"
    const val SETTINGS = "settings"
    const val HOUSEHOLD = "household"
    const val NOTIFICATIONS = "notifications"
    const val BACKUP = "backup"
    const val CATEGORIES = "categories"
    const val LOCATIONS = "locations"

    const val PRODUCTS = "products"
    fun productEdit(id: Long = 0, barcode: String? = null, name: String? = null) =
        "product_edit/$id?barcode=${barcode.orEmpty()}&name=${android.net.Uri.encode(name.orEmpty())}"
    fun productDetail(id: Long) = "product/$id"

    /** mode: grocery | vegetable | opening */
    fun addStock(productId: Long = 0, mode: String = "grocery", locationId: Long = 0) = "add_stock?productId=$productId&mode=$mode&locationId=$locationId"

    const val FRIDGE = "fridge"
    const val EXPIRING = "expiring"
    const val WASTE = "waste"
    const val VEGETABLES = "vegetables"
    const val HISTORY = "history"

    fun dayPlan(epochDay: Long) = "day_plan/$epochDay"
    const val WEEK = "week_plan"
    const val RECIPES = "recipes"
    fun recipeEdit(id: Long = 0) = "recipe_edit/$id"
    fun recipeDetail(id: Long) = "recipe/$id"

    const val TOMORROW = "tomorrow"
    const val MONTHLY = "monthly"
    fun listDetail(id: Long) = "list/$id"
    fun shoppingMode(id: Long) = "shopping_mode/$id"
    const val RECORD_PURCHASE = "record_purchase"
    const val PURCHASES = "purchases"
    fun purchaseDetail(id: Long) = "purchase/$id"

    const val BUDGET = "budget"
    /** target: inventory | shopping */
    fun scan(target: String = "inventory") = "scan?target=$target"
    const val VOICE = "voice"
    const val BILL_SCAN = "bill_scan"
    const val DOCUMENTS = "documents"
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, R.string.tab_home, Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Routes.INVENTORY, R.string.tab_inventory, Icons.Outlined.Kitchen, Icons.Filled.Kitchen),
    Tab(Routes.COOKING, R.string.tab_cooking, Icons.Outlined.RestaurantMenu, Icons.Filled.RestaurantMenu),
    Tab(Routes.SHOPPING, R.string.tab_shopping, Icons.Outlined.ShoppingCart, Icons.Filled.ShoppingCart),
    Tab(Routes.REPORTS, R.string.tab_reports, Icons.Outlined.BarChart, Icons.Filled.BarChart),
)

@Composable
fun HomeBasketApp(onboardingDone: Boolean, pendingRoute: String?, onRouteConsumed: () -> Unit) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val messenger = remember { Messenger(snackbar, scope) }
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showTabs = tabs.any { it.route == current }

    LaunchedEffect(pendingRoute, onboardingDone) {
        if (pendingRoute != null && onboardingDone) {
            if (tabs.any { it.route == pendingRoute }) nav.switchTab(pendingRoute) else nav.navigate(pendingRoute)
            onRouteConsumed()
        }
    }

    CompositionLocalProvider(LocalMessenger provides messenger) {
        Scaffold(
            modifier = Modifier.background(LocalBackdrop.current.brush),
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (showTabs) {
                    NavigationBar(
                        modifier = Modifier.shadow(12.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                    ) {
                        tabs.forEach { tab ->
                            val selected = current == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { nav.switchTab(tab.route) },
                                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, null) },
                                label = { Text(stringResource(tab.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                AppNavHost(nav, if (onboardingDone) Routes.HOME else Routes.ONBOARDING)
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun AppNavHost(nav: NavHostController, start: String) {
    val go: (String) -> Unit = { route ->
        if (tabs.any { it.route == route }) nav.switchTab(route) else nav.navigate(route) { launchSingleTop = true }
    }
    val back: () -> Unit = { nav.popBackStack() }
    val idArg = listOf(navArgument("id") { type = NavType.LongType })

    NavHost(nav, startDestination = start) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onDone = { nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } } })
        }
        composable(Routes.HOME) { HomeScreen(go) }
        composable(Routes.INVENTORY) { InventoryScreen(go) }
        composable(Routes.COOKING) { CookingScreen(go) }
        composable(Routes.SHOPPING) { ShoppingScreen(go) }
        composable(Routes.REPORTS) { ReportsScreen(go) }

        composable(Routes.SETTINGS) { SettingsScreen(go, back) }
        composable(Routes.HOUSEHOLD) { HouseholdScreen(back) }
        composable(Routes.NOTIFICATIONS) { NotificationsScreen(back) }
        composable(Routes.BACKUP) { BackupScreen(back) }
        composable(Routes.CATEGORIES) { CategoriesScreen(back) }
        composable(Routes.LOCATIONS) { LocationsScreen(back) }

        composable(Routes.PRODUCTS) { ProductListScreen(go, back) }
        composable(
            "product_edit/{id}?barcode={barcode}&name={name}",
            arguments = idArg + navArgument("barcode") { type = NavType.StringType; defaultValue = "" } + navArgument("name") { type = NavType.StringType; defaultValue = "" },
        ) { ProductEditScreen(back) }
        composable("product/{id}", arguments = idArg) { ProductDetailScreen(go, back) }
        composable(
            "add_stock?productId={productId}&mode={mode}&locationId={locationId}",
            arguments = listOf(
                navArgument("productId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("mode") { type = NavType.StringType; defaultValue = "grocery" },
                navArgument("locationId") { type = NavType.LongType; defaultValue = 0L },
            ),
        ) { AddStockScreen(go, back) }

        composable(Routes.FRIDGE) { FridgeScreen(go, back) }
        composable(Routes.EXPIRING) { ExpiringScreen(go, back) }
        composable(Routes.WASTE) { WasteScreen(back) }
        composable(Routes.VEGETABLES) { VegetablesScreen(go, back) }
        composable(Routes.HISTORY) { HistoryScreen(back) }

        composable("day_plan/{day}", arguments = listOf(navArgument("day") { type = NavType.LongType })) { DayPlanScreen(go, back) }
        composable(Routes.WEEK) { WeekPlanScreen(go, back) }
        composable(Routes.RECIPES) { RecipesScreen(go, back) }
        composable("recipe_edit/{id}", arguments = idArg) { RecipeEditScreen(back) }
        composable("recipe/{id}", arguments = idArg) { RecipeDetailScreen(go, back) }

        composable(Routes.TOMORROW) { TomorrowScreen(go, back) }
        composable(Routes.MONTHLY) { MonthlyScreen(go, back) }
        composable("list/{id}", arguments = idArg) { ListDetailScreen(go, back) }
        composable("shopping_mode/{id}", arguments = idArg) { ShoppingModeScreen(back) }
        composable(Routes.RECORD_PURCHASE) { RecordPurchaseScreen(back) }
        composable(Routes.PURCHASES) { PurchasesScreen(go, back) }
        composable("purchase/{id}", arguments = idArg) { PurchaseDetailScreen(back) }

        composable(Routes.BUDGET) { BudgetScreen(back) }
        composable(Routes.DOCUMENTS) { DocumentsScreen(back) }
        composable("scan?target={target}", arguments = listOf(navArgument("target") { type = NavType.StringType; defaultValue = "inventory" })) { ScanScreen(go, back) }
        composable(Routes.VOICE) { VoiceScreen(go, back) }
        composable(Routes.BILL_SCAN) { BillScanScreen(back) }
    }
}
