<h1 align="center">🧺 HomeBasket</h1>

<p align="center"><b>Plan Smart. Shop Smart. Cook Smart. Save More. — A smart grocery & kitchen manager for Indian households.</b></p>

<p align="center">
<img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin"/> <img src="https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android"/> <img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose"/> <img src="https://img.shields.io/badge/Material%203-757575?style=for-the-badge&logo=materialdesign&logoColor=white" alt="Material 3"/> <img src="https://img.shields.io/badge/Hilt-FF6F00?style=for-the-badge&logo=google&logoColor=white" alt="Hilt"/> <img src="https://img.shields.io/badge/Room%20DB-4CAF50?style=for-the-badge&logo=sqlite&logoColor=white" alt="Room DB"/> <img src="https://img.shields.io/badge/CameraX%20%2B%20ML%20Kit-FF4081?style=for-the-badge&logo=google&logoColor=white" alt="CameraX + ML Kit"/>
</p>

<p align="center"><a href="https://github.com/joker7866/homebasket/releases/latest"><img src="https://img.shields.io/github/v/release/joker7866/homebasket?style=flat-square&label=Download%20APK&color=2ea44f" alt="Download"/></a></p>

## ✨ Highlights

- **Inventory with expiry tracking** — first-expiring-first-out consumption, spoiled stock excluded automatically
- **Meal planner + shortage engine** — works out exactly what to buy for the meals you plan
- **Shopping checklist** with partial-shopping support and no double counting
- **Barcode & bill scanning** (on-device ML Kit) and **voice commands**
- **Budget reports**, PDF/CSV export, encrypted backups
- **English, தமிழ் and हिन्दी** — fully offline, no account, no server

---

A native Android app (Kotlin, Jetpack Compose, Material 3) for Indian households to track what is at home,
plan meals, work out exactly what to buy, shop with a checklist, and see where the money goes. Everything
essential works offline; there is no account, server or Firebase.

## Build and run

Requirements: JDK 17+ (21 tested), Android SDK with platform 37.

```bash
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest      # engine, money, units and voice-parser tests
./gradlew :app:installDebug           # install on a connected device/emulator
```

`minSdk 26`, `targetSdk 36`, `compileSdk 37`. Release builds are minified (`./gradlew assembleRelease`);
add your own signing config before publishing.

## Stack

| Concern | Choice |
|---|---|
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Architecture | MVVM — `core/domain` (pure Kotlin rules), `core/data` (repositories), `feature/*` (screens + ViewModels) |
| Storage | Room (22 tables, exported schema in `app/schemas`), DataStore for preferences |
| DI / async | Hilt, Coroutines + Flow |
| Background | WorkManager (two daily reminder runs, de-duplicated) |
| Documents | `PdfDocument` + `StaticLayout` (shapes Tamil/Devanagari), Android Print Framework, `FileProvider` sharing |
| Scanning | CameraX + ML Kit barcode scanning (on-device) |
| Voice | System `RecognizerIntent` (may use the network — the app says so), plus a typed alternative |
| Localisation | English, Tamil (`values-ta`), Hindi (`values-hi`); per-app language via AppCompat |

Dependency versions are in `gradle/libs.versions.toml` (AGP 9.4.1, Kotlin 2.4.21, Room 2.8.5, Hilt 2.60.1,
Compose BOM 2026.09.00).

## Project layout

```
app/src/main/java/com/homebasket/
  MainActivity.kt, HomeBasketApplication.kt
  navigation/            routes, bottom tabs, NavHost
  core/
    domain/model         units (g/ml/pcs base units), Money (Long paise)
    domain/engine        ShortageEngine, MonthlyPlanner, VoiceCommandParser  (pure, unit-tested)
    database/            entities, DAOs, row types, HomeBasketDatabase
    data/                repositories, seed catalogue & recipes, DataStore settings
    backup/              JSON backup/restore, optional AES-GCM encryption
    pdf/                 PDF renderer, print/share, CSV export
    notifications/       reminder scheduler, worker, boot receiver
    designsystem/        theme (brand colours, dark mode) and shared components
    ui/                  enum → string-resource labels
  feature/
    home, inventory, products, cooking, shopping, reports, scan, settings
```

## How the main rules are enforced

