package com.rhythm.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rhythm.app.optimem.Action
import com.rhythm.app.optimem.Decision
import com.rhythm.app.optimem.OptimemEngine
import com.rhythm.app.ui.RhythmViewModel

@Composable
fun OptimemScreen(vm: RhythmViewModel) {
    val context = LocalContext.current.applicationContext
    var decision by remember { mutableStateOf<Decision?>(null) }
    var snapshot by remember { mutableStateOf<OptimemEngine.MetricsSnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val engine = OptimemEngine(context)
        decision = engine.currentDecision()
        snapshot = engine.metricsSnapshot()
        loading = false
        loaded = true
    }

    if (loading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text(
                "Optimem",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            if (decision == null) {
                StillLearningOptimem()
            } else {
                DecisionCard(decision!!, vm)
            }
        }

        item {
            Text(
                "Today's Statistics",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            snapshot?.let { StatsGrid(it) }
        }
    }
}

@Composable
private fun DecisionCard(decision: Decision, vm: RhythmViewModel) {
    val label = vm.resolveLabel(decision.resource.forPackage)
    val confidence = (decision.predictedProbability * 100).toInt()
    val isPrefetch = decision.action == Action.PREFETCH

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Predicted Next",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "$confidence% confidence",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            Text(
                if (isPrefetch) "PREFETCH" else "SKIP",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isPrefetch)
                    MaterialTheme.colorScheme.tertiary
                else
                    MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(4.dp))
            Text(
                decision.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatsGrid(snapshot: OptimemEngine.MetricsSnapshot) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatRow("Predictions resolved", "${snapshot.resolvedPredictions}")
        StatRow(
            "Top-1 accuracy",
            if (snapshot.resolvedPredictions > 0)
                "${(snapshot.top1Accuracy * 100).toInt()}% (${snapshot.top1Correct}/${snapshot.resolvedPredictions})"
            else "—"
        )
        StatRow("Prefetches made", "${snapshot.prefetchCount}")
        StatRow("Useful (cache hits)", "${snapshot.cacheHitCount}")
        StatRow("Wasted prefetches", "${snapshot.wastedPrefetchCount}")
        StatRow("Latency saved", "${snapshot.totalLatencySavedMs} ms")
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
private fun StillLearningOptimem() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Still learning",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Optimem needs enough transitions before it can predict and decide on prefetching.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}