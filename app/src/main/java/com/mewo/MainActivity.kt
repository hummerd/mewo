package com.mewo

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mewo.ui.MewoScreen
import com.mewo.ui.MewoTheme
import com.mewo.ui.MewoViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MewoApplication
        setContent {
            val model: MewoViewModel = viewModel(factory = viewModelFactory {
                initializer { MewoViewModel(app.repository, app.settings) }
            })
            val state by model.state.collectAsStateWithLifecycle()
            var notificationsAllowed by remember { mutableStateOf(app.notifications.allowed()) }
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                notificationsAllowed = app.notifications.allowed()
            }
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            DisposableEffect(lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) notificationsAllowed = app.notifications.allowed()
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            MewoTheme {
                MewoScreen(state, notificationsAllowed,
                    onEnableNotifications = {
                        if (Build.VERSION.SDK_INT >= 33 && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) &&
                            getSharedPreferences("permission", MODE_PRIVATE).getBoolean("requested", false)) {
                            openNotificationSettings()
                        } else if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            getSharedPreferences("permission", MODE_PRIVATE).edit().putBoolean("requested", true).apply()
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else { openNotificationSettings() }
                    },
                    onSave = model::save,
                    onRetry = model::load)
            }
        }
    }

    private fun openNotificationSettings() {
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }
}
