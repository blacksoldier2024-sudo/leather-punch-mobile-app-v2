package com.leatherpunch.remote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leatherpunch.remote.*
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

private val GAP = Arrangement.spacedBy(12.dp)

// =====================================================================  HOME
private val TILES = listOf(
    "RATE" to "Constant punches / min",
    "INTERVAL" to "Dwell on top",
    "HOLD" to "Single shot",
    "COUNT" to "Fixed count",
    "STEP" to "Stepped depth",
    "REPEAT" to "Repeat in place",
    "DEPTH" to "Set depth",
    "FULL ABC" to "A-B-C-D-E cycle",
    "MANUAL" to "Jog & calibrate",
)

@Composable
fun HomeUi(vm: RemoteViewModel, dev: Dev) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = GAP) {
        if (dev.activeProgress >= 0 && dev.motorRunning) {
            Panel(accent = C.Green) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("A profile is running", color = C.Green, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("Return to its progress screen", color = C.Mid, fontSize = 13.sp)
                    }
                    Btn("Open", { vm.act("progress") }, Modifier.width(96.dp), Kind.Go, height = 44.dp)
                }
            }
        }
        Panel { Gauge(dev, height = 110.dp) }
        for (row in 0..2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (col in 0..2) {
                    val i = row * 3 + col
                    val shape = RoundedCornerShape(16.dp)
                    Column(
                        Modifier.weight(1f).aspectRatio(0.92f).clip(shape).background(C.Panel)
                            .border(1.dp, C.Line, shape)
                            .clickable { vm.act("tile", i) }
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        TileIcon(i, C.Amber)
                        Spacer(Modifier.height(8.dp))
                        Text(TILES[i].first, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(TILES[i].second, color = C.Dim, fontSize = 10.sp, textAlign = TextAlign.Center, lineHeight = 12.sp)
                    }
                }
            }
        }
        Btn("Disconnect", { vm.disconnect() }, Modifier.fillMaxWidth(), Kind.Normal, height = 46.dp, fontSize = 14.sp)
    }
}

// =====================================================================  RATE
@Composable
fun RateUi(vm: RemoteViewModel, dev: Dev) {
    val running = dev.snapRun
    val homing = dev.snapFlag
    val ppm = vm.eff("ppm", 30f).toInt()
    Column(Modifier.fillMaxWidth(), verticalArrangement = GAP) {
        Panel { Gauge(dev) }
        ParamCard(vm, Specs.RATE_PPM)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 20, 30, 60).forEach { p ->
                Chip(p.toString(), ppm == p, modifier = Modifier.weight(1f)) { vm.setAbs(Specs.RATE_PPM, p.toFloat()) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("START", { vm.act("start") }, Modifier.weight(1f), Kind.Go, enabled = !running && !homing,
                height = 62.dp, icon = { IconPlay(it) })
            Btn(if (homing) "HOMING…" else "PAUSE", { vm.act("pause") }, Modifier.weight(1f), Kind.Danger,
                enabled = running, height = 62.dp, icon = { IconPause(it) })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            when {
                homing -> StatusPill("RETURNING HOME", C.Amber)
                running -> StatusPill("RUNNING", C.Green)
                else -> StatusPill("READY", C.Mid)
            }
        }
    }
}

// =====================================================================  HOLD
@Composable
fun HoldUi(vm: RemoteViewModel, dev: Dev) {
    val punching = dev.snapRun
    val ppm = vm.eff("ppm", 30f).toInt()
    Column(Modifier.fillMaxWidth(), verticalArrangement = GAP) {
        Panel { Gauge(dev) }
        Panel {
            Label("Punches this session")
            BigNumber(dev.snapCount.toString(), "", C.Text)
        }
        ParamCard(vm, Specs.RATE_PPM, enabled = !punching, hint = if (punching) "locked while punching" else null)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 20, 30, 60).forEach { p ->
                Chip(p.toString(), ppm == p, enabled = !punching, modifier = Modifier.weight(1f)) {
                    vm.setAbs(Specs.RATE_PPM, p.toFloat())
                }
            }
        }
        Btn("PUNCH ONCE", { vm.act("start") }, Modifier.fillMaxWidth(), Kind.Go, enabled = !punching,
            height = 70.dp, fontSize = 20.sp, icon = { IconPlay(it, 22.dp) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            StatusPill(if (punching) "PUNCHING" else "SINGLE SHOT", if (punching) C.Green else C.Mid)
        }
    }
}

