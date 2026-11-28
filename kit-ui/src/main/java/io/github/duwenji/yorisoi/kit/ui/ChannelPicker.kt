package io.github.duwenji.yorisoi.kit.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.duwenji.yorisoi.core.text.localized

/**
 * A channel to choose in [ChannelPicker]: its [id], [name] and [summary] in the app's language, and
 * whether it offers that language ([inAppLanguage], platform REQ-LANGUAGES F-23). A regional
 * channel not yet taken in is [installed] false: choosing it takes it in first.
 */
data class ChannelChoice(
    val id: String,
    val name: String,
    val summary: String,
    val inAppLanguage: Boolean = true,
    val installed: Boolean = true,
)

/**
 * The channel in use, as the main screen's title; tapping it lists the others, the [regional]
 * ones where the user is first (platform REQ-REGIONAL-CHANNELS F-4〜F-6). [onChoose] gets the id
 * of an installed channel, [onUseRegional] one to take in.
 */
@Composable
fun ChannelPicker(
    currentId: String,
    currentName: String,
    channels: List<ChannelChoice>,
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier,
    regional: List<ChannelChoice> = emptyList(),
    onUseRegional: (String) -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val selected = localized("選択中", "Selected", "已选择", "선택됨")
    Box(modifier) {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp))
                .clickable(onClickLabel = localized("チャンネルを選ぶ", "Choose a channel", "选择频道", "채널 선택"), role = Role.Button) { open = true }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Radio, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column {
                Text(localized("チャンネル", "Channel", "频道", "채널"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        currentName, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (regional.isNotEmpty()) {
                Text(
                    localized("この地域のチャンネル", "Channels for this area", "这一带的频道", "이 지역의 채널"),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                regional.forEach { r ->
                    DropdownMenuItem(
                        text = {
                            ChoiceText(
                                r,
                                if (r.installed) r.summary
                                else localized("取り込んで使う・${r.summary}", "Take in to use · ${r.summary}", "获取后使用・${r.summary}", "가져와서 사용・${r.summary}"),
                            )
                        },
                        leadingIcon = {
                            if (r.id == currentId) Icon(Icons.Filled.Check, contentDescription = selected) else Icon(Icons.Filled.Place, contentDescription = null)
                        },
                        onClick = {
                            open = false
                            when {
                                r.id == currentId -> Unit
                                r.installed -> onChoose(r.id)
                                else -> onUseRegional(r.id)
                            }
                        },
                    )
                }
                HorizontalDivider()
            }
            channels.forEach { c ->
                DropdownMenuItem(
                    text = { ChoiceText(c, c.summary) },
                    leadingIcon = { if (c.id == currentId) Icon(Icons.Filled.Check, contentDescription = selected) },
                    onClick = {
                        open = false
                        if (c.id != currentId) {
                            // F-23: switching to a channel without the app's language says so.
                            if (!c.inAppLanguage) Toast.makeText(context, LANGUAGE_NOTE, Toast.LENGTH_LONG).show()
                            onChoose(c.id)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ChoiceText(choice: ChannelChoice, summary: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(choice.name, style = MaterialTheme.typography.bodyLarge)
        if (!choice.inAppLanguage) LanguageMark()
        Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** That a channel doesn't offer the app's language (platform REQ-LANGUAGES D-8・F-23). */
@Composable
fun LanguageMark() {
    Text(
        localized("日本語に未対応", "Not in English", "不支持中文", "한국어 미지원"),
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary,
    )
}

/** What choosing a channel without the app's language means (platform REQ-LANGUAGES F-23). */
val LANGUAGE_NOTE: String get() = localized(
    "このチャンネルは日本語に対応していません。AI の語りは日本語で話しますが、そのまま読む一部の文は流れません。",
    "This channel isn't in English. The AI still talks in English, but some lines that are read as written won't be read.",
    "这个频道不支持中文。AI 的讲述仍然用中文，但部分原样朗读的语句不会播放。",
    "이 채널은 한국어를 지원하지 않습니다. AI의 이야기는 한국어로 하지만, 그대로 읽는 일부 문장은 나오지 않습니다.",
)
