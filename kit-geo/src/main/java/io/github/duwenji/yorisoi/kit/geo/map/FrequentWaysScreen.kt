package io.github.duwenji.yorisoi.kit.geo.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import io.github.duwenji.yorisoi.kit.ui.ScreenBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import io.github.duwenji.yorisoi.kit.geo.FrequentWay
import io.github.duwenji.yorisoi.kit.geo.FrequentWayFinder
import io.github.duwenji.yorisoi.kit.geo.LatLon
import io.github.duwenji.yorisoi.kit.geo.Poi
import io.github.duwenji.yorisoi.kit.geo.WalkRoute
import io.github.duwenji.yorisoi.kit.geo.WayOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/** What was said about a spot along a way, and when. */
data class WayTalk(val name: String, val at: Long, val text: String)

/** The words for going along a way in each app: a walker's and a rider's. */
data class WayWords(
    /** "同じ道を 3 回歩くと". */
    val threeTimes: String,
    /** "60 日歩かないと". */
    val unused: String,
    /** "散歩", "走行". */
    val trip: String,
) {
    companion object {
        val WALK = WayWords("同じ道を 3 回歩くと", "60 日歩かないと", "散歩")
        val RIDE = WayWords("同じ道を 3 回走ると", "60 日走らないと", "走行")
    }
}

/** What the screen of the frequent ways does to them (`FrequentWayStore`). */
class WayActions(
    val rename: (String, String) -> Unit,
    val save: (String) -> Unit,
    val delete: (String) -> Unit,
    /** What was said along a way on the trips kept (REQ-FREQUENT-WAYS F-13); called off the main thread. */
    val talksOn: (FrequentWay) -> List<WayTalk>,
)

/**
 * The frequent ways (REQ-FREQUENT-WAYS F-13〜F-15): the list, and one way with its map, what was
 * said along it, and its name to change, to keep or to delete. The app opens it only outside a
 * walk or a ride (F-18).
 */