// =====================================================================  SETUP
class SetupInfo(
    val specs: List<PSpec>,
    val summary: List<Pair<String, String>>,
    val primary: String,
)

fun setupInfo(vm: RemoteViewModel, dev: Dev, scr: Int): SetupInfo? {
    fun e(k: String, d: Float = 0f) = vm.eff(k, d)
    val course = dev.courseMm
    return when (scr) {
        Scr.INTERVAL -> {
            val n = e("ic", 10f); val dw = e("im") * 60f + e("is", 5f)
            SetupInfo(
                listOf(Specs.IV_COUNT, Specs.IV_MIN, Specs.IV_SEC),
                listOf("Cycle (30 PPM, fixed)" to "2.0 s", "Dwell on top" to fmtTime(dw), "Total time" to fmtTime(n * (2f + dw))),
                "START",
            )
        }
        Scr.COUNT -> SetupInfo(
            listOf(Specs.COUNT),
            listOf("Speed" to "30 PPM (fixed)", "Total time" to fmtTime(e("cn", 10f) * 2f)),
            "START",
        )
        Scr.STEP -> {
            val t = e("sT", 1.6f); val n = max(1f, e("sN", 10f))
            SetupInfo(
                listOf(Specs.STEP_THICK, Specs.STEP_N),
                listOf("Depth per step" to fmt(t / n, 2) + " mm"),
                "NEXT",
            )
        }
        Scr.STEP2 -> {
            val n = e("sP", 10f); val p = max(1f, e("sM", 30f))
            SetupInfo(
                listOf(Specs.STEP_COUNT, Specs.STEP_PPM),
                listOf("Thickness" to fmt(dev.p("sT"), 1) + " mm", "Steps" to dev.pi("sN").toString(),
                    "Cycle" to fmtTime(60f / p), "Total ≈" to fmtTime(n * 60f / p)),
                "START",
            )
        }
        Scr.REPEAT -> SetupInfo(
            listOf(Specs.REP_THICK, Specs.REP_N),
            listOf("Each punch" to "repeated ${e("rN", 10f).toInt()}× at ${fmt(e("rT", 1.6f), 1)} mm"),
            "NEXT",
        )
        Scr.REPEAT2 -> {
            val n = e("rP", 10f); val p = max(1f, e("rM", 30f))
            SetupInfo(
                listOf(Specs.REP_COUNT, Specs.REP_PPM),
                listOf("Thickness" to fmt(dev.p("rT"), 1) + " mm", "Repeats" to dev.pi("rN").toString(),
                    "Cycle" to fmtTime(60f / p), "Total ≈" to fmtTime(n * 60f / p)),
                "START",
            )
        }
        Scr.DEPTH -> {
            val n = e("dP", 10f); val p = max(1f, e("dM", 30f)); val d = e("dD", 15f)
            SetupInfo(
                listOf(Specs.DEPTH, Specs.DEPTH_COUNT, Specs.DEPTH_PPM),
                listOf("Depth / course" to "${fmt(d, 1)} / ${fmt(course, 0)} mm",
                    "Cycle" to fmtTime(60f / p), "Total ≈" to fmtTime(n * 60f / p)),
                "START",
            )
        }
        Scr.FULL -> SetupInfo(
            listOf(Specs.FULL_THICK, Specs.FULL_BOTTOM, Specs.FULL_REF),
            listOf("Leather edge at" to fmt(max(0f, course - e("fT", 1.6f)), 1) + " mm from home"),
            "NEXT",
        )
        Scr.FULL2 -> {
            val per = e("fD", 1f) + e("fC", 1f) + e("fB", .5f) + e("fA", 1.5f) + e("fR", 1f)
            SetupInfo(
                listOf(Specs.FULL_DESC, Specs.FULL_CUT, Specs.FULL_ASC),
                listOf("Cycle A–E" to fmtTime(per)),
                "NEXT",
            )
        }
        Scr.FULL3 -> {
            val per = dev.p("fD", 1f) + dev.p("fC", 1f) + dev.p("fB", .5f) + dev.p("fA", 1.5f) + dev.p("fR", 1f)
            SetupInfo(
                listOf(Specs.FULL_COUNT, Specs.FULL_HOME),
                listOf("Cycle A–E" to fmtTime(per), "Total ≈" to fmtTime(per * e("fP", 10f))),
                "START",
            )
        }
        else -> null
    }
}

