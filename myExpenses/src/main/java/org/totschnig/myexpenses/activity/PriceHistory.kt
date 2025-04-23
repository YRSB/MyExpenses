package org.totschnig.myexpenses.activity

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.compose.AmountEdit
import org.totschnig.myexpenses.compose.AppTheme
import org.totschnig.myexpenses.compose.CharIcon
import org.totschnig.myexpenses.compose.LocalDateFormatter
import org.totschnig.myexpenses.compose.scrollbar.LazyColumnWithScrollbar
import org.totschnig.myexpenses.databinding.ActivityComposeBinding
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.retrofit.ExchangeRateApi
import org.totschnig.myexpenses.retrofit.ExchangeRateSource
import org.totschnig.myexpenses.util.checkMenuIcon
import org.totschnig.myexpenses.util.safeMessage
import org.totschnig.myexpenses.viewmodel.PriceHistoryViewModel
import org.totschnig.myexpenses.viewmodel.data.Price
import org.totschnig.myexpenses.viewmodel.transformForUser
import java.math.BigDecimal
import java.math.MathContext
import java.security.SecureRandom
import java.text.DecimalFormat
import java.time.LocalDate

class PriceHistory : ProtectedFragmentActivity() {

    val viewModel: PriceHistoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        injector.inject(viewModel)
        val binding = ActivityComposeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val commodity = viewModel.commodity
        title = currencyContext[commodity].description
        setupToolbar()
        binding.composeView.setContent {
            AppTheme {
                PriceListScreen(
                    viewModel.pricesWithMissingDates.collectAsState(initial = mapOf(LocalDate.now() to null)).value,
                    currencyContext.homeCurrencyUnit,
                    Modifier.padding(horizontal = 8.dp),
                    onDelete = {
                        viewModel.deletePrice(it)
                    },
                    onSave = { date, value ->
                        viewModel.savePrice(date, value)
                    },
                    onDownload = { date ->
                        viewModel.effectiveSource?.also {
                            lifecycleScope.launch {
                                val homeCurrencyString = currencyContext.homeCurrencyString
                                try {

                                    viewModel.loadFromNetwork(
                                        it,
                                        date,
                                        commodity,
                                        homeCurrencyString
                                    ).also {
                                        if (it.first != date) {
                                            showSnackBar(it.first.toString())
                                        }
                                    }
                                } catch (e: Exception) {
                                    showSnackBar(
                                        e.transformForUser(
                                            this@PriceHistory,
                                            commodity,
                                            homeCurrencyString
                                        ).safeMessage
                                    )
                                }
                            }
                        }
                            ?: run {
                                showSnackBar(
                                    getString(
                                        R.string.exchange_rate_not_supported,
                                        currencyContext.homeCurrencyString,
                                        viewModel.commodity
                                    )
                                )
                            }
                    },
                    inverseRate = viewModel.inverseRate.collectAsState(false).value
                )
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menu.add(Menu.NONE, R.id.INVERT_COMMAND, 1, R.string.menu_invert_transfer).apply {
            setIcon(R.drawable.ic_menu_move)
            setShowAsAction(SHOW_AS_ACTION_IF_ROOM)
        }
        val relevantSources = viewModel.relevantSources
        if (relevantSources.size > 1) {
            menu.addSubMenu(Menu.NONE, R.id.SELECT_SOURCE_MENU_ID, 1, getString(R.string.source))
                .apply {
                    item.setShowAsAction(SHOW_AS_ACTION_IF_ROOM)
                    relevantSources.forEach { source ->
                        add(1, source.id, Menu.NONE, source.name)
                    }
                    setGroupCheckable(1, true, true)
                }
        }
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        viewModel.userSelectedSource?.let {
            menu.findItem(R.id.SELECT_SOURCE_MENU_ID).subMenu?.findItem(it.id)?.setChecked(true)
        }
        lifecycleScope.launch {
            menu.findItem(R.id.INVERT_COMMAND)?.let {
                val first = viewModel.inverseRate.first()
                it.isChecked = first
                checkMenuIcon(it)
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = super.onOptionsItemSelected(item) ||
            if (item.itemId == R.id.INVERT_COMMAND) {
                lifecycleScope.launch {
                    viewModel.persistInverseRate(!item.isChecked)
                    invalidateOptionsMenu()
                }
                true
            } else ExchangeRateApi.getById(item.itemId)?.also {
                viewModel.userSelectedSource = it
                invalidateOptionsMenu()
            } != null
}

@Composable
fun PriceListScreen(
    prices: Map<LocalDate, Price?>,
    homeCurrency: CurrencyUnit,
    modifier: Modifier = Modifier,
    onDelete: (Price) -> Unit,
    onSave: (LocalDate, BigDecimal) -> Unit,
    onDownload: (LocalDate) -> Unit,
    inverseRate: Boolean = false,
) {
    val column1Weight = .4f
    val column2Weight = .4f
    val column3Weight = .2f
    val format = remember { DecimalFormat("#.################") }
    val editedDate = rememberSaveable { mutableStateOf<LocalDate?>(null) }
    Column(modifier = modifier) {
        Row {
            TableCell(stringResource(R.string.date), column1Weight, true)
            TableCell(
                "${stringResource(R.string.value)} (${homeCurrency.symbol})",
                column2Weight,
                true
            )
            TableCell(stringResource(R.string.source), column3Weight, true)
            Spacer(Modifier.width(96.dp))
        }
        HorizontalDivider()
        LazyColumnWithScrollbar(itemsAvailable = prices.size, fastScroll = true) {
            items(
                items = prices.entries.toList(),
                key = { it.key }
            ) { (date, price) ->
                val rate = price?.value?.let {
                    if (inverseRate) it.reciprocal else it
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TableCell(LocalDateFormatter.current.format(date), column1Weight)
                    if (editedDate.value == date) {

                        var valueForEdit by rememberSaveable { mutableStateOf(rate) }

                        val isValid = remember {
                            derivedStateOf {
                                valueForEdit?.let {
                                    it > BigDecimal.ZERO
                                }
                            }
                        }
                        AmountEdit(
                            value = valueForEdit,
                            onValueChange = {
                                valueForEdit = it
                            },
                            fractionDigits = 16,
                            modifier = Modifier
                                .requiredWidthIn(160.dp)
                                .weight(column2Weight)
                                .zIndex(1f),
                            keyboardActions = KeyboardActions(onDone = {
                                if (isValid.value == true) {
                                    valueForEdit?.let {
                                        onSave(date, (if (inverseRate) it.reciprocal else it))
                                    }
                                }
                                editedDate.value = null
                            }),
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(android.R.string.cancel),
                                    modifier = Modifier.clickable {
                                        editedDate.value = null
                                    })
                            },
                            isError = isValid.value == false,
                            allowNegative = false
                        )
                    } else {
                        if (rate != null) {
                            TableCell(
                                format.format(rate.toDouble()),
                                column2Weight
                            )
                        } else {
                            Spacer(Modifier.weight(column2Weight))
                        }
                    }
                    Box(Modifier.weight(column3Weight), contentAlignment = Alignment.Center) {
                        if (price != null) {
                            when (price.source) {
                                is ExchangeRateApi -> {
                                    CharIcon(price.source.name.first(), size = 12.sp)
                                }

                                ExchangeRateSource.User -> org.totschnig.myexpenses.compose.Icon(
                                    "user",
                                    size = 12.sp
                                )

                                ExchangeRateSource.Calculation -> Icon(
                                    imageVector = Icons.Default.Calculate,
                                    contentDescription = null
                                )
                            }
                        }
                    }
                    Row(Modifier.width(96.dp), horizontalArrangement = Arrangement.Center) {
                        IconButton(onClick = { editedDate.value = date }) {
                            Icon(
                                imageVector = if (price?.source == ExchangeRateSource.User) Icons.Default.Edit else Icons.Default.Add,
                                contentDescription = stringResource(R.string.menu_edit)
                            )
                        }
                        if (price == null) {
                            IconButton(onClick = {
                                if (editedDate.value == date) {
                                    editedDate.value = null
                                }
                                onDownload(date)
                            }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = stringResource(R.string.action_download)
                                )
                            }
                        } else {
                            IconButton(onClick = { onDelete(price) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.menu_delete)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun RowScope.TableCell(
    text: String,
    weight: Float,
    isHeader: Boolean = false,
) {
    Text(
        text = text,
        modifier = Modifier
            .weight(weight)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal
    )
}

val BigDecimal.reciprocal: BigDecimal
    get() = BigDecimal.ONE.divide(this, MathContext.DECIMAL64)

@Preview
@Composable
fun HistoricPricesPreview() {
    val random = remember { SecureRandom() }
    PriceListScreen(
        buildMap {
            repeat(250) {
                val date = LocalDate.now().minusDays(it.toLong())
                put(date, Price(date, ExchangeRateApi.Frankfurter, BigDecimal.valueOf(random.nextDouble())))
            }
        },
        homeCurrency = CurrencyUnit.DebugInstance,
        onDelete = {},
        onSave = { _, _ -> },
        onDownload = {}
    )
}
