package com.leatherpunch.remote

import java.util.UUID
import kotlin.math.abs

/** UUIDها باید دقیقاً با RemoteLink.cpp یکی باشند. */
object Ble {
    val SERVICE: UUID = UUID.fromString("6e5a1000-b5a3-4f43-8c21-9a1b7d2e0001")
    val STATE: UUID = UUID.fromString("6e5a1001-b5a3-4f43-8c21-9a1b7d2e0001")
    val CMD: UUID = UUID.fromString("6e5a1002-b5a3-4f43-8c21-9a1b7d2e0001")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    const val PROTO = 1
}

/** ترتیب دقیقاً مثل enum class ScreenId در ScreenManager.h */
object Scr {
    const val WELCOME = 0
    const val HOME = 1
    const val RATE = 2
    const val INTERVAL = 3
    const val INTERVAL_RUN = 4
    const val HOLD = 5
    const val COUNT = 6
    const val COUNT_RUN = 7
    const val STEP = 8
    const val STEP2 = 9
    const val STEP_RUN = 10
    const val REPEAT = 11
    const val REPEAT2 = 12
    const val REPEAT_RUN = 13
    const val DEPTH = 14
    const val DEPTH_RUN = 15
    const val FULL = 16
    const val FULL2 = 17
    const val FULL3 = 18
    const val FULL_RUN = 19
    const val MANUAL = 20
    const val DONE = 21
    const val SETTINGS = 22
    const val CONFLICT = 23
}

fun Map<String, String>.i(k: String, d: Int = 0): Int = this[k]?.toIntOrNull() ?: d
fun Map<String, String>.f(k: String, d: Float = 0f): Float = this[k]?.toFloatOrNull() ?: d
fun Map<String, String>.l(k: String, d: Long = 0L): Long = this[k]?.toLongOrNull() ?: d

/** تصویر لحظه‌ای دستگاه؛ هر فریم BLE یکی از این سه map را عوض می‌کند. */
data class Dev(
    val live: Map<String, String> = emptyMap(),
    val rt: Map<String, String> = emptyMap(),
    val par: Map<String, String> = emptyMap(),
) {
    val hasLive get() = live.isNotEmpty()
    val screen get() = live.i("s", -1)
    val posMm get() = live.f("p")
    val motorRunning get() = live.i("r") == 1
    val step get() = live.l("st")
    val pct get() = live.i("pc")
    val homeSw get() = live.i("hs") == 1
    val endSw get() = live.i("es") == 1
    val activeProgress get() = live.i("ap", -1)

    // وضعیت اختصاصی Rate / Hold
    val snapPpm get() = live.i("sa")
    val snapRun get() = live.i("sr") == 1
    val snapFlag get() = live.i("sf") == 1
    val snapCount get() = live.l("sc")

    val courseMm get() = par.f("cl", 30f).coerceAtLeast(1f)
    val maxSpeed get() = par.f("mx", 40f)
    val stepsPerRev get() = par.f("spr", 19200f).coerceAtLeast(1f)

    /** 0 = بالا (Home) ... 1 = پایین‌ترین نقطه */
    val depthFrac get() = (posMm / courseMm).coerceIn(0f, 1f)

    /** کسر دور شفت، 0..1 */
    val shaftFrac: Float
        get() {
            val t = abs(step.toFloat() / stepsPerRev)
            return t - kotlin.math.floor(t)
        }

    fun p(key: String, d: Float = 0f) = par.f(key, d)
    fun pi(key: String, d: Int = 0) = par.i(key, d)
}

/** یک فریم متنی را به (نوع، map) تبدیل می‌کند. مثال: "L|s=1|p=0.0" */
fun parseFrame(line: String): Pair<String, Map<String, String>> {
    val parts = line.trim().split('|')
    if (parts.isEmpty()) return "" to emptyMap()
    val m = HashMap<String, String>(parts.size * 2)
    for (k in 1 until parts.size) {
        val e = parts[k]
        val eq = e.indexOf('=')
        if (eq > 0) m[e.substring(0, eq)] = e.substring(eq + 1)
        else if (e.isNotEmpty()) m["#$k"] = e
    }
    return parts[0] to m
}

/** مشخصات یک پارامتر قابل‌تنظیم (برای کارت‌های تنظیم). */
data class PSpec(
    val key: String,
    val label: String,
    val unit: String,
    val min: Float,
    val max: Float,
    val decimals: Int,
    val quick: List<Float>,
    val slider: Boolean = false,
)

object Specs {
    val COUNT_Q = listOf(-100f, -10f, -1f, 1f, 10f, 100f)
    val FINE_Q = listOf(-1f, -0.1f, 0.1f, 1f)
    val SPEED_Q = listOf(-5f, -1f, 1f, 5f)

    val IV_COUNT = PSpec("ic", "Punch count", "punches", 1f, 9999f, 0, COUNT_Q)
    val IV_MIN = PSpec("im", "Dwell minutes", "min", 0f, 99f, 0, listOf(-10f, -1f, 1f, 10f))
    val IV_SEC = PSpec("is", "Dwell seconds", "sec", 0f, 59f, 0, listOf(-10f, -1f, 1f, 10f))
    val COUNT = PSpec("cn", "Punch count", "punches", 1f, 9999f, 0, COUNT_Q)

