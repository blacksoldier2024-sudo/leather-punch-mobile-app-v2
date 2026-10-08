package com.leatherpunch.remote

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.leatherpunch.remote.ui.AppTheme
import com.leatherpunch.remote.ui.MainUi

class MainActivity : ComponentActivity() {

    private val vm: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // وقتی ماشین در حال کار است صفحه خاموش نشود
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            AppTheme {
                val perms = if (Build.VERSION.SDK_INT >= 31)
                    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { result ->
                    if (result.values.all { it }) vm.connect()
                }

                MainUi(vm) {
                    val ok = perms.all {
                        ContextCompat.checkSelfPermission(this@MainActivity, it) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    if (ok) vm.connect() else launcher.launch(perms)
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) vm.disconnect()
        super.onDestroy()
    }
}
