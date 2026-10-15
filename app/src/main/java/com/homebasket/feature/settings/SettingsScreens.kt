package com.homebasket.feature.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.homebasket.R
import com.homebasket.core.backup.BackupRepository
import com.homebasket.core.backup.RestoreCheck
import com.homebasket.core.data.CatalogRepository
import com.homebasket.core.data.HouseholdRepository
import com.homebasket.core.data.InventoryRepository
import com.homebasket.core.data.ReminderType
import com.homebasket.core.data.SettingsRepository
import com.homebasket.core.data.ThemeMode
import com.homebasket.core.database.entity.BudgetCategory
import com.homebasket.core.database.entity.Diet
import com.homebasket.core.database.entity.HouseholdEntity
import com.homebasket.core.database.entity.HouseholdSettingsEntity
import com.homebasket.core.database.entity.LocationKind
import com.homebasket.core.database.entity.ProductCategoryEntity
import com.homebasket.core.database.entity.StorageLocationEntity
import com.homebasket.core.designsystem.ConfirmDialog
import com.homebasket.core.designsystem.DropdownField
import com.homebasket.core.designsystem.HbScaffold
import com.homebasket.core.designsystem.InfoBanner
import com.homebasket.core.designsystem.LocalMessenger
import com.homebasket.core.designsystem.MoneyInput
import com.homebasket.core.designsystem.SectionTitle
import com.homebasket.core.designsystem.Tone
import com.homebasket.core.domain.model.Money
import com.homebasket.core.notifications.Notifier
import com.homebasket.core.notifications.ReminderScheduler
import com.homebasket.core.ui.budgetCategoryLabel
import com.homebasket.core.ui.householdDietLabel
import com.homebasket.core.ui.locationKindLabel
import com.homebasket.core.ui.reminderLabel
import com.homebasket.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

private val languages = listOf("" to R.string.lang_system, "en" to R.string.lang_english, "ta" to R.string.lang_tamil, "hi" to R.string.lang_hindi)

private fun currentLanguageTag(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',').substringBefore('-')
private fun setLanguage(tag: String) = AppCompatDelegate.setApplicationLocales(if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val household: HouseholdRepository,
    private val scheduler: ReminderScheduler,
    private val notifier: Notifier,
    private val catalog: CatalogRepository,
    private val inventory: InventoryRepository,
    private val backup: BackupRepository,
) : ViewModel() {
    val prefs = settings.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val householdFlow = household.household.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val settingsFlow = household.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val categories = catalog.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val locations = inventory.locations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTheme(m: ThemeMode) = viewModelScope.launch { settings.setTheme(m) }
    fun saveHousehold(h: HouseholdEntity, s: HouseholdSettingsEntity, onDone: () -> Unit = {}) = viewModelScope.launch { household.save(h, s); onDone() }
    fun finishOnboarding(h: HouseholdEntity, s: HouseholdSettingsEntity, onDone: () -> Unit) = viewModelScope.launch {
        household.save(h, s); settings.setOnboardingDone(); onDone()
    }

    fun canNotify() = notifier.canPost()
    fun setReminder(type: ReminderType, on: Boolean) = viewModelScope.launch { settings.setReminder(type, on); scheduler.rescheduleAll() }
    fun setTimes(morning: Int, evening: Int) = viewModelScope.launch { settings.setReminderTimes(morning, evening); scheduler.rescheduleAll() }

    fun saveCategory(c: ProductCategoryEntity) = viewModelScope.launch { catalog.saveCategory(c) }
    fun deleteCategory(c: ProductCategoryEntity, onResult: (Boolean) -> Unit) = viewModelScope.launch { onResult(catalog.deleteCategory(c)) }
    fun saveLocation(l: StorageLocationEntity) = viewModelScope.launch { inventory.saveLocation(l) }
    fun deleteLocation(l: StorageLocationEntity, onResult: (Boolean) -> Unit) = viewModelScope.launch { onResult(inventory.deleteLocation(l)) }

    fun export(uri: android.net.Uri, password: String?, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        onResult(runCatching { backup.export(uri, password) }.isSuccess)
    }
    fun inspect(uri: android.net.Uri, password: String?, onResult: (RestoreCheck) -> Unit) = viewModelScope.launch { onResult(backup.inspect(uri, password)) }
    fun restore(check: RestoreCheck.Ready, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = runCatching { backup.restore(check) }.isSuccess
        if (ok) scheduler.rescheduleAll()
        onResult(ok)
    }
    fun clearAll(onDone: () -> Unit) = viewModelScope.launch { backup.clearAllData(); onDone() }
}

