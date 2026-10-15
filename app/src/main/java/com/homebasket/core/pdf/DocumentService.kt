package com.homebasket.core.pdf

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.homebasket.R
import com.homebasket.core.data.BudgetRepository
import com.homebasket.core.data.CatalogRepository
import com.homebasket.core.data.Clock
import com.homebasket.core.data.InventoryRepository
import com.homebasket.core.data.MealRepository
import com.homebasket.core.data.ReportRepository
import com.homebasket.core.data.ShoppingRepository
import com.homebasket.core.database.dao.TransactionRow
import com.homebasket.core.database.entity.ItemState
import com.homebasket.core.database.entity.LocationKind
import com.homebasket.core.domain.engine.MealSlotKind
import com.homebasket.core.domain.model.Money
import com.homebasket.core.domain.model.Quantities
import com.homebasket.core.ui.budgetCategoryLabel
import com.homebasket.core.ui.conditionLabel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Builds the printable documents (Part 23), then prints or shares them. */
@Singleton
class DocumentService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shopping: ShoppingRepository,
    private val inventory: InventoryRepository,
    private val meals: MealRepository,
    private val reports: ReportRepository,
    private val budget: BudgetRepository,
    private val catalog: CatalogRepository,
    private val clock: Clock,
) {
    private val dateFmt get() = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    suspend fun shoppingList(listId: Long): File = withContext(Dispatchers.IO) {
        val list = requireNotNull(shopping.getList(listId))
        val items = shopping.getItems(listId)
        val estimate = items.sumOf { it.estimatedPricePaise ?: 0L }
        val actual = items.filter { it.state == ItemState.PURCHASED }.sumOf { it.actualPricePaise ?: 0L }
        val sections = items.groupBy { it.categoryName }.toSortedMap().map { (category, rows) ->
            PdfSection(
                category,
                table = PdfTable(
                    headers = listOf("✓", s(R.string.col_item), s(R.string.col_qty), s(R.string.col_est_price), s(R.string.col_actual), s(R.string.col_notes)),
                    weights = listOf(0.6f, 3f, 1.4f, 1.3f, 1.3f, 2f),
                    rows = rows.map {
                        listOf(
                            "", it.name, Quantities.format(it.quantity, it.unitKind, it.countLabel),
                            it.estimatedPricePaise?.let(Money::format) ?: "", it.actualPricePaise?.takeIf { _ -> it.state == ItemState.PURCHASED }?.let(Money::format) ?: "",
                            listOfNotNull(it.store, it.notes).joinToString(" • "),
                        )
                    },
                    checkboxColumn = 0,
                ),
            )
        } + PdfSection(
            s(R.string.pdf_extra_items),
            table = PdfTable(listOf("✓", s(R.string.col_item), s(R.string.col_qty), s(R.string.col_actual)), listOf(0.6f, 4f, 1.6f, 1.6f), emptyList(), 0, blankRows = 6),
        )
        val spec = PdfSpec(
            title = list.name,
            subtitleLines = listOf(
                s(R.string.pdf_date, LocalDate.now().format(dateFmt)) + "    " + s(R.string.pdf_store, list.store ?: "________________"),
                s(R.string.pdf_item_count, items.size),
            ),
            sections = sections,
            footerLines = listOfNotNull(
                s(R.string.pdf_estimated_total, Money.format(estimate)),
                if (actual > 0) s(R.string.pdf_actual_total, Money.format(actual)) else s(R.string.pdf_actual_total, "________"),
            ),
            footnote = s(R.string.pdf_estimate_note),
        )
        write(spec, "shopping_list_$listId")
    }

    /** Home stock register; [kinds] limits it to e.g. refrigerator + freezer. */
    suspend fun stockRegister(kinds: Set<LocationKind>?, title: String): File = withContext(Dispatchers.IO) {
        val today = clock.today().toEpochDay()
        val lots = inventory.lots.first().filter { kinds == null || it.locationKind in kinds }
        val sections = lots.groupBy { it.locationName }.map { (location, rows) ->
            PdfSection(
                location,
                table = PdfTable(
                    headers = listOf(s(R.string.col_item), s(R.string.col_qty), s(R.string.col_expiry), s(R.string.col_condition), s(R.string.col_checked)),
                    weights = listOf(3f, 1.5f, 2f, 1.5f, 1f),
                    rows = rows.map { r ->
                        listOf(
                            r.productName + (r.productLocalName?.let { " ($it)" } ?: ""),
                            Quantities.format(r.lot.quantity, r.unitKind, r.countLabel),
                            r.lot.expiryEpochDay?.let { LocalDate.ofEpochDay(it).format(dateFmt) + if (it < today) " !" else "" } ?: "-",
                            context.getString(conditionLabel(r.lot.condition)),
                            "",
                        )
                    },
                    checkboxColumn = 4,
                    blankRows = 3,
                ),
            )
        }
        val spec = PdfSpec(
            title = title,
            subtitleLines = listOf(s(R.string.pdf_date, LocalDate.now().format(dateFmt)), s(R.string.pdf_item_count, lots.size)),
            sections = sections.ifEmpty { listOf(PdfSection(null, listOf(s(R.string.empty_inventory)))) },
            footnote = s(R.string.pdf_expiry_note),
        )
        write(spec, "stock_register")
    }

    suspend fun weeklyMenu(weekStart: LocalDate): File = withContext(Dispatchers.IO) {
        val start = weekStart.toEpochDay()
        val slots = meals.observeSlots(start, start + 6).first()
        val rows = (0..6).map { offset ->
            val day = weekStart.plusDays(offset.toLong())
            val dayName = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + day.dayOfMonth
            listOf(dayName) + MealSlotKind.entries.map { kind ->
                slots.filter { it.slot.epochDay == day.toEpochDay() && it.slot.slot == kind }
                    .joinToString(", ") { (it.recipeName ?: it.slot.customName ?: "") + " ×${it.slot.servings}" }
            }
        }
        val spec = PdfSpec(
            title = s(R.string.pdf_weekly_menu),
            subtitleLines = listOf(weekStart.format(dateFmt) + " – " + weekStart.plusDays(6).format(dateFmt)),
            sections = listOf(
                PdfSection(
                    null,
                    table = PdfTable(
                        headers = listOf(s(R.string.col_day), s(R.string.slot_breakfast), s(R.string.slot_lunch), s(R.string.slot_snacks), s(R.string.slot_dinner)),
                        weights = listOf(1f, 2f, 2f, 2f, 2f),
                        rows = rows,
                    ),
                ),
            ),
        )
        write(spec, "weekly_menu")
    }

    suspend fun monthlyExpenseReport(month: YearMonth): File = withContext(Dispatchers.IO) {
        val data = reports.build(month.atDay(1), month.atEndOfMonth())
        val overview = budget.current(month)
        val spec = PdfSpec(
            title = s(R.string.pdf_expense_report),
            subtitleLines = listOf(month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.year),
            sections = listOf(
                PdfSection(
                    s(R.string.report_summary),
                    paragraphs = listOf(
                        s(R.string.pdf_total_spent, Money.format(data.totalPaise)),
                        s(R.string.pdf_budget_line, Money.format(overview.monthlyBudgetPaise), Money.format(overview.remainingPaise)),
                        s(R.string.pdf_previous, Money.format(data.previousPeriodPaise)),
                        s(R.string.pdf_waste_line, Money.format(data.wasteCostPaise)),
                        s(R.string.pdf_meals_cooked, data.mealsCooked),
                    ),
                ),
                PdfSection(
                    s(R.string.report_by_category),
                    table = PdfTable(
                        listOf(s(R.string.col_category), s(R.string.col_budget), s(R.string.col_spent)), listOf(3f, 2f, 2f),
                        overview.categories.filter { it.budgetPaise > 0 || it.spentPaise != 0L }.map {
                            listOf(context.getString(budgetCategoryLabel(it.category)), if (it.budgetPaise > 0) Money.format(it.budgetPaise) else "-", Money.format(it.spentPaise))
                        },
                    ),
                ),
                PdfSection(
                    s(R.string.report_purchases),
                    table = PdfTable(
                        listOf(s(R.string.col_date), s(R.string.col_item), s(R.string.col_qty), s(R.string.col_store), s(R.string.col_amount)), listOf(1.5f, 3f, 1.4f, 1.6f, 1.5f),
                        data.lines.map {
                            listOf(LocalDate.ofEpochDay(it.epochDay).format(DateTimeFormatter.ofPattern("dd MMM")), it.line.name,
                                Quantities.format(it.line.quantity, it.line.unitKind, it.line.countLabel), it.store ?: "", Money.format(it.line.amountPaise))
                        },
                    ),
                ),
            ),
            footerLines = listOf(s(R.string.pdf_total_spent, Money.format(data.totalPaise))),
        )
        write(spec, "expense_report_${month.year}_${month.monthValue}")
    }

    private fun write(spec: PdfSpec, baseName: String): File {
        val file = File(File(context.cacheDir, "exports"), "$baseName.pdf")
        PdfRenderer.render(spec, file)
        return file
    }

    fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /** Opens the system print dialog (which also offers "Save as PDF"). Needs an Activity context. */
    fun print(activityContext: Context, file: File, jobName: String) {
        val manager = activityContext.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val attrs = PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME).build()
        manager.print(jobName, FilePrintAdapter(file, jobName), attrs)
    }

    fun share(activityContext: Context, file: File, mime: String = "application/pdf") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uriFor(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            activityContext.startActivity(Intent.createChooser(intent, file.name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: ActivityNotFoundException) {
        }
    }

    // ------------------------------------------------------------------------------------- CSV

    suspend fun writeCsv(uri: Uri, start: LocalDate, end: LocalDate, kind: CsvKind) = withContext(Dispatchers.IO) {
        val rows: List<List<String>> = when (kind) {
            CsvKind.PURCHASES -> listOf(listOf("date", "store", "item", "quantity", "unit", "amount_inr", "budget_category", "is_return")) +
                reports.build(start, end).lines.map {
                    val unit = if (it.line.unitKind == com.homebasket.core.domain.model.UnitKind.COUNT) it.line.countLabel else baseUnit(it.line.unitKind)
                    listOf(LocalDate.ofEpochDay(it.epochDay).toString(), it.store.orEmpty(), it.line.name, Quantities.formatNumber(it.line.quantity), unit,
                        Money.toEditable(it.line.amountPaise), it.line.budgetCategory.name, (it.line.returnOfLineId != null).toString())
                }
            CsvKind.STOCK_HISTORY -> listOf(listOf("date_time", "product", "type", "quantity_change", "unit", "location", "note")) +
                reports.transactionsBetween(start.toEpochDay(), end.toEpochDay()).map { t: TransactionRow ->
                    val unit = if (t.unitKind == com.homebasket.core.domain.model.UnitKind.COUNT) t.countLabel else baseUnit(t.unitKind)
                    listOf(java.time.Instant.ofEpochMilli(t.tx.timestamp).toString(), t.productName, t.tx.type.name, Quantities.formatNumber(t.tx.quantityDelta), unit, t.locationName.orEmpty(), t.tx.note.orEmpty())
                }
            CsvKind.WASTE -> listOf(listOf("date", "item", "quantity", "unit", "reason", "estimated_cost_inr")) +
                reports.wasteBetween(start.toEpochDay(), end.toEpochDay()).map {
                    val unit = if (it.unitKind == com.homebasket.core.domain.model.UnitKind.COUNT) it.countLabel else baseUnit(it.unitKind)
                    listOf(LocalDate.ofEpochDay(it.epochDay).toString(), it.name, Quantities.formatNumber(it.quantity), unit, it.reason.name, Money.toEditable(it.estimatedCostPaise))
                }
        }
        val text = "﻿" + rows.joinToString("\r\n") { r -> r.joinToString(",") { csvCell(it) } }
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    private fun baseUnit(kind: com.homebasket.core.domain.model.UnitKind) = when (kind) {
        com.homebasket.core.domain.model.UnitKind.WEIGHT -> "g"
        com.homebasket.core.domain.model.UnitKind.VOLUME -> "ml"
        com.homebasket.core.domain.model.UnitKind.COUNT -> "pcs"
    }

    private fun csvCell(v: String): String {
        // Guard against spreadsheet formula injection and quote when needed.
        val safe = if (v.isNotEmpty() && v[0] in "=+-@" && v.toDoubleOrNull() == null) "'$v" else v
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}

enum class CsvKind { PURCHASES, STOCK_HISTORY, WASTE }

private class FilePrintAdapter(private val file: File, private val name: String) : PrintDocumentAdapter() {
    override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
        if (cancel?.isCanceled == true) { callback.onLayoutCancelled(); return }
        callback.onLayoutFinished(PrintDocumentInfo.Builder("$name.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
    }

    override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancel: CancellationSignal?, callback: WriteResultCallback) {
        try {
            FileInputStream(file).use { input -> FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        }
    }
}
