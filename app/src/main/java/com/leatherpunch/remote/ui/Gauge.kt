package com.leatherpunch.remote.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leatherpunch.remote.Dev
import kotlin.math.cos
import kotlin.math.sin

/**
 * همان دو نمایشگر روی دستگاه، بزرگ‌تر:
 *   چپ  : ریل عمق (کالسکه‌ی سفید + پرشدن کهربایی)
 *   راست: حلقه‌ی دور شفت با عدد موقعیت به میلی‌متر
 */
@Composable
fun Gauge(dev: Dev, modifier: Modifier = Modifier, height: Dp = 170.dp, label: String = "DISTANCE FROM HOME") {
    val depth by animateFloatAsState(dev.depthFrac, tween(90, easing = LinearEasing), label = "depth")
    val shaft = dev.shaftFrac
    val moving = dev.motorRunning

    Column(modifier.fillMaxWidth()) {
        Label(label)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().height(height),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ریل
            Canvas(Modifier.width(26.dp).fillMaxHeight()) {
                val w = size.width
                val trackW = 8.dp.toPx()
                val x = (w - trackW) / 2f
                drawRoundRect(C.PanelHi, Offset(x, 0f), Size(trackW, size.height), CornerRadius(trackW / 2))
                val fillH = size.height * depth
                if (fillH > 0f) drawRoundRect(C.AmberDim, Offset(x, 0f), Size(trackW, fillH), CornerRadius(trackW / 2))
                val ch = 8.dp.toPx()
                val cy = (fillH - ch / 2f).coerceIn(0f, size.height - ch)
                drawRoundRect(Color.White, Offset(0f, cy), Size(w, ch), CornerRadius(3.dp.toPx()))
            }
            // حلقه
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxHeight().aspectRatio(1f)) {
                    val stroke = 9.dp.toPx()
                    val d = size.minDimension - stroke
                    val tl = Offset(stroke / 2f, stroke / 2f)
                    drawArc(C.PanelHi, 0f, 360f, false, tl, Size(d, d), style = Stroke(stroke))
                    if (shaft > 0.002f) {
                        drawArc(
                            if (moving) C.Amber else C.AmberDim,
                            -90f, 360f * shaft, false, tl, Size(d, d),
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                    val ang = Math.toRadians((-90.0 + 360.0 * shaft))
                    val r = d / 2f
                    val c = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(
                        Color.White, 5.dp.toPx(),
                        Offset(c.x + (cos(ang) * r).toFloat(), c.y + (sin(ang) * r).toFloat()),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        fmt(dev.posMm, 1), color = C.Amber, fontSize = 34.sp,
                        fontFamily = Mono, fontWeight = FontWeight.Bold,
                    )
                    Text("MM", color = C.Mid, fontSize = 12.sp)
                }
            }
        }
    }
}