@Composable
fun SetupUi(vm: RemoteViewModel, dev: Dev, scr: Int) {
    val info = setupInfo(vm, dev, scr) ?: return
    Column(Modifier.fillMaxWidth(), verticalArrangement = GAP) {
        info.specs.forEach { ParamCard(vm, it) }
        Panel {
            info.summary.forEach { KeyValue(it.first, it.second) }
        }
    }
}

// =====================================================================  RUN
class RunModel(
    val phase: String,
    val active: Boolean,
    val done: Int,
    val total: Int,
    val rows: List<Pair<String, String>>,
    val startLabel: String,
    val startAct: String,
    val startEnabled: Boolean,
    val startKind: Kind,
    val hasAuto: Boolean = false,
    val autoOn: Boolean = false,
    val autoEnabled: Boolean = false,
    val subLabel: String? = null,
    val subFrac: Float = 0f,
    val stages: List<String>? = null,
    val stageIdx: Int = -1,
)

fun runModel(dev: Dev, scr: Int): RunModel? {
    val rt = dev.rt
    return when (scr) {
        Scr.INTERVAL_RUN -> {
            val ph = rt.i("i0"); val done = rt.i("i1"); val total = dev.pi("ic", 10)
            val dwell = (dev.pi("im") * 60 + dev.pi("is")).toFloat()
            val elapsed = rt.l("i3") / 1000f
            val totalSec = total * (2f + dwell)
            RunModel(
                listOf("Idle", "Punching", "Dwelling", "Paused", "Done").getOrElse(ph) { "-" },
                ph == 1 || ph == 2, done, total,
                listOf("Dwell on top" to fmtTime(dwell), "Elapsed" to fmtTime(elapsed),
                    "Remaining" to fmtTime(max(0f, totalSec - elapsed))),
                if (ph == 3) "RESUME" else "PAUSE", "pause", ph in 1..3,
                if (ph == 3) Kind.Go else Kind.Danger,
                subLabel = if (ph == 2 && dwell > 0f) "Dwell on top" else null,
                subFrac = if (dwell > 0f) rt.l("i2") / 1000f / dwell else 0f,
            )
        }
        Scr.COUNT_RUN -> {
            val ph = rt.i("c0")
            RunModel(
                listOf("Ready", "Punching", "Done").getOrElse(ph) { "-" }, ph == 1,
                rt.i("c1"), dev.pi("cn", 10),
                listOf("Speed" to "30 PPM (fixed)"),
                "PUNCH ONCE", "start", ph == 0, Kind.Go,
            )
        }
        Scr.STEP_RUN -> {
            val ph = rt.i("s0"); val idx = rt.i("s2"); val n = max(1, dev.pi("sN", 10)); val t = dev.p("sT", 1.6f)
            RunModel(
                listOf("Ready", "Cutting", "Retracting", "Homing", "Done").getOrElse(ph) { "-" },
                ph in 1..3, rt.i("s1"), dev.pi("sP", 10),
                listOf("Step" to "$idx / $n",
                    "Depth into leather" to "${fmt(t / n * idx, 2)} / ${fmt(t, 1)} mm",
                    "Speed" to "${dev.pi("sM", 30)} PPM"),
                "START", "start", ph == 0, Kind.Go,
                hasAuto = true, autoOn = rt.i("s3") == 1, autoEnabled = ph == 0 || rt.i("s3") == 1,
            )
        }
        Scr.REPEAT_RUN -> {
            val ph = rt.i("r0"); val idx = rt.i("r2"); val n = max(1, dev.pi("rN", 10))
            RunModel(
                listOf("Ready", "Cutting", "Retracting", "Homing", "Done").getOrElse(ph) { "-" },
                ph in 1..3, rt.i("r1"), dev.pi("rP", 10),
                listOf("Repeat" to "$idx / $n", "Thickness" to fmt(dev.p("rT", 1.6f), 1) + " mm",
                    "Speed" to "${dev.pi("rM", 30)} PPM"),
                "START", "start", ph == 0, Kind.Go,
                hasAuto = true, autoOn = rt.i("r3") == 1, autoEnabled = ph == 0 || rt.i("r3") == 1,
            )
        }
        Scr.DEPTH_RUN -> {
            val ph = rt.i("d0")
            RunModel(
                listOf("Ready", "Cutting", "Homing", "Done").getOrElse(ph) { "-" },
                ph in 1..2, rt.i("d1"), dev.pi("dP", 10),
                listOf("Depth" to fmt(dev.p("dD", 15f), 1) + " mm", "Speed" to "${dev.pi("dM", 30)} PPM"),
                "START", "start", ph == 0, Kind.Go,
                hasAuto = true, autoOn = rt.i("d3") == 1, autoEnabled = ph == 0 || rt.i("d3") == 1,
            )
        }
        Scr.FULL_RUN -> {
            val ph = rt.i("f0"); val st = rt.i("f1"); val total = dev.pi("fP", 10)
            val secs = listOf(0f, dev.p("fD", 1f), dev.p("fC", 1f), dev.p("fB", .5f), dev.p("fA", 1.5f), dev.p("fR", 1f))
            val per = secs.sum()
            val names = listOf("DOWN TO EDGE", "CUTTING", "HOLD BOTTOM", "RETURN HOME", "HOLD HOME")
            RunModel(
                if (st in 1..5) names[st - 1] else if (ph == 2) "Done" else "Ready",
                ph == 1, rt.i("f2"), total,
                listOf("Cycle A–E" to fmtTime(per), "Total ≈" to fmtTime(per * total),
                    "Leather edge" to fmt(max(0f, dev.courseMm - dev.p("fT", 1.6f)), 1) + " mm"),
                "START", "start", ph == 0, Kind.Go,
                hasAuto = true, autoOn = rt.i("f3") == 1, autoEnabled = ph == 0 || rt.i("f3") == 1,
                subLabel = if (st in 1..5) names[st - 1] else null,
                subFrac = if (st in 1..5 && secs[st] > 0f) rt.l("f4") / 1000f / secs[st] else 0f,
                stages = listOf("A", "B", "C", "D", "E"), stageIdx = st - 1,
            )
        }
        else -> null
    }
}

