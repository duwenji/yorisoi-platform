package io.github.duwenji.yorisoi.kit.mood

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The top of the companion card (kit-ui CompanionCard's header): the walk's or ride's figures
 * ([status]) over a small picture of the [scene], with the mood in words in the corner.
 */
@Composable
fun MoodSceneHeader(scene: Mood, status: String) {
    Box(Modifier.fillMaxWidth().height(64.dp)) {
        MoodScene(scene, Modifier.matchParentSize())
        // Keeps the white figures readable over a bright sky.
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.25f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))))
        val onScene = MaterialTheme.typography.labelLarge.copy(
            color = Color.White, shadow = Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 6f),
        )
        Text(
            scene.display, style = onScene.copy(fontSize = 11.sp),
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp, 6.dp),
        )
        Text(status, style = onScene, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp, 8.dp))
    }
}
