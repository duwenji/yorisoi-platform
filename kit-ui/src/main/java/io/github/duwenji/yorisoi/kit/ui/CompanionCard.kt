package io.github.duwenji.yorisoi.kit.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.duwenji.yorisoi.core.text.localized
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One line on the [CompanionCard]: when it was said, about what ([title], a spot's name), the
 * words, whose words they are ([credit]) and where a researched line's facts came from ([sources]).
 */
data class CardLine(
    val at: Long,
    val text: String,
    val title: String? = null,
    val credit: String? = null,
    val sources: List<CardSource> = emptyList(),
)

/** A source under a researched line (platform AGENT A-31); with a [url], tapping it opens the page. */
data class CardSource(val label: String, val url: String?)

/**
 * The companion's latest words over the map, newest first in [lines], with [status] (the walk's or
 * ride's figures) at the top, or drawn by [header] (a picture of the mood) instead. [onReplay]
 * reads the latest line again; the earlier lines open below it. After a walk or ride, [onDismiss]
 * closes the look back.
 *
 * What only an app has goes in by line index: [above] the words (a studio's label), [media]
 * under them (an image and its credit, in place of [CardLine.credit]), and [actions] at the end
 * (opening a detail, a report; the second argument is whether the earlier lines are open).
 *
 * Not [interactive] (while riding, CycleGuide's requirements F-36), it shows the latest line with its
 * credit and nothing to press: no buttons, links or [actions].
 */
@Composable
fun CompanionCard(
    status: String,
    lines: List<CardLine>,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    waiting: String = localized("動きはじめたら話しかけますね", "I'll talk once you get going", "开始移动后我就和您说话", "움직이기 시작하면 말을 걸게요"),
    onReplay: ((CardLine) -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    dismissLabel: String = localized("振り返りを閉じる", "Close the look back", "关闭回顾", "돌아보기 닫기"),
    interactive: Boolean = true,
    above: @Composable (Int) -> Unit = {},
    media: @Composable (Int) -> Unit = {},
    actions: @Composable (Int, Boolean) -> Unit = { _, _ -> },
) {
    var opened by rememberSaveable { mutableStateOf(false) }
    val expanded = opened && interactive
    val timeFormat = SimpleDateFormat("H:mm", Locale.JAPAN)
    val faint = MaterialTheme.colorScheme.onSurfaceVariant
    val uriHandler = LocalUriHandler.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = OVER_MAP_ALPHA),
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column {
            header?.invoke()
            // Opened, the earlier lines scroll inside the card so the map stays in view.
            Column(
                Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())
                    .padding(16.dp, if (header != null) 4.dp else 12.dp, 16.dp, 0.dp),
            ) {
                if (header == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(status, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        if (onDismiss != null && interactive) {
                            IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = dismissLabel, tint = faint)
                            }
                        }
                    }
                }
                val shown = if (expanded) lines.take(6) else lines.take(1)
                if (shown.isEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(waiting, style = MaterialTheme.typography.bodyMedium)
                }
                shown.forEachIndexed { i, line ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        buildString {
                            append(timeFormat.format(Date(line.at)))
                            line.title?.let { append("　").append(it) }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = faint,
                    )
                    above(i)
                    if (line.text.isNotBlank()) {
                        Text(
                            line.text,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (expanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (interactive) media(i)
                    line.credit?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = faint) }
                    line.sources.forEach { src ->
                        val url = src.url
                        Text(
                            localized("出典: ${src.label}", "Source: ${src.label}", "出处：${src.label}", "출처: ${src.label}"),
                            style = MaterialTheme.typography.labelSmall,
                            color = faint,
                            modifier = if (url != null && interactive) Modifier.clickable { uriHandler.openUri(url) } else Modifier,
                        )
                    }
                    if (interactive) actions(i, expanded)
                }
            }
            val latest = lines.firstOrNull()
            val canReplay = interactive && onReplay != null && latest != null && latest.text.isNotBlank()
            val canOpen = interactive && lines.size > 1
            if (canReplay || canOpen) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (canReplay) {
                        TextButton(onClick = { onReplay!!(latest!!) }) {
                            Icon(Icons.Filled.Replay, contentDescription = null, Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(localized("もう一度聞く", "Hear it again", "再听一次", "다시 듣기"))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (canOpen) {
                        TextButton(onClick = { opened = !opened }) {
                            Text(
                                if (expanded) localized("閉じる", "Close", "关闭", "닫기")
                                else localized("これまでの話", "What was said", "之前的话", "지금까지의 이야기"),
                                color = faint,
                            )
                            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = faint)
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