@Composable
private fun LanguagePicker() {
    var tag by remember { mutableStateOf(currentLanguageTag()) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        languages.forEach { (t, label) -> FilterChip(tag == t, { tag = t; setLanguage(t) }, label = { Text(stringResource(label)) }) }
    }
}

/** First run: language, household size, diet and budget. Everything can be changed later. */
@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val h by vm.householdFlow.collectAsStateWithLifecycle()
    val s by vm.settingsFlow.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var members by rememberSaveable { mutableStateOf("4") }
    var diet by rememberSaveable { mutableStateOf(Diet.VEG) }
    var budget by rememberSaveable { mutableStateOf("8000") }
    var vegBudget by rememberSaveable { mutableStateOf("800") }

    HbScaffold(title = stringResource(R.string.welcome_title)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.welcome_text), style = MaterialTheme.typography.bodyLarge)
            SectionTitle(stringResource(R.string.language))
            LanguagePicker()
            SectionTitle(stringResource(R.string.your_household))
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.household_name)) }, placeholder = { Text(stringResource(R.string.household_name_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(members, { members = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.family_members)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Diet.entries.forEach { d -> FilterChip(diet == d, { diet = d }, label = { Text(stringResource(householdDietLabel(d))) }) }
            }
            MoneyInput(budget, { budget = it }, stringResource(R.string.monthly_grocery_budget))
            MoneyInput(vegBudget, { vegBudget = it }, stringResource(R.string.weekly_veg_budget))
            Text(stringResource(R.string.offline_promise), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                enabled = h != null && s != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                onClick = {
                    val n = (members.toIntOrNull() ?: 4).coerceIn(1, 30)
                    vm.finishOnboarding(
                        h!!.copy(name = name.trim().ifEmpty { h!!.name }, members = n, defaultServings = n, diet = diet),
                        s!!.copy(monthlyBudgetPaise = Money.fromRupees(budget) ?: 0, weeklyVegetableBudgetPaise = Money.fromRupees(vegBudget) ?: 0),
                        onDone,
                    )
                },
            ) { Text(stringResource(R.string.get_started)) }
        }
    }
}

@Composable
fun SettingsScreen(go: (String) -> Unit, back: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    HbScaffold(title = stringResource(R.string.settings), onBack = back) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
            item { SettingRow(Icons.Default.Home, stringResource(R.string.household_profile), stringResource(R.string.household_profile_sub)) { go(Routes.HOUSEHOLD) } }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Translate, null) },
                    headlineContent = { Text(stringResource(R.string.language)) },
                    supportingContent = { Column { Text(stringResource(R.string.language_sub)); Spacer(Modifier.height(6.dp)); LanguagePicker() } },
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.DarkMode, null) },
                    headlineContent = { Text(stringResource(R.string.theme)) },
                    supportingContent = {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark).forEach { (m, label) ->
                                FilterChip(prefs?.themeMode == m, { vm.setTheme(m) }, label = { Text(stringResource(label)) })
                            }
                        }
                    },
                )
            }
            item { SettingRow(Icons.Default.Notifications, stringResource(R.string.reminders), stringResource(R.string.reminders_sub)) { go(Routes.NOTIFICATIONS) } }
            item { SettingRow(Icons.Default.ListAlt, stringResource(R.string.products), stringResource(R.string.products_sub)) { go(Routes.PRODUCTS) } }
            item { SettingRow(Icons.Default.Category, stringResource(R.string.categories), stringResource(R.string.categories_sub)) { go(Routes.CATEGORIES) } }
            item { SettingRow(Icons.Default.Kitchen, stringResource(R.string.storage_places), stringResource(R.string.storage_places_sub)) { go(Routes.LOCATIONS) } }
            item { SettingRow(Icons.Default.Print, stringResource(R.string.print_center), stringResource(R.string.print_center_sub)) { go(Routes.DOCUMENTS) } }
            item { SettingRow(Icons.Default.Backup, stringResource(R.string.backup_restore), stringResource(R.string.backup_restore_sub)) { go(Routes.BACKUP) } }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Info, null) },
                    headlineContent = { Text(stringResource(R.string.about)) },
                    supportingContent = { Text(stringResource(R.string.about_text, version)) },
                )
            }
        }
    }
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(leadingContent = { Icon(icon, null) }, headlineContent = { Text(title) }, supportingContent = { Text(subtitle) }, modifier = Modifier.clickable(onClick = onClick))
    HorizontalDivider()
}