@Composable
fun FrequentWaysScreen(ways: List<FrequentWay>, actions: WayActions, words: WayWords, onClose: () -> Unit) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    ways.find { it.id == open }?.let { way ->
        BackHandler { open = null }
        WayDetail(way, actions, words, onClose = { open = null })
        return
    }
    Scaffold(topBar = { ScreenBar("よく通る道", onBack = onClose) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            item {
                if (ways.isEmpty()) {
                    Text(
                        "まだありません。${words.threeTimes}、よく通る道になります。${words.trip}の履歴から作ることもできます。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(ways, key = { it.id }) { way ->
                Column(Modifier.fillMaxWidth().clickable { open = way.id }.padding(vertical = 12.dp)) {
                    Text(way.name, style = MaterialTheme.typography.bodyLarge)
                    Text(summary(way, words), style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
            }
        }
    }
}

/** "1.6 km・12 回・最後は10月28日・自動で見つけた（保存済み）". */
private fun summary(way: FrequentWay, words: WayWords): String = listOf(
    "%.1f km".format(Locale.JAPAN, way.lengthM / 1000),
    "${way.passes} 回",
    "最後は${day.format(way.lastPassedAt)}",
    when {
        way.origin == WayOrigin.USER -> "自分で作った"
        way.saved -> "自動で見つけた（保存済み）"
        else -> "自動で見つけた（${words.unused}消えます）"
    },
).joinToString("・")

@Composable
private fun WayDetail(way: FrequentWay, actions: WayActions, words: WayWords, onClose: () -> Unit) {
    val talks by produceState(emptyList<WayTalk>(), way.id) { value = withContext(Dispatchers.IO) { actions.talksOn(way) } }
    var name by remember(way.id) { mutableStateOf(way.name) }
    var deleting by remember { mutableStateOf(false) }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("「${way.name}」を消しますか") },
            text = {
                if (way.origin == WayOrigin.FOUND) Text("消した道は、また何度通っても、自動ではよく通る道になりません。")
            },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    actions.delete(way.id)
                    onClose()
                }) { Text("消す") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("やめる") } },
        )
    }
    Scaffold(topBar = { ScreenBar(way.name, onBack = onClose) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text(summary(way, words), style = MaterialTheme.typography.bodyMedium) }
            item { WayMap(way.points, highlight = null, Modifier.fillMaxWidth().height(320.dp)) }
            item {
                OutlinedTextField(name, { name = it }, label = { Text("名前") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { actions.rename(way.id, name.trim()) }, enabled = name.isNotBlank() && name.trim() != way.name) {
                        Text("名前を変える")
                    }
                    if (!way.saved) OutlinedButton(onClick = { actions.save(way.id) }) { Text("保存する") }
                    TextButton(onClick = { deleting = true }) { Text("消す") }
                }
            }
            if (talks.isNotEmpty()) item { Text("この道で話した見どころ", style = MaterialTheme.typography.titleMedium) }
            items(talks) { talk ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text("${day.format(talk.at)}　${talk.name}", style = MaterialTheme.typography.titleSmall)
                    Text(talk.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/**
 * Makes a frequent way from a walk or a ride gone along [route] (REQ-FREQUENT-WAYS F-16): where it
 * starts and ends, [END_M] in from both ends at first (D-5), and its name.
 */
@Composable
fun NewWayFromTrip(route: List<LatLon>, onSave: (String, List<LatLon>) -> Unit, onCancel: () -> Unit) {
    val along = remember(route) { FrequentWayFinder.distances(route) }
    val total = along.lastOrNull() ?: 0.0
    var range by remember(route) {
        mutableStateOf(END_M.coerceAtMost(total / 2).toFloat()..(total - END_M).coerceAtLeast(total / 2).toFloat())
    }
    var name by remember { mutableStateOf("") }
    val section = remember(route, range) { FrequentWayFinder.section(route, range.start.toDouble(), range.endInclusive.toDouble()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("よく通る道にする", style = MaterialTheme.typography.titleMedium)
        WayMap(route, highlight = section, Modifier.fillMaxWidth().height(320.dp))
        Text(
            "始まりと終わり: ${"%.1f".format(Locale.JAPAN, range.start / 1000)}〜${"%.1f".format(Locale.JAPAN, range.endInclusive / 1000)} km（太い線）",
            style = MaterialTheme.typography.bodySmall,
        )
        RangeSlider(value = range, onValueChange = { range = it }, valueRange = 0f..total.toFloat().coerceAtLeast(1f))
        OutlinedTextField(name, { name = it }, label = { Text("名前（例: 通勤の道）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(name.trim(), section) }, enabled = name.isNotBlank() && section.size >= 2) { Text("保存する") }
            TextButton(onClick = onCancel) { Text("やめる") }
        }
    }
}

/** [points] fitted in the map; [highlight] drawn over them as a thick line (the part chosen). */
@Composable
private fun WayMap(points: List<LatLon>, highlight: List<LatLon>?, modifier: Modifier) {
    val shown = highlight ?: points
    SpotMap(
        tiles = MapTiles.Gsi(MapStyle.GSI_PALE),
        location = null,
        heading = null,
        spots = emptyList(),
        facilities = emptyList(),
        walked = points,
        // The way to a spot is drawn thick over the way gone: here, the part of the way chosen.
        route = shown.takeIf { it.size >= 2 }?.let { RouteToSpot(WAY_END, WalkRoute(it, 0.0, onPaths = true), it.first()) },
        focus = null,
        onSpotClick = {},
        onViewportChanged = { _, _, _, _, _ -> },
        modifier = modifier.clipToBounds(),
        fitWalked = true,
    )
}

/** Left out at both ends of a trip made into a way, as when ways are found (REQ-FREQUENT-WAYS D-5). */
private const val END_M = 200.0

/** No spot: the end of the line the map draws thick. */
private val WAY_END = Poi("", "", 0.0, 0.0, "", emptyMap())

private val day = SimpleDateFormat("M月d日", Locale.JAPAN)
