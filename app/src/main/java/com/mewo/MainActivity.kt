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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mewo.reminders.ReminderNotifications
import com.mewo.ui.MewoScreen
import com.mewo.ui.MewoTheme
import com.mewo.ui.MewoViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MewoApplication
        setContent {
            val model: MewoViewModel =
                viewModel(
                    factory =
                        viewModelFactory {
                            initializer { MewoViewModel(app.repository, app.settings) }
                        },
                )
            val state by model.state.collectAsStateWithLifecycle()
            var notificationsAllowed by remember { mutableStateOf(app.notifications.allowed()) }
            val permission =
                rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                    notificationsAllowed = app.notifications.allowed()
                }
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            DisposableEffect(lifecycle) {
                val observer =
                    LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) notificationsAllowed = app.notifications.allowed()
                    }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            MewoTheme {
                MewoScreen(
                    state,
                    notificationsAllowed,
                    onEnableNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) &&
                            getSharedPreferences("permission", MODE_PRIVATE).getBoolean("requested", false)
                        ) {
                            openNotificationSettings()
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            getSharedPreferences("permission", MODE_PRIVATE).edit { putBoolean("requested", true) }
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openNotificationSettings()
                        }
                    },
                    onSave = model::save,
                    onUpdateWord = model::updateWord,
                    onNotificationSound = ::openNotificationSoundSettings,
                    onRetry = model::load,
                    onAbout = { startActivity(Intent(this, AboutActivity::class.java)) },
                )
            }
        }
    }

    private fun openNotificationSoundSettings() {
        startActivity(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, ReminderNotifications.CHANNEL_ID),
        )
    }

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        )
    }
}
