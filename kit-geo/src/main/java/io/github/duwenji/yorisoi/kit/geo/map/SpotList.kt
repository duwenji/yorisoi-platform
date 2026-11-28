package io.github.duwenji.yorisoi.kit.geo.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.duwenji.yorisoi.core.text.localized
import io.github.duwenji.yorisoi.kit.geo.Poi

/** The height of a [SpotRow], for a sheet that peeks at its first rows. */
val SPOT_ROW_HEIGHT = 64.dp

/** A nearby spot in a list: its group's dot, name and kind, how often it was visited, and how far. */
@Composable
fun SpotRow(item: SpotItem, onClick: () -> Unit) {
    val color = SpotGroup.of(item.poi.category).color
    Row(
        Modifier.fillMaxWidth().heightIn(min = SPOT_ROW_HEIGHT).clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The map marker's color, so a row and its dot can be matched.
        Box(Modifier.size(22.dp).background(color.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(12.dp).background(color, CircleShape))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.poi.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.poi.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (item.visitCount > 0) {
            Icon(
                Icons.Filled.CheckCircle, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                localized("${item.visitCount}回", "${item.visitCount}×", "${item.visitCount}次", "${item.visitCount}회"),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
        }
        item.distanceM?.let { Text(spotDistance(item.poi, it), style = MaterialTheme.typography.labelLarge) }
    }
}

/** How far a spot is: 「敷地内」 when the user is inside its area, else [formatMeters]. */
fun spotDistance(poi: Poi, m: Int): String =
    if (poi.isInside(m)) localized("敷地内", "On the grounds", "在场地内", "부지 안") else formatMeters(m)

/** "すべて" and each [SpotGroup] there are [spots] of, with how many; [onGroup] narrows the list and the map. */
@Composable
fun GroupFilter(spots: List<SpotItem>, group: SpotGroup?, onGroup: (SpotGroup?) -> Unit) {
    val counts = spots.groupingBy { SpotGroup.of(it.poi.category) }.eachCount()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = group == null, onClick = { onGroup(null) }, label = { Text(localized("すべて", "All", "全部", "전체")) })
        SpotGroup.entries.filter { it in counts }.forEach { g ->
            FilterChip(
                selected = group == g,
                onClick = { onGroup(if (group == g) null else g) },
                label = { Text("${g.label} ${counts.getValue(g)}") },
                leadingIcon = { Box(Modifier.size(8.dp).background(g.color, CircleShape)) },
            )
        }
    }
}
