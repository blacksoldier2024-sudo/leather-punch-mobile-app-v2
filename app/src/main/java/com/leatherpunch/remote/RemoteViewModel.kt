package com.leatherpunch.remote

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.leatherpunch.remote.ble.BleClient
import com.leatherpunch.remote.ble.LinkState

class RemoteViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("remote", Context.MODE_PRIVATE)

    var link by mutableStateOf(LinkState.Idle); private set
    var dev by mutableStateOf(Dev()); private set
    var deviceName by mutableStateOf("PunchRemote"); private set
    var pin by mutableStateOf(prefs.getString("pin", "1234") ?: "1234")
    var lastFrameMs by mutableStateOf(0L); private set

    /** نسخه‌ی فریمور دستگاه (از پاسخ K|1|<fw> بعد از تأیید PIN) */
    var fwVersion by mutableStateOf(""); private set

    /** آخرین صفحه‌ی پروفایل/مانوال که دستگاه در آن بود؛ برای عنوان صفحه‌ی Done («RATE COMPLETE» و...) */
    var lastMode by mutableStateOf(-1); private set

    // مقدار خوش‌بینانه: بین لمس کاربر و تأیید دستگاه همین مقدار نمایش داده می‌شود
    // (و برای دکمه‌های نگه‌داشتنی پایه‌ی محاسبه‌ی گام بعدی است).
    private val optimistic = mutableStateMapOf<String, Pair<Float, Long>>()

    private val client = BleClient(
        app,
        onState = { s ->
            link = s
            if (s == LinkState.Lost || s == LinkState.Idle || s == LinkState.BadPin) {
                dev = Dev()
            }
        },
        onFrame = { line -> onFrame(line) },
        onName = { deviceName = it },
    )

    val bluetoothOn get() = client.isBluetoothOn

    fun connect() {
        prefs.edit().putString("pin", pin).apply()
        client.startScan(pin)
    }

    fun disconnect() = client.disconnect()

    private fun onFrame(line: String) {
        val (type, m) = parseFrame(line)
        lastFrameMs = System.currentTimeMillis()
        when (type) {
            "K" -> fwVersion = m["#2"] ?: fwVersion
            "L" -> {
                dev = dev.copy(live = m)
                val scr = m.i("s", -1)
                if (modeLabel(scr) != null) lastMode = scr
            }
            "R" -> dev = dev.copy(rt = m)
            "P1", "P2", "P3" -> {
                dev = dev.copy(par = dev.par + m)
                val now = System.currentTimeMillis()
                for ((k, v) in m) {
                    val o = optimistic[k] ?: continue
                    val dv = v.toFloatOrNull() ?: continue
                    if (kotlin.math.abs(dv - o.first) < 0.001f || now - o.second > 1500) optimistic.remove(k)
                }
            }
        }
    }

    // ------------------------------------------------------------ commands
    val connected get() = link == LinkState.Ready

    /** عملیات روی صفحه‌ی فعلی. شماره‌ی صفحه همراه فرمان می‌رود تا فرمان کهنه اجرا نشود. */
    fun act(name: String, arg: Int = 0, screen: Int = dev.screen) {
        if (!connected || screen < 0) return
        client.send("A|$screen|$name|$arg")
    }

    fun joy(arg: Int) {
        if (!connected) return
        client.send("A|${dev.screen}|joy|$arg", dropIfBusy = true)
    }

    fun joyRelease() {
        if (!connected) return
        client.send("A|${dev.screen}|joyrel|0")
    }

    fun stop() {
        // STOP بدون شماره‌ی صفحه و جلوی صف
        client.send("X|stop")
    }

    fun back() = act("back")
    fun right() = act("right")

    /** مقدار مؤثر یک پارامتر: لمس اخیر کاربر، وگرنه مقدار دستگاه */
    fun eff(key: String, d: Float = 0f): Float {
        val o = optimistic[key]
        if (o != null && System.currentTimeMillis() - o.second < 1500) return o.first
        return if (key == "ppm") dev.snapPpm.toFloat() else dev.p(key, d)
    }

    private fun round(v: Float, dec: Int): Float {
        var m = 1f
        repeat(dec) { m *= 10f }
        return Math.round(v * m) / m
    }

    /** مقدار مطلق (اسلایدر / دکمه) */
    fun setAbs(spec: PSpec, value: Float) {
        if (!connected) return
        val nv = round(value.coerceIn(spec.min, spec.max), spec.decimals)
        optimistic[spec.key] = nv to System.currentTimeMillis()
        if (spec.key == "ppm") {
            act("ppm", nv.toInt())
        } else {
            val txt = if (spec.decimals == 0) nv.toInt().toString()
                      else String.format(java.util.Locale.US, "%.${spec.decimals}f", nv)
            client.send("S|${dev.screen}|${spec.key}|$txt")
        }
    }

    /** گام نسبی (دکمه‌های -/+) */
    fun adjust(spec: PSpec, delta: Float) {
        val base = eff(spec.key, spec.min)
        setAbs(spec, base + delta)
    }
}
