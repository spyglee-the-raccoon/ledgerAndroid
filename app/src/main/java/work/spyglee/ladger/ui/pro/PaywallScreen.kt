package work.spyglee.ladger.ui.pro

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Period
import kotlinx.coroutines.launch
import work.spyglee.ladger.R
import work.spyglee.ladger.billing.Plans
import work.spyglee.ladger.billing.ProEvent
import work.spyglee.ladger.billing.ProPlan
import work.spyglee.ladger.data.CategoryColor
import work.spyglee.ladger.ui.LocalAppContainer
import work.spyglee.ladger.ui.theme.toColor

private const val TERMS_URL = "https://spyglee.work/ledger/terms/"
private const val PRIVACY_URL = "https://spyglee.work/ledger/privacy/"

/**
 * Ledger Pro's paywall, like iOS's `PaywallView` (Apple's ready-made subscription store, which Android
 * doesn't have): the chart icon, the title and text from the iOS catalog, both plans with Google Play's
 * prices and trial, restore, and the terms and privacy links. Google Play shows its own purchase sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(onClose: () -> Unit) {
    val container = LocalAppContainer.current
    val subscriptions = container.subscriptions
    val plans by subscriptions.plans.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var selectedProduct by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { subscriptions.loadPlans() }
    LaunchedEffect(Unit) {
        subscriptions.events.collect { event ->
            val message = when (event) {
                ProEvent.Unlocked -> null
                ProEvent.Pending -> R.string.pro_pending
                ProEvent.Failed -> R.string.pro_failed
                ProEvent.NothingToRestore -> R.string.pro_restore_none
                ProEvent.Unavailable -> R.string.pro_unavailable
            }
            if (message != null) {
                // A new message replaces the one showing instead of waiting behind it.
                snackbar.currentSnackbarData?.dismiss()
                launch { snackbar.showSnackbar(context.getString(message)) }
            }
        }
    }
    // Google Play's sheet and the web pages are the app's own trips: no re-lock on return (D5).
    val openPage: (String) -> Unit = { url ->
        container.appLock.expectReturn()
        uriHandler.openUri(url)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.TrendingUp,
                contentDescription = null,
                tint = CategoryColor.PURPLE.toColor(),
                modifier = Modifier.size(56.dp),
            )
            Text(
                stringResource(R.string.paywall_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.paywall_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))

            when (val state = plans) {
                Plans.Loading -> CircularProgressIndicator(Modifier.padding(24.dp))
                Plans.Unavailable -> {
                    Text(stringResource(R.string.pro_unavailable), textAlign = TextAlign.Center)
                    OutlinedButton(onClick = { scope.launch { subscriptions.loadPlans() } }) {
                        Text(stringResource(R.string.pro_try_again))
                    }
                }
                is Plans.Ready -> {
                    val selected = state.plans.firstOrNull { it.productId == selectedProduct } ?: state.plans.first()
                    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.plans.forEach { plan ->
                            PlanOption(plan, isSelected = plan == selected, onSelect = { selectedProduct = plan.productId })
                        }
                    }
                    Button(
                        onClick = {
                            if (activity != null) {
                                container.appLock.expectReturn()
                                scope.launch { subscriptions.buy(activity, selected) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.pro_subscribe))
                    }
                    Text(
                        stringResource(R.string.pro_renewal_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            TextButton(onClick = { scope.launch { subscriptions.restore() } }) { Text(stringResource(R.string.pro_restore)) }
            Row(horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { openPage(TERMS_URL) }) { Text(stringResource(R.string.pro_terms)) }
                TextButton(onClick = { openPage(PRIVACY_URL) }) { Text(stringResource(R.string.pro_privacy)) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PlanOption(plan: ProPlan, isSelected: Boolean, onSelect: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect),
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = isSelected, onClick = null)
            Column(Modifier.padding(start = 12.dp)) {
                val (name, price) = when (plan.period) {
                    ProPlan.Period.MONTH -> R.string.monthly to R.string.pro_price_per_month
                    ProPlan.Period.YEAR -> R.string.yearly to R.string.pro_price_per_year
                }
                Text(stringResource(name), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(price, plan.price), style = MaterialTheme.typography.bodyMedium)
                plan.freeTrial?.let {
                    Text(trialText(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun trialText(trial: Period): String {
    val months = trial.toTotalMonths().toInt()
    return when {
        months > 0 -> pluralStringResource(R.plurals.pro_free_trial_months, months, months)
        trial.days % 7 == 0 -> pluralStringResource(R.plurals.pro_free_trial_weeks, trial.days / 7, trial.days / 7)
        else -> pluralStringResource(R.plurals.pro_free_trial_days, trial.days, trial.days)
    }
}

/** The statistics screens need Ledger Pro, as on iOS; without it the paywall takes their place. */
@Composable
fun ProGate(onClose: () -> Unit, content: @Composable () -> Unit) {
    val isPro by LocalAppContainer.current.subscriptions.isPro.collectAsStateWithLifecycle()
    if (isPro) content() else PaywallScreen(onClose)
}
