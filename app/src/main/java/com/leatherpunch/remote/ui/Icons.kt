package com.leatherpunch.remote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable fun IconPlay(c: Color, s: Dp = 18.dp) = Canvas(Modifier.size(s)) {
    val p = Path().apply { moveTo(size.width * .2f, 0f); lineTo(size.width, size.height / 2); lineTo(size.width * .2f, size.height); close() }
    drawPath(p, c)
}
@Composable fun IconPause(c: Color, s: Dp = 18.dp) = Canvas(Modifier.size(s)) {
    val w = size.width * .28f
    drawRoundRect(c, Offset(size.width * .1f, 0f), Size(w, size.height), CornerRadius(2f))
    drawRoundRect(c, Offset(size.width * .62f, 0f), Size(w, size.height), CornerRadius(2f))
}
@Composable fun IconStop(c: Color, s: Dp = 18.dp) = Canvas(Modifier.size(s)) {
    drawRoundRect(c, Offset(size.width * .1f, size.height * .1f), Size(size.width * .8f, size.height * .8f), CornerRadius(3f))
}
@Composable fun IconBack(c: Color, s: Dp = 18.dp) = Canvas(Modifier.size(s)) {
    val st = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
    drawLine(c, Offset(size.width * .95f, size.height / 2), Offset(size.width * .1f, size.height / 2), st.width, StrokeCap.Round)
    drawLine(c, Offset(size.width * .45f, size.height * .1f), Offset(size.width * .05f, size.height / 2), st.width, StrokeCap.Round)
    drawLine(c, Offset(size.width * .45f, size.height * .9f), Offset(size.width * .05f, size.height / 2), st.width, StrokeCap.Round)
}
@Composable fun IconNext(c: Color, s: Dp = 18.dp) = Canvas(Modifier.size(s)) {
    val w = 2.6.dp.toPx()
    drawLine(c, Offset(size.width * .05f, size.height / 2), Offset(size.width * .9f, size.height / 2), w, StrokeCap.Round)
    drawLine(c, Offset(size.width * .55f, size.height * .1f), Offset(size.width * .95f, size.height / 2), w, StrokeCap.Round)
    drawLine(c, Offset(size.width * .55f, size.height * .9f), Offset(size.width * .95f, size.height / 2), w, StrokeCap.Round)
}

/** آیکون کاشی‌های Home (شکل ساده‌ی همان آیکون‌های دستگاه) */
@Composable
fun TileIcon(kind: Int, c: Color, s: Dp = 30.dp) = Canvas(Modifier.size(s)) {
    val w = 2.4.dp.toPx()
    val st = Stroke(width = w, cap = StrokeCap.Round)
    val W = size.width; val H = size.height
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(c, Offset(W * x1, H * y1), Offset(W * x2, H * y2), w, StrokeCap.Round)
    when (kind) {
        0 -> { // RATE: ساعت
            drawCircle(c, W * .42f, Offset(W / 2, H / 2), style = st)
            line(.5f, .5f, .5f, .22f); line(.5f, .5f, .72f, .6f)
        }
        1 -> { // INTERVAL: پانچ + توقف
            drawCircle(c, W * .42f, Offset(W / 2, H / 2), style = st)
            line(.4f, .32f, .4f, .68f); line(.6f, .32f, .6f, .68f)
        }
        2 -> { // HOLD: انگشت روی دکمه
            drawCircle(c, W * .2f, Offset(W / 2, H * .42f), style = st)
            line(.2f, .82f, .8f, .82f)
        }
        3 -> { // COUNT: سه نقطه
            drawCircle(c, W * .09f, Offset(W * .2f, H / 2))
            drawCircle(c, W * .09f, Offset(W * .5f, H / 2))
            drawCircle(c, W * .09f, Offset(W * .8f, H / 2))
        }
        4 -> { // STEP: پله
            val p = Path().apply { moveTo(W * .1f, H * .85f); lineTo(W * .1f, H * .6f); lineTo(W * .4f, H * .6f); lineTo(W * .4f, H * .35f); lineTo(W * .7f, H * .35f); lineTo(W * .7f, H * .12f); lineTo(W * .92f, H * .12f) }
            drawPath(p, c, style = st)
        }
        5 -> { // REPEAT: فلش چرخشی
            drawArc(c, 40f, 280f, false, Offset(W * .12f, H * .12f), Size(W * .76f, H * .76f), style = st)
            line(.78f, .2f, .9f, .38f); line(.78f, .2f, .6f, .26f)
        }
        6 -> { // DEPTH: فلش به پایین روی خط
            line(.5f, .1f, .5f, .7f); line(.28f, .5f, .5f, .72f); line(.72f, .5f, .5f, .72f)
            line(.12f, .9f, .88f, .9f)
        }
        7 -> { // FULL: A-B-C
            drawRoundRect(c, Offset(W * .08f, H * .2f), Size(W * .84f, H * .6f), CornerRadius(6f), style = st)
            line(.36f, .2f, .36f, .8f); line(.64f, .2f, .64f, .8f)
        }
        else -> { // MANUAL: دو فلش
            line(.1f, .5f, .9f, .5f); line(.1f, .5f, .28f, .3f); line(.1f, .5f, .28f, .7f)
            line(.9f, .5f, .72f, .3f); line(.9f, .5f, .72f, .7f)
        }
    }
}