| Rule | Where |
|---|---|
| No automatic monthly reset | Stock lives in `inventory_lot` rows that only change through `InventoryRepository`; nothing is date-triggered. |
| No double counting of purchases | `ShoppingRepository.confirmPurchase` runs in one transaction and only processes items in state `PICKED` with no `purchaseLineId`; each becomes `PURCHASED` with its line id. Calling it twice does nothing. |
| Shopping ≠ inventory | List items are separate rows; ticking sets `PICKED` (provisional). Stock changes only on confirmation. |
| Planning ≠ consumption | `MealRepository.addMeal` touches no stock. `markCooked` consumes exactly the quantities the user confirmed (first-expiring-first-out), once per meal. |
| Transfer ≠ purchase | `InventoryRepository.transfer` writes `TRANSFER_OUT` + `TRANSFER_IN` of equal size and a `stock_transfer` row. |
| Spoiled / expired stock unusable | The `USABLE_LOT` SQL condition excludes `SPOILED`, `DISCARDED` and past-date lots from every calculation. |
| Partial shopping | Only ticked items are recorded; the rest stay on the list, which can be resumed later. |
| Audit trail | Every lot change writes an `inventory_transaction` row (`changeLot`). Purchases are corrected by return/correction lines, never edited. |
| Money precision | All amounts are `Long` paise; formatting uses Indian grouping (₹1,23,456). |
| Estimates labelled | Costs show "≈ … (estimate)"; expiry dates carry a type (estimate / use-by / best-before) and a disclaimer. |

### Shortage engine

`Required Purchase = MAX(0, Required + Desired Reserve − Usable Stock − Confirmed Incoming)`, aggregated per
product across all meals in the window. Meals planned before the window (for example tonight's dinner when
planning tomorrow) reserve stock first. Items on a shopping list are not treated as incoming. Results are
rounded up to the product's pack size (switchable) and every line carries a structured reason that the UI
turns into a sentence such as "Planned meals (Sambar) need 1 kg. You currently have 600 g. Add 500 g…".

### Monthly planner

Requirement comes from, in order: the user's monthly requirement → average usage over 90 days → last
month's purchases → planned meals (planned meals can raise it). Suggested = requirement + minimum stock −
usable stock, rounded to packs. Perishables are excluded unless switched on. No live store prices are used.

## Data model (Room)

`household`, `household_settings`, `product_category`, `product`, `product_variant`, `storage_location`,
`inventory_lot`, `inventory_transaction`, `stock_transfer`, `recipe`, `recipe_ingredient`, `meal_plan`,
`meal_slot`, `meal_completion`, `shopping_list`, `shopping_list_item`, `purchase`, `purchase_line`,
`budget`, `recurring_purchase_rule`, `food_waste`, `prepared_leftover`.

Deliberate differences from the original entity list:
- **IngredientReservation** is computed by the engine on every query rather than stored, so it can never go stale.
- **StockTarget** fields (minimum, refill-to, monthly requirement) live on `product`; **BudgetCategory** is an enum.
- **NotificationPreference** is stored in DataStore, alongside theme and onboarding flags.
- **MealPlan** holds per-day occasion/notes; **MealSlot** holds each planned dish.

Schema changes must add a Room `Migration`; destructive migration is intentionally not enabled.

## Privacy

All data stays on the device. The only optional network use is the phone's own speech-recognition service
(and ML Kit model delivery by Google Play services, depending on the device). Backups go to a file the user
chooses via the Storage Access Framework; no storage permission is requested.

## Known limitations / next steps

- Voice grammar is English-first with common Hindi/Tamil unit, place and meal words; full native-language
  sentence parsing is not implemented.
- Family sharing / cloud sync is not included (the data layer is local-only by design).
- Reminders use WorkManager with an initial delay, so delivery time can drift by a few minutes under Doze.
- No instrumented UI tests yet; the pure business logic is covered by JVM unit tests.
- Seeded category and storage-place names are in English; users can rename them.


---

## 👨‍💻 Author

**Kishore** — Mechanical engineer turned software developer, building practical apps for everyday problems in Tamil Nadu and India.

- GitHub: [@joker7866](https://github.com/joker7866)

<p align="center"><sub>⭐ If you find this project useful, consider giving it a star!</sub></p>
