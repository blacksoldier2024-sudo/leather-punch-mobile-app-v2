package com.leatherpunch.remote.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leatherpunch.remote.*
import com.leatherpunch.remote.ble.LinkState
import kotlinx.coroutines.delay

@Composable
fun MainUi(vm: RemoteViewModel, onConnect: () -> Unit) {
    Box(Modifier.fillMaxSize().background(C.Bg).systemBarsPadding()) {
        if (vm.link != LinkState.Ready) {
            ConnectUi(vm, onConnect)
        } else {
            ControlUi(vm)
        }
    }
}

@Composable
private fun ControlUi(vm: RemoteViewModel) {
    val dev = vm.dev
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); now = System.currentTimeMillis() }
    }
    val stale = vm.lastFrameMs != 0L && now - vm.lastFrameMs > 4000

    Column(Modifier.fillMaxSize()) {
        TopBar(vm, dev)
        if (stale) {
            Box(Modifier.fillMaxWidth().background(C.RedDim).padding(8.dp), contentAlignment = Alignment.Center) {
                Text("No data from the machine — check the connection", color = C.Red, fontSize = 13.sp)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!dev.hasLive) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Syncing with the machine…", color = C.Mid, fontSize = 16.sp)
                }
            } else {
                Crossfade(targetState = dev.screen, animationSpec = tween(140), label = "screen") { scr ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        ScreenBody(vm, vm.dev, scr)
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
        BottomBar(vm, dev)
    }
}

@Composable
private fun ScreenBody(vm: RemoteViewModel, dev: Dev, scr: Int) {
    when (scr) {
        Scr.WELCOME -> WelcomeUi()
        Scr.HOME -> HomeUi(vm, dev)
        Scr.RATE -> RateUi(vm, dev)
        Scr.HOLD -> HoldUi(vm, dev)
        Scr.INTERVAL, Scr.COUNT, Scr.STEP, Scr.STEP2, Scr.REPEAT, Scr.REPEAT2,
        Scr.DEPTH, Scr.FULL, Scr.FULL2, Scr.FULL3 -> SetupUi(vm, dev, scr)
        Scr.INTERVAL_RUN, Scr.COUNT_RUN, Scr.STEP_RUN, Scr.REPEAT_RUN,
        Scr.DEPTH_RUN, Scr.FULL_RUN -> RunUi(vm, dev, scr)
        Scr.MANUAL -> ManualUi(vm, dev)
        Scr.SETTINGS -> SettingsUi(vm, dev)
        Scr.DONE -> DoneUi(vm, dev)
        Scr.CONFLICT -> ConflictUi(dev)
        else -> WelcomeUi()
    }
}

@Composable
private fun TopBar(vm: RemoteViewModel, dev: Dev) {
    val (title, sub) = screenTitle(dev.screen)
    val moving = dev.motorRunning
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = C.Mid, fontSize = 13.sp)
        }
        if (dev.hasLive) {
            StatusPill(if (moving) "MOVING" else "IDLE", if (moving) C.Green else C.Mid)
            Spacer(Modifier.width(10.dp))
            Text(fmt(dev.posMm, 1) + " mm", color = C.Amber, fontSize = 16.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BottomBar(vm: RemoteViewModel, dev: Dev) {
    val scr = dev.screen
    val back: String? = when (scr) {
        Scr.WELCOME -> null
        Scr.HOME -> "Settings"
        Scr.DONE -> "Menu"
        Scr.CONFLICT -> "Continue previous"
        else -> "Back"
    }
    val info = setupInfo(vm, dev, scr)
    val primary: String? = when {
        info != null -> info.primary
        scr == Scr.DONE -> "Restart"
        scr == Scr.CONFLICT -> "Cancel previous"
        else -> null
    }
    val primaryKind = when {
        scr == Scr.CONFLICT -> Kind.Danger
        info?.primary == "START" || scr == Scr.DONE -> Kind.Go
        else -> Kind.Amber
    }
    val showStop = scr != Scr.SETTINGS && scr != Scr.CONFLICT

    Row(
        Modifier.fillMaxWidth().background(C.Panel).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (back != null) {
            Btn(back, { vm.back() }, Modifier.weight(1f), Kind.Normal, height = 56.dp, fontSize = 15.sp,
                enabled = !(scr == Scr.HOME && dev.motorRunning),
                icon = { if (scr != Scr.HOME) IconBack(it) })
        }
        if (primary != null) {
            Btn(primary, { vm.right() }, Modifier.weight(1.2f), primaryKind, height = 56.dp, fontSize = 15.sp,
                icon = { if (primaryKind == Kind.Go) IconPlay(it) else if (primaryKind == Kind.Amber) IconNext(it) })
        }
        if (showStop) {
            Btn("STOP", { vm.stop() }, Modifier.weight(1f), Kind.Danger, height = 56.dp, fontSize = 16.sp,
                icon = { IconStop(it) })
        }
    }
}
