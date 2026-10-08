package com.leatherpunch.remote.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.leatherpunch.remote.Ble
import java.util.ArrayDeque

enum class LinkState { Idle, Scanning, Connecting, Authenticating, Ready, BadPin, Lost }

/**
 * لایه‌ی کم‌سطح BLE: اسکن، اتصال، MTU، فعال‌سازی notify، و ارسال ترتیبی فرمان‌ها.
 * همه‌ی callbackها روی thread خود اندروید می‌آیند و با Handler اصلی به UI می‌رسند.
 */
@SuppressLint("MissingPermission")
class BleClient(
    private val ctx: Context,
    private val onState: (LinkState) -> Unit,
    private val onFrame: (String) -> Unit,
    private val onName: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val mgr = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = mgr.adapter

    private var gatt: BluetoothGatt? = null
    private var cmdChar: BluetoothGattCharacteristic? = null
    private var scanner: BluetoothLeScanner? = null
    private var scanning = false
    private var scanGen = 0

    private val queue = ArrayDeque<ByteArray>()
    private var writing = false
    private var pin = ""
    private var wantReconnect = false
    private var lastAddress: String? = null
    private var rx = StringBuilder()

    val isBluetoothOn get() = adapter?.isEnabled == true

    // ----------------------------------------------------- scan
    fun startScan(pin: String) {
        this.pin = pin
        wantReconnect = true
        val sc = adapter?.bluetoothLeScanner ?: return
        scanner = sc
        if (scanning) return
        scanning = true
        val gen = ++scanGen
        post(LinkState.Scanning)
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(Ble.SERVICE)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        sc.startScan(listOf(filter), settings, scanCb)
        main.postDelayed({
            if (scanning && gen == scanGen) {
                stopScan()
                post(LinkState.Lost)
                if (wantReconnect) main.postDelayed({ if (wantReconnect) startScan(pin) }, 1000)
            }
        }, 15000)
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        try { scanner?.stopScan(scanCb) } catch (_: Exception) {}
    }

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(type: Int, r: ScanResult) {
            if (!scanning) return
            stopScan()
            connect(r.device)
        }
        override fun onScanFailed(code: Int) {
            scanning = false
            post(LinkState.Lost)
        }
    }

    // ----------------------------------------------------- connect
    private fun connect(dev: BluetoothDevice) {
        post(LinkState.Connecting)
        lastAddress = dev.address
        onNameMain(dev.name ?: "PunchRemote")
        gatt = if (Build.VERSION.SDK_INT >= 23)
            dev.connectGatt(ctx, false, gattCb, BluetoothDevice.TRANSPORT_LE)
        else dev.connectGatt(ctx, false, gattCb)
    }

    fun disconnect() {
        wantReconnect = false
        stopScan()
        synchronized(queue) { queue.clear(); writing = false }
        try { gatt?.disconnect() } catch (_: Exception) {}
        try { gatt?.close() } catch (_: Exception) {}
        gatt = null
        cmdChar = null
        post(LinkState.Idle)
    }

    private val gattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                g.requestMtu(247)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                try { g.close() } catch (_: Exception) {}
                if (gatt === g) gatt = null
                cmdChar = null
                synchronized(queue) { queue.clear(); writing = false }
                post(LinkState.Lost)
                if (wantReconnect) main.postDelayed({ if (wantReconnect) startScan(pin) }, 1200)
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val svc = g.getService(Ble.SERVICE)
            val st = svc?.getCharacteristic(Ble.STATE)
            cmdChar = svc?.getCharacteristic(Ble.CMD)
            if (st == null || cmdChar == null) { g.disconnect(); return }
            g.setCharacteristicNotification(st, true)
            val d = st.getDescriptor(Ble.CCCD)
            d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            g.writeDescriptor(d)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            post(LinkState.Authenticating)
            sendRaw("H|$pin|${Ble.PROTO}", front = true)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            synchronized(queue) { writing = false }
            pump()
        }

        // API < 33
        @Deprecated("old signature")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            handleBytes(c.value ?: return)
        }

        // API >= 33
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            handleBytes(value)
        }
    }

    private fun handleBytes(b: ByteArray) {
        val s = String(b, Charsets.UTF_8)
        main.post {
            if (s.startsWith("K|")) {
                if (s.startsWith("K|0")) post(LinkState.BadPin) else post(LinkState.Ready)
            }
            onFrame(s)
        }
    }

    // ----------------------------------------------------- send
    /** فرمان معمولی؛ فرمان‌های کنترلی (joy) با dropIfBusy از صف عقب نمی‌مانند. */
    fun send(cmd: String, dropIfBusy: Boolean = false) {
        if (cmdChar == null) return
        if (dropIfBusy) synchronized(queue) { if (writing || queue.size > 2) return }
        sendRaw(cmd, false)
    }

    private fun sendRaw(cmd: String, front: Boolean) {
        val b = cmd.toByteArray(Charsets.UTF_8)
        synchronized(queue) { if (front) queue.addFirst(b) else queue.addLast(b) }
        pump()
    }

    private fun pump() {
        val g = gatt ?: return
        val c = cmdChar ?: return
        val next: ByteArray
        synchronized(queue) {
            if (writing) return
            next = queue.pollFirst() ?: return
            writing = true
        }
        c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val ok = if (Build.VERSION.SDK_INT >= 33)
            g.writeCharacteristic(c, next, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        else {
            c.value = next
            g.writeCharacteristic(c)
        }
        if (!ok) synchronized(queue) { writing = false }
    }

    private fun post(s: LinkState) = main.post { onState(s) }
    private fun onNameMain(n: String) = main.post { onName(n) }
}