/** Family profile (Part 24). */
@Composable
fun HouseholdScreen(back: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val h by vm.householdFlow.collectAsStateWithLifecycle()
    val s by vm.settingsFlow.collectAsStateWithLifecycle()
    var loaded by remember { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var members by rememberSaveable { mutableStateOf("") }
    var servings by rememberSaveable { mutableStateOf("") }
    var diet by rememberSaveable { mutableStateOf(Diet.VEG) }
    var shoppingDay by rememberSaveable { mutableStateOf(7) }
    var stores by rememberSaveable { mutableStateOf("") }
    var budget by rememberSaveable { mutableStateOf("") }
    var vegBudget by rememberSaveable { mutableStateOf("") }
    var w1 by rememberSaveable { mutableStateOf("80") }
    var w2 by rememberSaveable { mutableStateOf("90") }
    var w3 by rememberSaveable { mutableStateOf("100") }
    var expiryDays by rememberSaveable { mutableStateOf("2") }
    var roundPacks by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(h, s) {
        val hh = h ?: return@LaunchedEffect
        val ss = s ?: return@LaunchedEffect
        if (loaded) return@LaunchedEffect
        name = hh.name; members = hh.members.toString(); servings = hh.defaultServings.toString(); diet = hh.diet; shoppingDay = hh.shoppingDay; stores = hh.preferredStores
        budget = Money.toEditable(ss.monthlyBudgetPaise); vegBudget = Money.toEditable(ss.weeklyVegetableBudgetPaise)
        w1 = ss.warnPercent1.toString(); w2 = ss.warnPercent2.toString(); w3 = ss.warnPercent3.toString(); expiryDays = ss.expiryWarningDays.toString(); roundPacks = ss.roundToPacks
        loaded = true
    }
    HbScaffold(title = stringResource(R.string.household_profile), onBack = back) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val num = KeyboardOptions(keyboardType = KeyboardType.Number)
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.household_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(members, { members = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.family_members)) }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
                OutlinedTextField(servings, { servings = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.default_servings)) }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Diet.entries.forEach { d -> FilterChip(diet == d, { diet = d }, label = { Text(stringResource(householdDietLabel(d))) }) }
            }
            DropdownField(stringResource(R.string.shopping_day), (1..7).toList(), shoppingDay, { DayOfWeek.of(it).getDisplayName(TextStyle.FULL, Locale.getDefault()) }, { shoppingDay = it })
            OutlinedTextField(stores, { stores = it }, label = { Text(stringResource(R.string.preferred_stores)) }, supportingText = { Text(stringResource(R.string.preferred_stores_hint)) }, modifier = Modifier.fillMaxWidth())
            MoneyInput(budget, { budget = it }, stringResource(R.string.monthly_grocery_budget))
            MoneyInput(vegBudget, { vegBudget = it }, stringResource(R.string.weekly_veg_budget))
            Text(stringResource(R.string.budget_warnings), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(w1 to { v: String -> w1 = v }, w2 to { v: String -> w2 = v }, w3 to { v: String -> w3 = v }).forEach { (value, set) ->
                    OutlinedTextField(value, { set(it.filter(Char::isDigit).take(3)) }, suffix = { Text("%") }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
                }
            }
            OutlinedTextField(expiryDays, { expiryDays = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.expiry_warning_days)) }, singleLine = true, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.round_to_packs), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.round_to_packs_hint), style = MaterialTheme.typography.bodySmall)
                }
                Switch(roundPacks, { roundPacks = it })
            }
            Button(enabled = h != null && s != null && name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(56.dp), onClick = {
                vm.saveHousehold(
                    h!!.copy(name = name.trim(), members = (members.toIntOrNull() ?: 1).coerceAtLeast(1), defaultServings = (servings.toIntOrNull() ?: 1).coerceAtLeast(1),
                        diet = diet, shoppingDay = shoppingDay, preferredStores = stores.split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")),
                    s!!.copy(monthlyBudgetPaise = Money.fromRupees(budget) ?: 0, weeklyVegetableBudgetPaise = Money.fromRupees(vegBudget) ?: 0,
                        warnPercent1 = w1.toIntOrNull() ?: 80, warnPercent2 = w2.toIntOrNull() ?: 90, warnPercent3 = w3.toIntOrNull() ?: 100,
                        expiryWarningDays = (expiryDays.toIntOrNull() ?: 2).coerceIn(0, 30), roundToPacks = roundPacks),
                ) { messenger.show(context.getString(R.string.saved)); back() }
            }) { Text(stringResource(R.string.save)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(back: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<ReminderType?>(null) }
    var denied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        pending?.let { if (ok) vm.setReminder(it, true) else denied = true }
        pending = null
    }
    var editTime by remember { mutableStateOf<Int?>(null) } // 0 morning, 1 evening
    val n = prefs?.notifications
    HbScaffold(title = stringResource(R.string.reminders), onBack = back) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item { Text(stringResource(R.string.reminders_intro), style = MaterialTheme.typography.bodyMedium) }
            if (denied) item { InfoBanner(stringResource(R.string.notifications_denied), Tone.WARNING) }
            item {
                fun fmt(m: Int) = "%02d:%02d".format(m / 60, m % 60)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    OutlinedButton(onClick = { editTime = 0 }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.morning_at, fmt(n?.morningMinutes ?: 390))) }
                    OutlinedButton(onClick = { editTime = 1 }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.evening_at, fmt(n?.eveningMinutes ?: 1140))) }
                }
            }
            items(ReminderType.entries) { type ->
                val on = n?.enabled?.contains(type) == true
                ListItem(
                    headlineContent = { Text(stringResource(reminderLabel(type))) },
                    trailingContent = {
                        Switch(on, { want ->
                            if (want && !vm.canNotify() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                pending = type; permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else vm.setReminder(type, want)
                        })
                    },
                )
                HorizontalDivider()
            }
        }
    }
    editTime?.let { which ->
        val current = if (which == 0) n?.morningMinutes ?: 390 else n?.eveningMinutes ?: 1140
        val state = rememberTimePickerState(initialHour = current / 60, initialMinute = current % 60)
        AlertDialog(
            onDismissRequest = { editTime = null },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    val m = state.hour * 60 + state.minute
                    if (which == 0) vm.setTimes(m, n?.eveningMinutes ?: 1140) else vm.setTimes(n?.morningMinutes ?: 390, m)
                    editTime = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { editTime = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Backup & restore (Part 25) through the Storage Access Framework; no storage permission is requested. */
@Composable
fun BackupScreen(back: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    var exportPassword by rememberSaveable { mutableStateOf("") }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var askPassword by remember { mutableStateOf(false) }
    var check by remember { mutableStateOf<RestoreCheck.Ready?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(uri, exportPassword.ifEmpty { null }) { ok -> messenger.show(context.getString(if (ok) R.string.msg_backup_saved else R.string.err_generic)) }
    }
    fun handle(result: RestoreCheck) {
        when (result) {
            is RestoreCheck.Ready -> check = result
            RestoreCheck.NeedsPassword -> askPassword = true
            RestoreCheck.WrongPassword -> { messenger.show(context.getString(R.string.err_wrong_password)); askPassword = true }
            RestoreCheck.NotABackup -> messenger.show(context.getString(R.string.err_not_backup))
            RestoreCheck.NewerVersion -> messenger.show(context.getString(R.string.err_newer_backup))
            RestoreCheck.Corrupted -> messenger.show(context.getString(R.string.err_corrupt_backup))
        }
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { restoreUri = uri; vm.inspect(uri, null, ::handle) }
    }

    HbScaffold(title = stringResource(R.string.backup_restore), onBack = back) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(stringResource(R.string.create_backup))
            Text(stringResource(R.string.backup_intro), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(exportPassword, { exportPassword = it }, label = { Text(stringResource(R.string.backup_password_optional)) }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), supportingText = { Text(stringResource(R.string.backup_password_hint)) })
            Button(onClick = { exporter.launch("homebasket-backup-${java.time.LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_backup)) }
            HorizontalDivider()
            SectionTitle(stringResource(R.string.restore_backup))
            Text(stringResource(R.string.restore_intro), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { opener.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.choose_backup_file)) }
            HorizontalDivider()
            SectionTitle(stringResource(R.string.delete_all_data))
            Text(stringResource(R.string.delete_all_intro), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Delete, null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.delete_all_data), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (askPassword) {
        var pw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askPassword = false },
            title = { Text(stringResource(R.string.backup_password)) },
            text = { OutlinedTextField(pw, { pw = it }, singleLine = true, visualTransformation = PasswordVisualTransformation()) },
            confirmButton = { TextButton(onClick = { askPassword = false; restoreUri?.let { vm.inspect(it, pw, ::handle) } }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { askPassword = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    check?.let { c ->
        val p = c.preview
        val created = Instant.ofEpochMilli(p.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))
        ConfirmDialog(
            stringResource(R.string.restore_confirm_title),
            stringResource(R.string.restore_confirm_text, created, p.products, p.stockLots, p.purchases, p.recipes, p.meals),
            stringResource(R.string.replace_my_data),
            onConfirm = { vm.restore(c) { ok -> messenger.show(context.getString(if (ok) R.string.msg_restored else R.string.err_generic)) } },
            onDismiss = { check = null }, destructive = true,
        )
    }
    if (confirmClear) {
        var typed by remember { mutableStateOf("") }
        val word = stringResource(R.string.delete_confirm_word)
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.delete_all_data)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.delete_all_confirm, word))
                    OutlinedTextField(typed, { typed = it }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = typed.trim().equals(word, ignoreCase = true), onClick = {
                    confirmClear = false
                    vm.clearAll { messenger.show(context.getString(R.string.msg_all_deleted)) }
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun CategoriesScreen(back: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ProductCategoryEntity?>(null) }
    HbScaffold(title = stringResource(R.string.categories), onBack = back, floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { editing = ProductCategoryEntity(name = "", budgetCategory = BudgetCategory.GROCERY, perishable = false, sortOrder = categories.size) },
            icon = { Icon(Icons.Default.Add, null) }, text = { Text(stringResource(R.string.new_category)) })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)) {
            items(categories, key = { it.id }) { c ->
                ListItem(
                    headlineContent = { Text(c.name) },
                    supportingContent = { Text(stringResource(budgetCategoryLabel(c.budgetCategory)) + if (c.perishable) " • " + stringResource(R.string.perishable) else "") },
                    trailingContent = {
                        IconButton(onClick = { vm.deleteCategory(c) { ok -> messenger.show(context.getString(if (ok) R.string.msg_deleted else R.string.err_category_in_use)) } }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete))
                        }
                    },
                    modifier = Modifier.clickable { editing = c },
                )
                HorizontalDivider()
            }
        }
    }
    editing?.let { c ->
        var name by remember { mutableStateOf(c.name) }
        var budget by remember { mutableStateOf(c.budgetCategory) }
        var perishable by remember { mutableStateOf(c.perishable) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(if (c.id == 0L) R.string.new_category else R.string.edit_category)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.category)) }, singleLine = true)
                    DropdownField(stringResource(R.string.budget_category), BudgetCategory.entries, budget, { stringResource(budgetCategoryLabel(it)) }, { budget = it })
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.perishable), Modifier.weight(1f)); Switch(perishable, { perishable = it }) }
                }
            },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.saveCategory(c.copy(name = name.trim(), budgetCategory = budget, perishable = perishable)); editing = null }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun LocationsScreen(back: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val locations by vm.locations.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<StorageLocationEntity?>(null) }
    HbScaffold(title = stringResource(R.string.storage_places), onBack = back, floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { editing = StorageLocationEntity(name = "", kind = LocationKind.OTHER, sortOrder = locations.size) },
            icon = { Icon(Icons.Default.Add, null) }, text = { Text(stringResource(R.string.new_place)) })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)) {
            item { Text(stringResource(R.string.places_intro), style = MaterialTheme.typography.bodyMedium) }
            items(locations, key = { it.id }) { l ->
                ListItem(
                    headlineContent = { Text(l.name) },
                    supportingContent = { Text(stringResource(locationKindLabel(l.kind))) },
                    trailingContent = {
                        IconButton(onClick = { vm.deleteLocation(l) { ok -> messenger.show(context.getString(if (ok) R.string.msg_deleted else R.string.err_place_in_use)) } }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete))
                        }
                    },
                    modifier = Modifier.clickable { editing = l },
                )
                HorizontalDivider()
            }
        }
    }
    editing?.let { l ->
        var name by remember { mutableStateOf(l.name) }
        var kind by remember { mutableStateOf(l.kind) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(if (l.id == 0L) R.string.new_place else R.string.edit_place)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.place_name)) }, singleLine = true)
                    DropdownField(stringResource(R.string.place_type), LocationKind.entries, kind, { stringResource(locationKindLabel(it)) }, { kind = it })
                }
            },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.saveLocation(l.copy(name = name.trim(), kind = kind)); editing = null }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
