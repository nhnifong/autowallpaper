package com.example.autowallpaper

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

/**
 * A Session represents a single user interaction with the app on the car screen.
 * It's responsible for managing the screen stack.
 */
class MainSession : Session() {

    /**
     * Called when the session is first created.
     * @param intent The intent that started the app.
     * @return The first screen to display to the user.
     */
    override fun onCreateScreen(intent: Intent): Screen {
        return ImageDisplayScreen(carContext)
    }
}