@Composable
fun RunUi(vm: RemoteViewModel, dev: Dev, scr: Int) {
    val m = runModel(dev, scr) ?: return
    val frac = if (m.total > 0) m.done.toFloat() / m.total else 0f
    Column(Modifier.fillMaxWidth(), verticalArrangement = GAP) {
        Panel { Gauge(dev) }
        Panel(accent = if (m.active) C.Green.copy(alpha = 0.6f) else C.Line) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(m.phase.uppercase(), if (m.active) C.Green else C.Mid)
                Spacer(Modifier.weight(1f))
                Text("${m.done}", color = C.Text, fontSize = 30.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
                Text(" / ${m.total}", color = C.Mid, fontSize = 18.sp, fontFamily = Mono)
            }
            Spacer(Modifier.height(10.dp))
            Bar(frac, C.Amber, height = 12.dp)

            if (m.stages != null) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    m.stages.forEachIndexed { i, s ->
                        val cur = i == m.stageIdx
                        val past = m.stageIdx > i
                        Box(
                            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (cur) C.Amber else if (past) C.GreenDim else C.PanelHi),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(s, color = if (cur) Color.Black else if (past) C.Green else C.Dim,
                                fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
            if (m.subLabel != null) {
                Spacer(Modifier.height(12.dp))
                Label(m.subLabel)
                Spacer(Modifier.height(6.dp))
                Bar(m.subFrac, C.Blue, height = 8.dp)
            }
            Spacer(Modifier.height(12.dp))
            m.rows.forEach { KeyValue(it.first, it.second) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn(m.startLabel, { vm.act(m.startAct) }, Modifier.weight(1f), m.startKind, enabled = m.startEnabled,
                height = 64.dp, fontSize = 18.sp,
                icon = { if (m.startKind == Kind.Danger) IconPause(it) else IconPlay(it) })
            if (m.hasAuto) {
                Btn(if (m.autoOn) "AUTO ON" else "AUTO", { vm.act("auto") }, Modifier.weight(1f), Kind.Auto,
                    enabled = m.autoEnabled, height = 64.dp, fontSize = 18.sp)
            }
        }
        if (m.hasAuto && m.autoOn) {
            Text("Tap AUTO again to stop after the current cycle.", color = C.Dim, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

// =====================================================================  MANUAL
@Composable
fun ManualUi(vm: RemoteViewModel, dev: Dev) {
    val running = dev.motorRunning
    Column(Modifier.fillMaxWidth(), verticalArrangement = GAP) {
        Panel { Gauge(dev) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("HOME\n" + if (dev.homeSw) "switch ON" else "go to start", { vm.act("homesw") },
                Modifier.weight(1f), if (dev.homeSw) Kind.Amber else Kind.Normal, enabled = !running, height = 68.dp,
                fontSize = 15.sp)
            Btn("END STOP\n" + if (dev.endSw) "switch ON" else "go to end", { vm.act("endsw") },
                Modifier.weight(1f), if (dev.endSw) Kind.Amber else Kind.Normal, enabled = !running, height = 68.dp,
                fontSize = 15.sp)
        }
        Panel {
            Label("Jog (mm)")
            Spacer(Modifier.height(8.dp))
            val labels = listOf("−5", "−1", "−0.1", "+0.1", "+1", "+5")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                labels.forEachIndexed { i, l ->
                    Btn(l, { vm.act("jog", i) }, Modifier.weight(1f), if (i >= 3) Kind.Amber else Kind.Normal,
                        height = 52.dp, fontSize = 14.sp)
                }
            }
        }
        Panel {
            Label("Joystick — hold and slide")
            Spacer(Modifier.height(10.dp))
            Joystick(vm, dev)
        }
    }
}

@Composable
fun Joystick(vm: RemoteViewModel, dev: Dev) {
    var value by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val speed = value * dev.maxSpeed * 0.35f
    val dead = kotlin.math.abs(value) < 0.14f

    LaunchedEffect(dragging) {
        while (dragging) {
            vm.joy((value * 100f).roundToInt())
            delay(100)
        }
    }
    DisposableEffect(Unit) { onDispose { vm.joyRelease() } }

    Box(
        Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(40.dp)).background(C.PanelHi)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val margin = 40.dp.toPx()
                    fun norm(x: Float): Float {
                        val half = size.width / 2f
                        return ((x - half) / (half - margin)).coerceIn(-1f, 1f)
                    }
                    val down = awaitFirstDown()
                    dragging = true
                    value = norm(down.position.x)
                    do {
                        val ev = awaitPointerEvent()
                        val p = ev.changes.firstOrNull()
                        if (p != null && p.pressed) {
                            value = norm(p.position.x)
                            p.consume()
                        }
                    } while (ev.changes.any { it.pressed })
                    dragging = false
                    value = 0f
                    vm.joyRelease()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val margin = 40.dp.toPx()
            val cx = size.width / 2f
            drawRoundRect(C.Line, Offset(cx - 1.dp.toPx(), 14.dp.toPx()), Size(2.dp.toPx(), size.height - 28.dp.toPx()), CornerRadius(1f))
            val x = cx + value * (cx - margin)
            if (kotlin.math.abs(x - cx) > 2f) {
                drawRoundRect(
                    C.AmberDim, Offset(minOf(cx, x), size.height / 2f - 3.dp.toPx()),
                    Size(kotlin.math.abs(x - cx), 6.dp.toPx()), CornerRadius(3f),
                )
            }
            drawCircle(if (dragging && !dead) C.Amber else Color.White, 28.dp.toPx() * (if (dragging) 1f else .85f), Offset(x, size.height / 2f))
        }
        Text(
            if (dragging && !dead) fmt(speed, 1) + " mm/s" else "idle",
            color = C.Dim, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
        )
    }
}

// =====================================================================  SETTINGS
@Composable
private fun SectionTitle(text: String) {
    Text(text, color = C.Dim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp))
}

@Composable
private fun SettingRow(vm: RemoteViewModel, dev: Dev, index: Int, r: SSpec) {
    val v = dev.p(r.key)
    val ms = dev.pi("ms", 10)
    val msIdx = SettingsSpecs.MICROSTEPS.indexOfLast { it <= ms }.coerceAtLeast(0)

    val txt = when (r.key) {
        "ht" -> if (dev.pi("ht", 1) == 1) "ON" else "OFF"
        "iv" -> if (dev.pi("iv", 1) == 1) "Reversed" else "Normal"
        "ms" -> "1/$ms"
        else -> fmt(v, 0)
    }
    val canDec = when (r.kind) {
        SKind.Step -> v > r.min + 0.001f
        SKind.Microstep -> msIdx > 0
        SKind.Toggle -> true
    }
    val canInc = when (r.kind) {
        SKind.Step -> v < r.max - 0.001f
        SKind.Microstep -> msIdx < SettingsSpecs.MICROSTEPS.size - 1
        SKind.Toggle -> true
    }
    val hint = when (r.kind) {
        SKind.Step -> "Range ${fmt(r.min, 0)}–${fmt(r.max, 0)} · step ${fmt(r.step, 0)}"
        else -> r.note
    }

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label(r.label)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(txt, color = C.Amber, fontSize = 26.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
                    if (r.unit.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Text(r.unit, color = C.Mid, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
                if (hint.isNotEmpty()) {
                    Text(hint, color = C.Dim, fontSize = 11.sp)
                }
            }
            if (r.kind == SKind.Toggle) {
                Btn("Toggle", { vm.act("row", index * 2 + 1) }, Modifier.width(120.dp), Kind.Amber, height = 46.dp)
            } else {
                Btn("−", { vm.act("row", index * 2) }, Modifier.width(64.dp), Kind.Normal, enabled = canDec, height = 46.dp)
                Spacer(Modifier.width(8.dp))
                Btn("+", { vm.act("row", index * 2 + 1) }, Modifier.width(64.dp), Kind.Amber, enabled = canInc, height = 46.dp)
            }
        }
    }
}

@Composable
fun SettingsUi(vm: RemoteViewModel, dev: Dev) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("MOTOR & MOTION")
        SettingsSpecs.ROWS.forEachIndexed { i, r -> SettingRow(vm, dev, i, r) }

        Panel {
            Label("Derived")
            Spacer(Modifier.height(4.dp))
            KeyValue("Steps / mm", fmt(dev.par.f("smm"), 2))
            KeyValue("Steps / shaft rev", fmt(dev.par.f("spr"), 0))
            KeyValue("Driver pulses / motor rev", (200 * dev.pi("ms", 10)).toString())
        }

        SectionTitle("CONNECTION")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label("Bluetooth")
                    Text("This app is connected", color = C.Text, fontSize = 15.sp)
                }
                StatusPill("CONNECTED", C.Green)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Bluetooth is switched on or off only on the machine (Settings → Bluetooth). " +
                    "Turning it off there disconnects this app.",
                color = C.Dim, fontSize = 12.sp,
            )
        }

        SectionTitle("TOUCH SCREEN")
        Panel {
            Label("Touch calibration")
            Spacer(Modifier.height(4.dp))
            Text(
                "Calibration is done on the machine's own screen only: Settings → CALIBRATE TOUCH. " +
                    "Stop the motor first.",
                color = C.Mid, fontSize = 13.sp,
            )
        }

        SectionTitle("ABOUT")
        Panel {
            KeyValue("Machine", vm.deviceName)
            KeyValue("Machine firmware", vm.fwVersion.ifEmpty { "—" })
            KeyValue("App version", APP_VERSION)
        }

        Text("Changes are saved on the machine automatically.", color = C.Dim, fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

// =====================================================================  DONE / CONFLICT / WELCOME
@Composable
fun DoneUi(vm: RemoteViewModel, dev: Dev) {
    val mode = modeLabel(vm.lastMode) ?: "MOTION"
    Panel(accent = C.Green.copy(alpha = 0.6f)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✓", color = C.Green, fontSize = 64.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("$mode COMPLETE", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("FINAL POSITION ${fmt(dev.posMm, 1)} MM", color = C.Mid, fontSize = 14.sp, fontFamily = Mono)
            Spacer(Modifier.height(10.dp))
            Text("Restart to run it again, or go back to the menu.", color = C.Dim, fontSize = 13.sp,
                textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ConflictUi(dev: Dev) {
    val name = modeLabel(dev.activeProgress)
    Panel(accent = C.Red.copy(alpha = 0.6f)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("!", color = C.Red, fontSize = 52.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("UNFINISHED PROFILE", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center)
            if (name != null) {
                Spacer(Modifier.height(8.dp))
                StatusPill(name, C.Amber)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "A profile is still running or waiting for its next repeat. " +
                    "Manual position changes are blocked until you choose:",
                color = C.Mid, fontSize = 14.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text("CONTINUE previous profile — nothing changes, back to Home.", color = C.Green, fontSize = 13.sp,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text("CANCEL previous — stops the motor, cancels the profile and opens the screen you picked.",
                color = C.Red, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun WelcomeUi() {
    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Machine is starting…", color = C.Mid, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text("Waiting for homing to finish.", color = C.Dim, fontSize = 13.sp)
    }
}
