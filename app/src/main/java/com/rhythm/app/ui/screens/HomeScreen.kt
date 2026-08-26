package com.rhythm.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rhythm.app.model.Predictor
import com.rhythm.app.model.TransitionExtractor
import com.rhythm.app.ui.RhythmViewModel
import java.util.Calendar

@Composable
fun HomeScreen(vm: RhythmViewModel) {
    val sessions by vm.sessions.collectAsState()

    // Extract once and reuse — the old code called extract() twice per recomposition.
    val transitions = remember(sessions) { TransitionExtractor.extract(sessions) }
    val predictor = remember(transitions) { Predictor(transitions) }

    val canPredict = predictor.canPredict()
    val transitionCount = transitions.size

    // Find the most recent session as "prev"
    val prevSession = remember(sessions) {
        sessions.maxByOrNull { it.endTime }
    }

    val now = remember { Calendar.getInstance() }
    val hour = now.get(Calendar.HOUR_OF_DAY)
    val isWeekend = remember {
        val dow = now.get(Calendar.DAY_OF_WEEK)
        dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
    }

    val predictions = remember(predictor, prevSession, hour, isWeekend) {
        if (canPredict && prevSession != null) {
            predictor.predict(prevSession.packageName, hour, isWeekend)
        } else {
            emptyList()
        }
    }

    // 0L, not 0 — both branches must be Long or the inferred type collapses to Comparable.
    val etaMinutes: Long = remember(predictor, prevSession, hour) {
        if (canPredict && prevSession != null) {
            predictor.etaMinutes(prevSession.packageName, hour)
        } else 0L
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!canPredict) {
            Spacer(Modifier.weight(1f))
            StillLearning(transitionCount)
            Spacer(Modifier.weight(1f))
        } else if (predictions.isEmpty()) {
            Spacer(Modifier.weight(1f))
            CircularProgressIndicator()
            Spacer(Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(0.5f))

            val top = predictions[0]
            val label = vm.resolveLabel(top.packageName)
            val confidence = (top.score * 100).toInt()

            Text(
                "Next",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$confidence%",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "confidence",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "~${etaMinutes}m",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        "est. wait",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            Text(
                "Also likely",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            predictions.take(5).drop(1).forEach { pred ->
                PredictionBar(
                    label = vm.resolveLabel(pred.packageName),
                    score = pred.score,
                    maxScore = predictions[0].score
                )
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PredictionBar(label: String, score: Double, maxScore: Double) {
    val animatedWidth by animateFloatAsState(
        targetValue = (score / maxScore.coerceAtLeast(0.001)).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "bar"
    )
    val pct = (score * 100).toInt()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$pct%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedWidth)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun StillLearning(count: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Still learning",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "I need at least 50 app transitions to start predicting.\nYou have $count so far.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            "Keep using your phone normally — I'm watching the rhythm.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
