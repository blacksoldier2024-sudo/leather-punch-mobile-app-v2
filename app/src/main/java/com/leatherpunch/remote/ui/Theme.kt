package com.leatherpunch.remote.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** پالت هم‌خانواده با نمایشگر دستگاه: سرمه‌ای + کهربایی؛ سبز = START ، قرمز = STOP */
object C {
    val Bg = Color(0xFF0B1426)
    val Panel = Color(0xFF111D35)
    val PanelHi = Color(0xFF1A2B4D)
    val Line = Color(0xFF233659)
    val Text = Color(0xFFEAF0FA)
    val Mid = Color(0xFF9DB0CF)
    val Dim = Color(0xFF5F7396)
    val Amber = Color(0xFFFFB652)
    val AmberDim = Color(0xFF5A4220)
    val Green = Color(0xFF4FD08A)
    val GreenDim = Color(0xFF113A2B)
    val Red = Color(0xFFFF5A4F)
    val RedDim = Color(0xFF3B1A20)
    val Blue = Color(0xFF6AA4FF)
    val BlueDim = Color(0xFF1B2E57)
}

val Mono = FontFamily.Monospace

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Amber, onPrimary = Color.Black,
            background = C.Bg, onBackground = C.Text,
            surface = C.Panel, onSurface = C.Text,
        ),
        content = content,
    )
}

fun fmt(v: Float, d: Int): String = String.format(Locale.US, "%.${d}f", v)

fun fmtTime(sec: Float): String {
    if (sec < 0f) return "--"
    if (sec < 60f) return fmt(sec, 1) + " s"
    val t = sec.toInt()
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun signed(d: Float): String {
    val a = kotlin.math.abs(d)
    val body = if (a == a.toInt().toFloat()) a.toInt().toString() else fmt(a, 1)
    return (if (d < 0) "−" else "+") + body
}

// ------------------------------------------------------------------ Panel
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    accent: Color = C.Line,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(C.Panel)
            .border(1.dp, accent, shape)
            .padding(14.dp),
        content = content,
    )
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = C.Mid) {
    Text(text, modifier, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun BigNumber(text: String, unit: String = "", color: Color = C.Amber, size: TextUnit = 44.sp) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text, color = color, fontSize = size, fontFamily = Mono, fontWeight = FontWeight.Bold)
        if (unit.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Text(unit, color = C.Mid, fontSize = 15.sp, modifier = Modifier.padding(bottom = 7.dp))
        }
    }
}

// ------------------------------------------------------------------ Buttons
enum class Kind { Normal, Go, Danger, Auto, Amber }

@Composable
fun Btn(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: Kind = Kind.Normal,
    enabled: Boolean = true,
    height: Dp = 54.dp,
    fontSize: TextUnit = 16.sp,
    haptic: Boolean = true,
    icon: (@Composable (Color) -> Unit)? = null,
) {
    val fg = when (kind) {
        Kind.Normal -> C.Text; Kind.Go -> C.Green; Kind.Danger -> C.Red
        Kind.Auto -> C.Blue; Kind.Amber -> C.Amber
    }
    val bg = when (kind) {
        Kind.Normal -> C.PanelHi; Kind.Go -> C.GreenDim; Kind.Danger -> C.RedDim
        Kind.Auto -> C.BlueDim; Kind.Amber -> C.AmberDim
    }
    val shape = RoundedCornerShape(14.dp)
    val hf = LocalHapticFeedback.current
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(if (enabled) bg else C.Panel)
            .border(BorderStroke(1.dp, if (enabled) fg.copy(alpha = 0.55f) else C.Line), shape)
            .clickable(enabled = enabled) {
                if (haptic) hf.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        val c = if (enabled) fg else C.Dim
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) { icon(c); Spacer(Modifier.width(8.dp)) }
            Text(text, color = c, fontSize = fontSize, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}

/** دکمه‌ی گام با تکرار هنگام نگه‌داشتن */
@Composable
fun StepBtn(
    label: String,
    positive: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onStep: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val cb by rememberUpdatedState(onStep)
    var pressed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val hf = LocalHapticFeedback.current
    val tint = if (positive) C.Amber else C.Mid
    Box(
        modifier
            .height(48.dp)
            .clip(shape)
            .background(if (pressed) C.AmberDim else C.PanelHi)
            .border(1.dp, if (pressed) C.Amber else C.Line, shape)
            .then(
                if (enabled) Modifier.pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        pressed = true
                        hf.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        cb()
                        val job = scope.launch {
                            delay(450)
                            while (true) { cb(); delay(130) }
                        }
                        tryAwaitRelease()
                        job.cancel()
                        pressed = false
                    })
                } else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) tint else C.Dim,
            fontSize = 16.sp,
            fontFamily = Mono,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun Chip(text: String, selected: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(if (selected) C.AmberDim else C.PanelHi)
            .border(1.dp, if (selected) C.Amber else C.Line, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (!enabled) C.Dim else if (selected) C.Amber else C.Mid,
            fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp,
        )
    }
}

@Composable
fun Bar(frac: Float, color: Color = C.Amber, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(C.PanelHi)
    ) {
        Box(
            Modifier
                .fillMaxWidth(frac.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(shape)
                .background(color)
        )
    }
}
