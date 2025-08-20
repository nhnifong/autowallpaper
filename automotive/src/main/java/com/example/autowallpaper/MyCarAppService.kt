package com.example.autowallpaper

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * This is the main entry point for the Android Auto application.
 *
 * Android Auto discovers this service and binds to it to start the app.
 */
class MyCarAppService : CarAppService() {

    /**
     * Creates a host validator that allows connections from any host.
     * For production apps, you should restrict this to known hosts like Android Auto.
     */
    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    /**
     * Called by the system when a new connection to the car is established.
     * @return A new Session instance for this connection.
     */
    override fun onCreateSession(): Session {
        return MainSession()
    }
}
