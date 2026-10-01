package com.bitperfect.player.sdk.plugin.runtime

import android.os.IBinder
import com.bitperfect.player.sdk.plugin.IPluginHostCallbackV1

internal class PluginHostCallbackRegistry(
    private val onChanged: (connected: Boolean) -> Unit = {},
) {
    @Volatile
    private var registration: Registration? = null

    val callback: IPluginHostCallbackV1?
        get() = registration?.callback

    @Synchronized
    fun register(callback: IPluginHostCallbackV1) {
        val next = Registration(callback)
        next.deathRecipient = IBinder.DeathRecipient { clearIfCurrent(next) }
        next.binder.linkToDeath(next.deathRecipient, 0)
        val previous = registration
        registration = next
        previous?.binder?.unlinkToDeath(previous.deathRecipient, 0)
        onChanged(true)
    }

    @Synchronized
    fun clear(callback: IPluginHostCallbackV1) {
        val current = registration ?: return
        if (current.binder != callback.asBinder()) return
        registration = null
        current.binder.unlinkToDeath(current.deathRecipient, 0)
        onChanged(false)
    }

    @Synchronized
    private fun clearIfCurrent(candidate: Registration) {
        if (registration !== candidate) return
        registration = null
        candidate.binder.unlinkToDeath(candidate.deathRecipient, 0)
        onChanged(false)
    }

    private class Registration(val callback: IPluginHostCallbackV1) {
        val binder: IBinder = callback.asBinder()
        lateinit var deathRecipient: IBinder.DeathRecipient
    }
}
