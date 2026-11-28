package io.github.duwenji.yorisoi.kit.geo.map

import io.github.duwenji.yorisoi.kit.ui.AppLocales
import io.github.duwenji.yorisoi.core.text.localized
import io.github.duwenji.yorisoi.core.text.Languages
import android.text.format.DateFormat
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
import io.github.duwenji.yorisoi.kit.geo.WalkRoute
import io.github.duwenji.yorisoi.kit.geo.WayOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/** What was said about a spot along a way, and when. */
data class WayTalk(val name: String, val at: Long, val text: String)

/** The words for going along a way in each app, a walker's and a rider's, in the app's language (REQ-LANGUAGES F-4). */
class WayWords private constructor(private val walk: Boolean) {
    /** What the list says while it is empty. */
    internal val empty: String get() = if (walk) {
        localized(
            "まだありません。同じ道を 3 回歩くと、よく通る道になります。散歩の履歴から作ることもできます。",
            "None yet. Walk the same way three times and it becomes a frequent way. You can also make one from a walk in the history.",
            "还没有。同一条路走 3 次，就会成为常走的路。也可以从散步记录中创建。",
            "아직 없습니다. 같은 길을 3번 걸으면 자주 다니는 길이 됩니다. 산책 기록에서 만들 수도 있습니다.",
        )
    } else {
        localized(
            "まだありません。同じ道を 3 回走ると、よく通る道になります。走行の履歴から作ることもできます。",
            "None yet. Ride the same way three times and it becomes a frequent way. You can also make one from a ride in the history.",
            "还没有。同一条路骑 3 次，就会成为常走的路。也可以从骑行记录中创建。",
            "아직 없습니다. 같은 길을 3번 달리면 자주 다니는 길이 됩니다. 주행 기록에서 만들 수도 있습니다.",
        )
    }

    /** A way found and not kept: when it goes. */
    internal val foundUnsaved: String get() = if (walk) {
        localized("自動で見つけた（60 日歩かないと消えます）", "found (goes if not walked for 60 days)", "自动发现（60 天不走就会消失）", "자동으로 찾음(60일 동안 걷지 않으면 사라집니다)")
    } else {
        localized("自動で見つけた（60 日走らないと消えます）", "found (goes if not ridden for 60 days)", "自动发现（60 天不骑就会消失）", "자동으로 찾음(60일 동안 달리지 않으면 사라집니다)")
    }

    companion object {
        val WALK = WayWords(walk = true)
        val RIDE = WayWords(walk = false)
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
    Scaffold(topBar = { ScreenBar(localized("よく通る道", "Frequent ways", "常走的路", "자주 다니는 길"), onBack = onClose) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            item {
                if (ways.isEmpty()) {
                    Text(words.empty, style = MaterialTheme.typography.bodyMedium)
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
    "%.1f km".format(Locale.ROOT, way.lengthM / 1000),
    localized("${way.passes} 回", "${way.passes} times", "${way.passes} 次", "${way.passes}회"),
    localized("最後は", "last on ", "最近一次：", "마지막: ") + day(way.lastPassedAt),
    when {
        way.origin == WayOrigin.USER -> localized("自分で作った", "made by you", "自己创建", "직접 만듦")
        way.saved -> localized("自動で見つけた（保存済み）", "found (kept)", "自动发现（已保存）", "자동으로 찾음(저장됨)")
        else -> words.foundUnsaved
    },
).joinToString(localized("・", " · ", " · ", " · "))

@Composable
private fun WayDetail(way: FrequentWay, actions: WayActions, words: WayWords, onClose: () -> Unit) {
    val talks by produceState(emptyList<WayTalk>(), way.id) { value = withContext(Dispatchers.IO) { actions.talksOn(way) } }
    var name by remember(way.id) { mutableStateOf(way.name) }
    var deleting by remember { mutableStateOf(false) }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(localized("「${way.name}」を消しますか", "Delete “${way.name}”?", "要删除「${way.name}」吗？", "「${way.name}」을(를) 삭제할까요?")) },
            text = {
                if (way.origin == WayOrigin.FOUND) {
                    Text(
                        localized(
                            "消した道は、また何度通っても、自動ではよく通る道になりません。",
                            "A deleted way doesn't become a frequent way again by itself, however often you go along it.",
                            "删除的路，无论再走多少次，都不会自动成为常走的路。",
                            "삭제한 길은 몇 번을 다시 지나가도 자동으로 자주 다니는 길이 되지 않습니다.",
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    actions.delete(way.id)
                    onClose()
                }) { Text(Labels.delete) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(Labels.cancel) } },
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
                OutlinedTextField(name, { name = it }, label = { Text(localized("名前", "Name", "名称", "이름")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { actions.rename(way.id, name.trim()) }, enabled = name.isNotBlank() && name.trim() != way.name) {
                        Text(localized("名前を変える", "Rename", "改名", "이름 바꾸기"))
                    }
                    if (!way.saved) OutlinedButton(onClick = { actions.save(way.id) }) { Text(Labels.save) }
                    TextButton(onClick = { deleting = true }) { Text(Labels.delete) }
                }
            }
            if (talks.isNotEmpty()) item { Text(localized("この道で話した見どころ", "Sights talked about on this way", "在这条路上讲过的景点", "이 길에서 이야기한 볼거리"), style = MaterialTheme.typography.titleMedium) }
            items(talks) { talk ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text("${day(talk.at)}　${talk.name}", style = MaterialTheme.typography.titleSmall)
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
        Text(localized("よく通る道にする", "Make it a frequent way", "设为常走的路", "자주 다니는 길로 만들기"), style = MaterialTheme.typography.titleMedium)
        WayMap(route, highlight = section, Modifier.fillMaxWidth().height(320.dp))
        Text(
            localized("始まりと終わり: ", "Start and end: ", "起点和终点：", "시작과 끝: ") +
                "${"%.1f".format(Locale.ROOT, range.start / 1000)}〜${"%.1f".format(Locale.ROOT, range.endInclusive / 1000)} km" +
                localized("（太い線）", " (thick line)", "（粗线）", " (굵은 선)"),
            style = MaterialTheme.typography.bodySmall,
        )
        RangeSlider(value = range, onValueChange = { range = it }, valueRange = 0f..total.toFloat().coerceAtLeast(1f))
        OutlinedTextField(name, { name = it }, label = { Text(localized("名前（例: 通勤の道）", "Name (e.g. To work)", "名称（例：通勤的路）", "이름(예: 출근길)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(name.trim(), section) }, enabled = name.isNotBlank() && section.size >= 2) { Text(Labels.save) }
            TextButton(onClick = onCancel) { Text(Labels.cancel) }
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
private val WAY_END = RouteTarget("", "", 0.0, 0.0)

/** A day as the app's language writes it: 「10月28日」「Oct 28」. */
private fun day(at: Long): String {
    val locale = AppLocales.locale(Languages.current)
    return SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale).format(at)
}

/** The buttons' words. */
private object Labels {
    val save get() = localized("保存する", "Save", "保存", "저장")
    val delete get() = localized("消す", "Delete", "删除", "삭제")
    val cancel get() = localized("やめる", "Cancel", "取消", "취소")
}
