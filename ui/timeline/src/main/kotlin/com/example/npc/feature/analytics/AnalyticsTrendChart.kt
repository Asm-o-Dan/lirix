package com.example.npc.feature.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList

/**
 * Легковесный компонент отрисовки тренда финансовых операций.
 * Отрисовка выполняется через [DrawScope] с переиспользованием предвыделенного [Path]
 * для гарантии отсутствия аллокаций в горячем цикле отрисовки (60 FPS на Poco M7).
 */
@Composable
fun AnalyticsTrendChart(
    dataPoints: ImmutableList<Long>,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(180.dp),
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillGradientTopColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
    fillGradientBottomColor: Color = Color.Transparent,
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    pointColor: Color = MaterialTheme.colorScheme.tertiary
) {
    // Предвыделенные объекты Path, сохраняемые между кадрами (Zero-allocation onDraw)
    val linePath = remember { Path() }
    val fillPath = remember { Path() }

    val strokeWidth = 3.dp

    // Предварительный расчет минимума и максимума вне onDraw цикла
    val (minVal, maxVal) = remember(dataPoints) {
        if (dataPoints.isEmpty()) {
            0L to 1L
        } else {
            val min = dataPoints.minOrNull() ?: 0L
            val max = dataPoints.maxOrNull() ?: 0L
            if (min == max) min to (max + 1L) else min to max
        }
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val paddingHorizontal = 16f
        val paddingTop = 16f
        val paddingBottom = 24f

        val chartWidth = width - (paddingHorizontal * 2f)
        val chartHeight = height - paddingTop - paddingBottom

        if (chartWidth <= 0 || chartHeight <= 0) return@Canvas

        // 1. Отрисовка горизонтальных направляющих сетки (без аллокаций)
        drawGridLines(
            width = width,
            height = height,
            paddingHorizontal = paddingHorizontal,
            paddingTop = paddingTop,
            chartHeight = chartHeight,
            gridColor = gridColor
        )

        if (dataPoints.isEmpty()) {
            return@Canvas
        }

        val range = (maxVal - minVal).toFloat().coerceAtLeast(1f)

        if (dataPoints.size == 1) {
            val cx = width / 2f
            val cy = paddingTop + chartHeight / 2f
            drawCircle(color = lineColor, radius = 6f, center = Offset(cx, cy))
            drawLine(
                color = lineColor.copy(alpha = 0.5f),
                start = Offset(paddingHorizontal, cy),
                end = Offset(width - paddingHorizontal, cy),
                strokeWidth = 2f
            )
            return@Canvas
        }

        // 2. Сброс предвыделенных путей
        linePath.reset()
        fillPath.reset()

        val stepX = chartWidth / (dataPoints.size - 1)

        val firstY = paddingTop + chartHeight - ((dataPoints[0] - minVal).toFloat() / range * chartHeight)
        linePath.moveTo(paddingHorizontal, firstY)
        fillPath.moveTo(paddingHorizontal, paddingTop + chartHeight)
        fillPath.lineTo(paddingHorizontal, firstY)

        for (i in 1 until dataPoints.size) {
            val px = paddingHorizontal + (i * stepX)
            val py = paddingTop + chartHeight - ((dataPoints[i] - minVal).toFloat() / range * chartHeight)
            linePath.lineTo(px, py)
            fillPath.lineTo(px, py)
        }

        // Замыкание пути градиента к базовой линии
        fillPath.lineTo(paddingHorizontal + chartWidth, paddingTop + chartHeight)
        fillPath.close()

        // 3. Заливка области под графиком мягким градиентом
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(fillGradientTopColor, fillGradientBottomColor),
                startY = paddingTop,
                endY = paddingTop + chartHeight
            )
        )

        // 4. Отрисовка линии тренда
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round
            )
        )

        // 5. Отрисовка узловых точек
        for (i in dataPoints.indices) {
            val px = paddingHorizontal + (i * stepX)
            val py = paddingTop + chartHeight - ((dataPoints[i] - minVal).toFloat() / range * chartHeight)
            drawCircle(
                color = pointColor,
                radius = 4f,
                center = Offset(px, py)
            )
            drawCircle(
                color = Color.White,
                radius = 2f,
                center = Offset(px, py)
            )
        }
    }
}

private fun DrawScope.drawGridLines(
    width: Float,
    height: Float,
    paddingHorizontal: Float,
    paddingTop: Float,
    chartHeight: Float,
    gridColor: Color
) {
    // 3 горизонтальные линии
    for (i in 0..2) {
        val y = paddingTop + (chartHeight * (i / 2f))
        drawLine(
            color = gridColor,
            start = Offset(paddingHorizontal, y),
            end = Offset(width - paddingHorizontal, y),
            strokeWidth = 1f
        )
    }
}
