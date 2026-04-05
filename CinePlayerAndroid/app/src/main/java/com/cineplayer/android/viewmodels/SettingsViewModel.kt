package com.cineplayer.android.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.cineplayer.android.data.AppSettings

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    val settings = AppSettings.getInstance(application)
}
