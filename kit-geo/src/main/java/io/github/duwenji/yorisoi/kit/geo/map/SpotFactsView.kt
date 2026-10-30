package io.github.duwenji.yorisoi.kit.geo.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.duwenji.yorisoi.kit.geo.SpotFacts

/**
 * A spot's [SpotFacts] as lines, label beside value, then its links. A long value (an
 * inscription) shows its first lines until pressed. Nothing when there's nothing to show.
 */
@Composable
fun SpotFactsView(facts: SpotFacts, modifier: Modifier = Modifier) {
    if (facts.details.isEmpty() && facts.links.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        facts.details.forEach { fact ->
            Row {
                Text(
                    fact.label, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(LABEL_WIDTH),
                )
                var open by remember(fact) { mutableStateOf(false) }
                Text(
                    fact.value, style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (open) Int.MAX_VALUE else LONG_LINES, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { open = !open },
                )
            }
        }
        facts.links.forEach { link ->
            Text(
                link.label,
                style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { uriHandler.openUri(link.url) },
            )
        }
    }
}

/** [SpotFacts.summary] in one line, e.g. 「仏教（臨済宗）・鎌倉時代・国宝」; null when there's none. */
fun SpotFacts.summaryLine(): String? = summary.takeIf { it.isNotEmpty() }?.joinToString("・")

private val LABEL_WIDTH = 96.dp
private const val LONG_LINES = 3
