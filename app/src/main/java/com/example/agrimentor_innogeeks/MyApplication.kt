package com.example.agrimentor_innogeeks

import android.app.Application
import com.google.firebase.FirebaseApp

// In your Application class (create one if it doesn't exist)
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}