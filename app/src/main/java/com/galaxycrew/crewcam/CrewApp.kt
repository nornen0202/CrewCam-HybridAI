package com.galaxycrew.crewcam

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck

class CrewApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val firebase = FirebaseApp.initializeApp(this) ?: return
        FirebaseAppCheck.getInstance(firebase).installAppCheckProviderFactory(appCheckFactory())
    }
}
