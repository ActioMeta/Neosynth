package com.example.neosynth

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.neosynth.widget.WidgetUpdateManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NeoSynthApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var widgetUpdateManager: WidgetUpdateManager

    override fun onCreate() {
        super.onCreate()
        // Start observing music changes to update widgets
        widgetUpdateManager.startObserving()
    }

    override val workManagerConfiguration: Configuration
        get() {
            Log.d("NeoSynthApp", "WorkManager configuration requested, workerFactory initialized: ${::workerFactory.isInitialized}")
            return Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(Log.DEBUG)
                .build()
        }
}