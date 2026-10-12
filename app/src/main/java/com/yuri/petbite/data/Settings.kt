package com.yuri.petbite.data

import android.content.Context

data class PetSettings(
    val petName: String = "Mi mascota",
    val address: String = "http://192.168.1.100",
    val token: String = "",
    val syncedSignature: String = "",
    val retentionDays: Int = 30
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("petbite_settings", Context.MODE_PRIVATE)
    fun read() = PetSettings(
        petName = prefs.getString("petName", "Mi mascota")!!,
        address = prefs.getString("address", "http://192.168.1.100")!!,
        token = prefs.getString("token", "")!!,
        syncedSignature = prefs.getString("signature", "")!!,
        retentionDays = prefs.getInt("retentionDays", 30).let { if (it == 7) 7 else 30 }
    )
    fun save(value: PetSettings) {
        prefs.edit().putString("petName", value.petName)
            .putString("address", value.address).putString("token", value.token)
            .putString("signature", value.syncedSignature).putInt("retentionDays", value.retentionDays).apply()
    }
}
