package com.leatherpunch.remote.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leatherpunch.remote.R
import com.leatherpunch.remote.RemoteViewModel
import com.leatherpunch.remote.ble.LinkState

@Composable
fun ConnectUi(vm: RemoteViewModel, onConnect: () -> Unit) {
    val busy = vm.link == LinkState.Scanning || vm.link == LinkState.Connecting || vm.link == LinkState.Authenticating

    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(132.dp).clip(CircleShape).background(C.Bg)) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(20.dp))
        Text("Punch Remote", color = C.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            when (vm.link) {
                LinkState.Idle -> "Not connected"
                LinkState.Scanning -> "Searching for the machine…"
                LinkState.Connecting -> "Connecting to ${vm.deviceName}…"
                LinkState.Authenticating -> "Checking PIN…"
                LinkState.BadPin -> "Wrong PIN. Check REMOTE_PIN in Config.h"
                LinkState.Lost -> "Connection lost. Retrying…"
                LinkState.Ready -> "Syncing…"
            },
            color = if (vm.link == LinkState.BadPin) C.Red else C.Mid,
            fontSize = 15.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = vm.pin,
            onValueChange = { vm.pin = it.filter { ch -> ch.isLetterOrDigit() }.take(12) },
            label = { Text("Device PIN") },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = C.Amber, unfocusedBorderColor = C.Line,
                focusedLabelColor = C.Amber, unfocusedLabelColor = C.Mid,
                focusedTextColor = C.Text, unfocusedTextColor = C.Text,
                cursorColor = C.Amber,
            ),
            modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
        if (busy || vm.link == LinkState.Lost) {
            Btn("Cancel", { vm.disconnect() }, Modifier.widthIn(max = 280.dp).fillMaxWidth(), Kind.Normal)
        } else {
            Btn("Connect", onConnect, Modifier.widthIn(max = 280.dp).fillMaxWidth(), Kind.Amber)
        }
        if (!vm.bluetoothOn) {
            Spacer(Modifier.height(14.dp))
            Text("Bluetooth is off. Turn it on, then tap Connect.", color = C.Red, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}