    val STEP_THICK = PSpec("sT", "Leather thickness", "mm", 1f, 20f, 1, FINE_Q, true)
    val STEP_N = PSpec("sN", "Steps per punch", "steps", 1f, 30f, 0, listOf(-5f, -1f, 1f, 5f))
    val STEP_COUNT = PSpec("sP", "Punch count", "punches", 1f, 9999f, 0, COUNT_Q)
    val STEP_PPM = PSpec("sM", "Speed", "PPM", 1f, 60f, 0, SPEED_Q, true)

    val REP_THICK = PSpec("rT", "Leather thickness", "mm", 1f, 20f, 1, FINE_Q, true)
    val REP_N = PSpec("rN", "Repeats per punch", "repeats", 1f, 30f, 0, listOf(-5f, -1f, 1f, 5f))
    val REP_COUNT = PSpec("rP", "Punch count", "punches", 1f, 9999f, 0, COUNT_Q)
    val REP_PPM = PSpec("rM", "Speed", "PPM", 1f, 60f, 0, SPEED_Q, true)

    val DEPTH = PSpec("dD", "Depth", "mm", 0f, 30f, 1, FINE_Q, true)
    val DEPTH_COUNT = PSpec("dP", "Punch count", "punches", 1f, 9999f, 0, COUNT_Q)
    val DEPTH_PPM = PSpec("dM", "Speed", "PPM", 1f, 60f, 0, SPEED_Q, true)

    val FULL_THICK = PSpec("fT", "Leather thickness", "mm", 0f, 20f, 1, FINE_Q, true)
    val FULL_BOTTOM = PSpec("fB", "C · Hold at bottom", "sec", 0f, 5f, 1, FINE_Q, true)
    val FULL_REF = PSpec("fR", "E · Hold at home", "sec", 0f, 60f, 1, listOf(-1f, -0.1f, 0.1f, 1f), true)
    val FULL_DESC = PSpec("fD", "A · Down to leather edge", "sec", 0f, 15f, 1, FINE_Q, true)
    val FULL_CUT = PSpec("fC", "B · Cutting", "sec", 0f, 15f, 1, FINE_Q, true)
    val FULL_ASC = PSpec("fA", "D · Return home", "sec", 0f, 15f, 1, FINE_Q, true)
    val FULL_COUNT = PSpec("fP", "Punch count", "punches", 1f, 9999f, 0, COUNT_Q)
    val FULL_HOME = PSpec("fH", "Homing speed", "%", 10f, 100f, 0, SPEED_Q, true)

    val RATE_PPM = PSpec("ppm", "Speed", "PPM", 1f, 60f, 0, SPEED_Q, true)
}

const val APP_VERSION = "2.0"

/**
 * نام پروفایل مثل labelForScreen() در MotionDoneScreen.cpp.
 * برای صفحه‌ی Done / Conflict استفاده می‌شود؛ برای صفحه‌های غیرپروفایل null.
 */
fun modeLabel(scr: Int): String? = when (scr) {
    Scr.RATE -> "RATE"
    Scr.INTERVAL, Scr.INTERVAL_RUN -> "INTERVAL"
    Scr.HOLD -> "HOLD"
    Scr.COUNT, Scr.COUNT_RUN -> "COUNT"
    Scr.STEP, Scr.STEP2, Scr.STEP_RUN -> "STEP"
    Scr.REPEAT, Scr.REPEAT2, Scr.REPEAT_RUN -> "REPEAT"
    Scr.DEPTH, Scr.DEPTH_RUN -> "DEPTH"
    Scr.FULL, Scr.FULL2, Scr.FULL3, Scr.FULL_RUN -> "FULL ABC"
    Scr.MANUAL -> "MANUAL JOG"
    else -> null
}

enum class SKind { Toggle, Step, Microstep }

/** یک ردیف صفحه‌ی Settings دستگاه. ترتیب لیست = شماره‌ی ردیف در SettingsScreen.cpp (ردیف ۷ = بلوتوث، از اپ قابل تغییر نیست). */
data class SSpec(
    val key: String,
    val label: String,
    val unit: String,
    val kind: SKind,
    val min: Float = 0f,
    val max: Float = 0f,
    val step: Float = 0f,
    val note: String = "",
)

object SettingsSpecs {
    /** لیست ریزگام‌های مجاز؛ + و − روی دستگاه یک خانه در همین لیست جلو/عقب می‌روند. */
    val MICROSTEPS = listOf(1, 2, 4, 5, 8, 10, 16, 20, 25, 32)

    val ROWS = listOf(
        SSpec("ht", "Hold torque", "", SKind.Toggle, note = "Keep the motor energised when idle"),
        SSpec("rm", "Ramp", "%", SKind.Step, 2f, 25f, 1f),
        SSpec("ac", "Jog accel", "steps/s²", SKind.Step, 1000f, 50000f, 1000f),
        SSpec("mx", "Max speed", "mm/s", SKind.Step, 5f, 120f, 5f),
        SSpec("ms", "Microstep", "", SKind.Microstep, note = "1, 2, 4, 5, 8, 10, 16, 20, 25, 32"),
        SSpec("iv", "Direction", "", SKind.Toggle, note = "Reverse the motor direction"),
        SSpec("cl", "Course", "mm", SKind.Step, 10f, 50f, 1f),
    )
}
