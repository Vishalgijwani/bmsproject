package com.rhythm.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rhythm.app.model.Transition
import com.rhythm.app.model.TransitionExtractor
import com.rhythm.app.ui.RhythmViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RhythmScreen(vm: RhythmViewModel) {
    val sessions by vm.sessions.collectAsState()
    var showWeekend by remember { mutableStateOf(false) }

    val transitions = remember(sessions) { TransitionExtractor.extract(sessions) }

    // Build hour x app heatmap data
    val heatmapData = remember(transitions, showWeekend) {
        buildHeatmap(transitions, showWeekend)
    }

    val hourCounts = remember(transitions, showWeekend) {
        buildHourCounts(transitions, showWeekend)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Rhythm",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showWeekend,
                    onClick = { showWeekend = false },
                    label = { Text("Weekday") }
                )
                FilterChip(
                    selected = showWeekend,
                    onClick = { showWeekend = true },
                    label = { Text("Weekend") }
                )
            }
        }

        item {
            RadialDial(hourCounts = hourCounts, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
        }

        item {
            Text(
                "App x Hour Heatmap",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(heatmapData.apps.take(12)) { app ->
            HeatmapRow(
                appLabel = vm.resolveLabel(app),
                hourCounts = heatmapData.matrix[app] ?: IntArray(24),
                maxCount = heatmapData.maxCount
            )
        }
    }
}

// ---- Radial Dial ----

@Composable
private fun RadialDial(hourCounts: IntArray, modifier: Modifier = Modifier) {
    val maxCount = hourCounts.maxOrNull() ?: 0
    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outerR = size.minDimension / 2f * 0.92f
        val innerR = outerR * 0.78f
        val barWidth = (2 * PI * outerR / 24).toFloat() * 0.7f

        // Background ring
        drawCircle(
            color = surfaceVariant,
            radius = outerR,
            style = Stroke(width = (outerR - innerR).toFloat(), cap = StrokeCap.Butt)
        )

        // 24 segments — midnight at top, clockwise
        for (hour in 0 until 24) {
            if (hourCounts[hour] == 0 || maxCount == 0) continue
            val fraction = hourCounts[hour].toFloat() / maxCount
            // Midnight at top: angle starts at -90°, clockwise
            val startAngle = -90f + hour * 15f // 360/24 = 15 degrees per hour
            val sweepAngle = 15f * 0.85f

            val arcThickness = (innerR + (outerR - innerR) * fraction - innerR).coerceAtLeast(1f)
            val arcR = innerR + (outerR - innerR) * fraction

            drawArc(
                color = primary.copy(alpha = 0.3f + 0.7f * fraction),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(cx - arcR, cy - arcR),
                size = androidx.compose.ui.geometry.Size(arcR * 2, arcR * 2),
                style = Stroke(width = (outerR - innerR) * fraction, cap = StrokeCap.Butt)
            )
        }

        // Hour labels at 0, 6, 12, 18
        for (h in listOf(0, 6, 12, 18)) {
            val angle = (-90 + h * 15) * PI / 180
            val labelR = outerR + 20f
            val x = cx + labelR * cos(angle).toFloat()
            val y = cy + labelR * sin(angle).toFloat()
            // We can't draw text in Canvas easily, skip for now
        }
    }
}

// ---- Heatmap ----

data class HeatmapData(
    val apps: List<String>,
    val matrix: Map<String, IntArray>, // app -> [24] counts
    val maxCount: Int
)

private fun buildHeatmap(transitions: List<Transition>, weekend: Boolean): HeatmapData {
    val filtered = transitions.filter { it.isWeekend == weekend }
    val appSet = mutableSetOf<String>()
    val matrix = mutableMapOf<String, IntArray>()

    for (t in filtered) {
        appSet.add(t.next)
        val arr = matrix.getOrPut(t.next) { IntArray(24) }
        arr[t.hour]++
    }

    val maxCount = matrix.values.flatMap { it.toList() }.maxOrNull() ?: 0
    val apps = appSet.toList().sortedByDescending { app -> matrix[app]!!.sum() }

    return HeatmapData(apps, matrix, maxCount)
}

@Composable
private fun HeatmapRow(appLabel: String, hourCounts: IntArray, maxCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            appLabel.take(12),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(100.dp)
        )
        Spacer(Modifier.width(4.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            for (h in 0 until 24) {
                val count = hourCounts[h]
                val fraction = if (maxCount > 0) count.toFloat() / maxCount else 0f
                val alpha = if (count == 0) 0.05f else 0.15f + 0.85f * fraction
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                )
            }
        }
    }
}

private fun buildHourCounts(transitions: List<Transition>, weekend: Boolean): IntArray {
    val counts = IntArray(24)
    transitions.filter { it.isWeekend == weekend }.forEach { counts[it.hour]++ }
    return counts
}
