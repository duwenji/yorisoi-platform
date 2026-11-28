package io.github.duwenji.yorisoi.kit.geo.map

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalConvenienceStore
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Roofing
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.duwenji.yorisoi.core.text.localized
import io.github.duwenji.yorisoi.kit.geo.FacilityKind
import io.github.duwenji.yorisoi.kit.ui.OVER_MAP_ALPHA

/**
 * The nearest facility of each kind, over the map; the chips also serve as the map's legend.
 * [onClick] (showing the way there) is null while it may not be pressed, as while riding.
 */
@Composable
fun FacilityBar(items: List<FacilityItem>, onClick: ((FacilityItem) -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            val kind = item.facility.kind
            AssistChip(
                onClick = { onClick?.invoke(item) },
                enabled = onClick != null,
                label = { Text(listOfNotNull(kind.label.replaceFirstChar { it.titlecase() }, item.distanceM?.let(::formatMeters)).joinToString(" ")) },
                leadingIcon = {
                    // The map marker's rounded square, so the chips also serve as the map legend.
                    Box(Modifier.size(20.dp).background(facilityColor(kind), RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                        Icon(facilityIcon(kind), contentDescription = null, Modifier.size(12.dp), tint = Color.White)
                    }
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = OVER_MAP_ALPHA),
                    // Not greyed out while it can't be pressed: it is still to be read.
                    disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = OVER_MAP_ALPHA),
                    disabledLabelColor = MaterialTheme.colorScheme.onSurface,
                ),
                elevation = AssistChipDefaults.assistChipElevation(elevation = 2.dp, disabledElevation = 2.dp),
                border = null,
            )
        }
    }
}

/** The icon of each facility kind, drawn on its [facilityColor]. */
fun facilityIcon(kind: FacilityKind): ImageVector = when (kind) {
    FacilityKind.TOILETS -> Icons.Filled.Wc
    FacilityKind.DRINKING_WATER -> Icons.Filled.WaterDrop
    FacilityKind.VENDING_MACHINE -> Icons.Filled.LocalDrink
    FacilityKind.SHELTER -> Icons.Filled.Roofing
    FacilityKind.BENCH -> Icons.Filled.Chair
    FacilityKind.BICYCLE_PARKING, FacilityKind.BICYCLE_SHOP, FacilityKind.BICYCLE_PUMP -> Icons.Filled.PedalBike
    FacilityKind.CONVENIENCE_STORE -> Icons.Filled.LocalConvenienceStore
}

/**
 * Which spot or facility the line on the map leads to and how it was found, with a way to take it
 * away; [onClear] is null while it may not be pressed, as while riding.
 */
@Composable
fun RouteLabel(route: RouteToSpot, onClear: (() -> Unit)?, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = OVER_MAP_ALPHA),
        shadowElevation = 2.dp,
        modifier = modifier.padding(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // Without the button, as tall as with it.
            modifier = Modifier.padding(start = 12.dp, end = if (onClear == null) 12.dp else 0.dp, top = if (onClear == null) 8.dp else 0.dp, bottom = if (onClear == null) 8.dp else 0.dp),
        ) {
            Column(Modifier.weight(1f, fill = false)) {
                Text(
                    localized("${route.target.name}へ", "To ${route.target.name}", "前往${route.target.name}", "${route.target.name}(으)로"),
                    style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        route.loading -> localized("ルートを検索中…", "Finding a route…", "正在搜索路线…", "경로 검색 중…")
                        route.route.onPaths -> localized("道沿いのルート", "Route along the roads", "沿道路的路线", "도로를 따른 경로")
                        else -> localized("方向のみ（ルートを取得できませんでした）", "Direction only (no route could be found)", "仅显示方向（无法获取路线）", "방향만(경로를 가져올 수 없었습니다)")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onClear != null) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Close, contentDescription = localized("ルートを消す", "Clear the route", "清除路线", "경로 지우기"))
                }
            }
        }
    }
}

/** A distance as the map's bars write it: 「80m」「1.2km」. */
fun formatMeters(m: Int): String = if (m < 1000) "${m}m" else "%.1fkm".format(m / 1000f)
