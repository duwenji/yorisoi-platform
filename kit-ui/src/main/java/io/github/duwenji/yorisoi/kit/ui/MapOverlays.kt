package io.github.duwenji.yorisoi.kit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.duwenji.yorisoi.core.text.localized

/** How opaque the cards, bars and pills over the map are: the map shows through a little. */
const val OVER_MAP_ALPHA = 0.85f

/**
 * The most pressing notice over the map, with how many [more] there are. [onClick] opens it or
 * the list of all; null when there is nothing to open.
 */
@Composable
fun NoticePill(text: String, more: Int, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = OVER_MAP_ALPHA),
        shadowElevation = 2.dp,
        modifier = modifier.then(if (onClick != null) Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick) else Modifier),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (more > 0) {
                Spacer(Modifier.width(8.dp))
                Text(localized("ほか $more 件", "$more more", "另有 $more 条", "외 ${more}건"), style = MaterialTheme.typography.labelMedium)
            }
            if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, Modifier.size(18.dp))
        }
    }
}

/** The height of [SheetHandle], for a sheet's peek. */
val SHEET_HANDLE_HEIGHT = 20.dp

/** The bar at the top of a bottom sheet that shows it can be pulled up. */
@Composable
fun SheetHandle() {
    Box(Modifier.fillMaxWidth().height(SHEET_HANDLE_HEIGHT), contentAlignment = Alignment.Center) {
        Box(Modifier.size(32.dp, 4.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)))
    }
}
