package com.rhythm.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rhythm.app.model.Evaluator
import com.rhythm.app.model.EvalResult
import com.rhythm.app.model.Predictor
import com.rhythm.app.model.TransitionExtractor
import com.rhythm.app.ui.RhythmViewModel

@Composable
fun ModelScreen(vm: RhythmViewModel) {
    val sessions by vm.sessions.collectAsState()

    val transitions = remember(sessions) { TransitionExtractor.extract(sessions) }
    val predictor = remember(transitions) { Predictor(transitions) }
    val evalResult = remember(transitions) { Evaluator.evaluate(transitions) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Model",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        item { AccuracyScorecard(evalResult, transitions.size) }

        item {
            Text(
                "Transition Matrix",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item { TransitionMatrix(predictor, vm) }
    }
}

@Composable
private fun AccuracyScorecard(eval: EvalResult, transitionCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Accuracy Scorecard",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            )

            Text(
                "$transitionCount transitions",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (transitionCount < 50) {
                Text(
                    "Not enough data yet — need 50+ transitions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                ScoreRow("Top-1", eval.top1, MaterialTheme.colorScheme.primary)
                ScoreRow("Top-3", eval.top3, MaterialTheme.colorScheme.secondary)
                ScoreRow("Baseline (most frequent)", eval.baseline, MaterialTheme.colorScheme.tertiary)

                Spacer(Modifier.height(4.dp))
                Text(
                    "Train: ${eval.trainCount}  |  Test: ${eval.testCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ScoreRow(label: String, value: Double, color: Color) {
    val pct = (value * 100).toInt()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            "$pct%",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(value.toFloat())
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
    }
}

@Composable
private fun TransitionMatrix(predictor: Predictor, vm: RhythmViewModel) {
    val transitions = predictor.rawTransitions()
    val apps = remember(transitions) {
        val set = mutableSetOf<String>()
        transitions.forEach { set.add(it.prev); set.add(it.next) }
        set.toList().sortedByDescending { app ->
            transitions.count { it.prev == app } + transitions.count { it.next == app }
        }.take(10)
    }

    // Build matrix: matrix[prev][next] = count
    val matrix = remember(transitions, apps) {
        val m = mutableMapOf<String, MutableMap<String, Int>>()
        for (t in transitions) {
            if (t.prev in apps && t.next in apps) {
                m.getOrPut(t.prev) { mutableMapOf() }
                m[t.prev]!![t.next] = m[t.prev]!!.getOrDefault(t.next, 0) + 1
            }
        }
        m
    }

    val maxCount = remember(matrix) {
        matrix.values.flatMap { it.values }.maxOrNull() ?: 1
    }

    Column(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        // Header row
        Row {
            Text("", modifier = Modifier.width(80.dp))
            apps.forEach { app ->
                Text(
                    vm.resolveLabel(app).take(4),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(32.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        // Matrix rows
        apps.forEach { prev ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    vm.resolveLabel(prev).take(10),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(80.dp)
                )
                apps.forEach { next ->
                    val count = matrix[prev]?.get(next) ?: 0
                    val fraction = if (maxCount > 0) count.toFloat() / maxCount else 0f
                    val alpha = if (count == 0) 0.03f else 0.15f + 0.85f * fraction
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .padding(1.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (count > 0) {
                            Text(
                                count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
