package com.dccbigfred.android.ui.connection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dccbigfred.android.R
import com.dccbigfred.android.network.IcmpPinger
import com.dccbigfred.android.ui.components.topAppBarEdgePadding
import kotlinx.coroutines.delay
import kotlin.math.max

data class LatencySample(
    val num: Int,
    val latencyMs: Long?,
    val atEpochMs: Long,
)

private const val MAX_SAMPLES = 30
private const val PING_INTERVAL_MS = 1_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionStatusScreen(
    serverUrl: String,
    wifiLockHeld: Boolean,
    onBack: () -> Unit,
) {
    val pinger = remember { IcmpPinger() }
    val endpoint = remember(serverUrl) { IcmpPinger.endpointFromBaseUrl(serverUrl) }
    val samples = remember { mutableStateListOf<LatencySample>() }
    var nextNum by remember { mutableIntStateOf(1) }
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val chartTimeLabel = stringResource(R.string.connection_chart_time)

    LaunchedEffect(serverUrl) {
        samples.clear()
        nextNum = 1
        while (true) {
            val latency = pinger.measureRttMs(endpoint.host, endpoint.port)
            val sample = LatencySample(
                num = nextNum,
                latencyMs = latency,
                atEpochMs = System.currentTimeMillis(),
            )
            nextNum += 1
            samples.add(0, sample)
            while (samples.size > MAX_SAMPLES) {
                samples.removeAt(samples.lastIndex)
            }
            delay(PING_INTERVAL_MS)
        }
    }

    // Chart uses chronological order (oldest → newest left to right).
    val chartSamples = samples.toList().asReversed().filter { it.latencyMs != null }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.topAppBarEdgePadding(),
                title = { Text(stringResource(R.string.connection_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = serverUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.connection_latency_axis),
                style = MaterialTheme.typography.labelMedium,
                color = labelColor,
            )
            Spacer(modifier = Modifier.height(4.dp))
            LatencyChart(
                samples = chartSamples,
                lineColor = lineColor,
                gridColor = gridColor,
                labelColor = labelColor,
                timeAxisLabel = chartTimeLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.connection_col_num),
                    modifier = Modifier.weight(0.35f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.connection_col_latency),
                    modifier = Modifier.weight(0.65f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                )
            }
            HorizontalDivider()
            LazyColumn(modifier = Modifier.weight(1f, fill = true)) {
                items(samples, key = { it.num }) { sample ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = sample.num.toString(),
                            modifier = Modifier.weight(0.35f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = sample.latencyMs?.let {
                                stringResource(R.string.connection_latency_ms, it.toInt())
                            } ?: stringResource(R.string.connection_timeout),
                            modifier = Modifier.weight(0.65f),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.End,
                            color = if (sample.latencyMs == null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    HorizontalDivider()
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(
                    if (wifiLockHeld) {
                        R.string.connection_wifi_lock_active
                    } else {
                        R.string.connection_wifi_lock_inactive
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (wifiLockHeld) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun LatencyChart(
    samples: List<LatencySample>,
    lineColor: androidx.compose.ui.graphics.Color,
    gridColor: androidx.compose.ui.graphics.Color,
    labelColor: androidx.compose.ui.graphics.Color,
    timeAxisLabel: String,
    modifier: Modifier = Modifier,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    Canvas(modifier = modifier) {
        val leftPad = 48.dp.toPx()
        val rightPad = 12.dp.toPx()
        val topPad = 12.dp.toPx()
        val bottomPad = 28.dp.toPx()
        val plotW = size.width - leftPad - rightPad
        val plotH = size.height - topPad - bottomPad
        if (plotW <= 0f || plotH <= 0f) return@Canvas

        val maxMs = max(50L, samples.maxOfOrNull { it.latencyMs ?: 0L } ?: 50L)
        val yMax = ((maxMs + 9) / 10) * 10f

        // Horizontal grid + Y labels
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(
                (labelColor.alpha * 255).toInt(),
                (labelColor.red * 255).toInt(),
                (labelColor.green * 255).toInt(),
                (labelColor.blue * 255).toInt(),
            )
            textSize = with(density) { 10.sp.toPx() }
            textAlign = android.graphics.Paint.Align.RIGHT
            isAntiAlias = true
        }
        for (i in 0..4) {
            val fraction = i / 4f
            val y = topPad + plotH * (1f - fraction)
            val value = (yMax * fraction).toInt()
            drawLine(
                color = gridColor,
                start = Offset(leftPad, y),
                end = Offset(leftPad + plotW, y),
                strokeWidth = 1f,
            )
            drawContext.canvas.nativeCanvas.drawText(
                "$value",
                leftPad - 8.dp.toPx(),
                y + paint.textSize / 3f,
                paint,
            )
        }

        // Axes
        drawLine(
            color = gridColor,
            start = Offset(leftPad, topPad),
            end = Offset(leftPad, topPad + plotH),
            strokeWidth = 2f,
        )
        drawLine(
            color = gridColor,
            start = Offset(leftPad, topPad + plotH),
            end = Offset(leftPad + plotW, topPad + plotH),
            strokeWidth = 2f,
        )

        if (samples.isEmpty()) return@Canvas

        val path = Path()
        samples.forEachIndexed { index, sample ->
            val ms = sample.latencyMs ?: return@forEachIndexed
            val x = if (samples.size == 1) {
                leftPad + plotW / 2f
            } else {
                leftPad + plotW * (index.toFloat() / (samples.size - 1))
            }
            val y = topPad + plotH * (1f - (ms.toFloat() / yMax).coerceIn(0f, 1f))
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(x, y))
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )

        paint.textAlign = android.graphics.Paint.Align.CENTER
        drawContext.canvas.nativeCanvas.drawText(
            timeAxisLabel,
            leftPad + plotW / 2f,
            size.height - 4.dp.toPx(),
            paint,
        )
    }
}
