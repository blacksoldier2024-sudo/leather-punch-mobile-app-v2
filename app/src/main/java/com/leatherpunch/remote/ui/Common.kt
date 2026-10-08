package com.leatherpunch.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leatherpunch.remote.*

fun screenTitle(s: Int): Pair<String, String> = when (s) {
    Scr.WELCOME -> "STARTING" to "Device is booting"
    Scr.HOME -> "HOME" to "Choose a profile"
    Scr.RATE -> "RATE" to "Punch / min"
    Scr.INTERVAL -> "INTERVAL" to "Count + dwell on top"
    Scr.INTERVAL_RUN -> "INTERVAL" to "Running"
    Scr.HOLD -> "HOLD" to "Single shot"
    Scr.COUNT -> "COUNT" to "Fixed number of punches"
    Scr.COUNT_RUN -> "COUNT" to "Running"
    Scr.STEP -> "STEP" to "Setup 1/2 · thickness & steps"
    Scr.STEP2 -> "STEP" to "Setup 2/2 · count & speed"
    Scr.STEP_RUN -> "STEP" to "Running"
    Scr.REPEAT -> "REPEAT" to "Setup 1/2 · thickness & repeats"
    Scr.REPEAT2 -> "REPEAT" to "Setup 2/2 · count & speed"
    Scr.REPEAT_RUN -> "REPEAT" to "Running"
    Scr.DEPTH -> "DEPTH" to "Depth, count & speed"
    Scr.DEPTH_RUN -> "DEPTH" to "Running"
    Scr.FULL -> "FULL ABC" to "Setup 1/3 · thickness & dwells"
    Scr.FULL2 -> "FULL ABC" to "Setup 2/3 · timing"
    Scr.FULL3 -> "FULL ABC" to "Setup 3/3 · count & homing"
    Scr.FULL_RUN -> "FULL ABC" to "Running"
    Scr.MANUAL -> "MANUAL" to "Jog & calibrate"
    Scr.DONE -> "DONE" to "Profile finished"
    Scr.SETTINGS -> "SETTINGS" to "Saved on the machine automatically"
    Scr.CONFLICT -> "PROFILE IN PROGRESS" to "Choose how to continue"
    else -> "…" to ""
}

/** کارت یک پارامتر: عدد بزرگ، (اسلایدر)، دکمه‌های گام. */
@Composable
fun ParamCard(vm: RemoteViewModel, spec: PSpec, enabled: Boolean = true, hint: String? = null) {
    val v = vm.eff(spec.key, spec.min)
    var drag by remember(spec.key) { mutableStateOf<Float?>(null) }
    val shown = drag ?: v

    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Label(spec.label, Modifier.weight(1f))
            if (hint != null) Text(hint, color = C.Dim, fontSize = 12.sp)
        }
        Spacer(Modifier.height(2.dp))
        BigNumber(fmt(shown, spec.decimals), spec.unit)
        if (spec.slider) {
            Slider(
                value = shown.coerceIn(spec.min, spec.max),
                onValueChange = { if (enabled) drag = it },
                onValueChangeFinished = { drag?.let { vm.setAbs(spec, it) }; drag = null },
                valueRange = spec.min..spec.max,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = C.Amber, activeTrackColor = C.Amber, inactiveTrackColor = C.PanelHi,
                    disabledThumbColor = C.Dim, disabledActiveTrackColor = C.Dim, disabledInactiveTrackColor = C.PanelHi,
                ),
            )
        } else Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            spec.quick.forEach { d ->
                StepBtn(signed(d), d > 0, enabled, Modifier.weight(1f)) { vm.adjust(spec, d) }
            }
        }
    }
}

@Composable
fun KeyValue(k: String, v: String, vColor: androidx.compose.ui.graphics.Color = C.Text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(k, color = C.Mid, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(v, color = vColor, fontSize = 15.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StatusPill(text: String, color: androidx.compose.ui.graphics.Color, pulsing: Boolean = false) {
    Row(
        Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(C.PanelHi)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
