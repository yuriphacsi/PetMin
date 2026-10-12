package com.yuri.petbite.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// PETMIN NOTA: perfiles y juego usan almacenes distintos del historial Room.
data class PetProfile(val id: String, val name: String, val species: String, val age: String, val weight: String, val food: String, val photo: String)
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("petmin_profiles", Context.MODE_PRIVATE)
    fun read(): List<PetProfile> = runCatching {
        val a = JSONArray(prefs.getString("profiles", "[]"))
        (0 until a.length()).map { val o = a.getJSONObject(it); PetProfile(o.getString("id"), o.getString("name"), o.getString("species"), o.optString("age"), o.optString("weight"), o.optString("food"), o.optString("photo")) }
    }.getOrDefault(emptyList())
    fun save(values: List<PetProfile>) { val a = JSONArray(); values.forEach { a.put(JSONObject().put("id",it.id).put("name",it.name).put("species",it.species).put("age",it.age).put("weight",it.weight).put("food",it.food).put("photo",it.photo)) }; prefs.edit().putString("profiles", a.toString()).apply() }
}
