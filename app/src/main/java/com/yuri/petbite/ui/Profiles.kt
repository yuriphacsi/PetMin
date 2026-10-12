package com.yuri.petbite.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import com.yuri.petbite.data.PetProfile
import com.yuri.petbite.data.ProfileStore

@Composable fun ProfilePanel() {
    val context = LocalContext.current
    val store = remember { ProfileStore(context) }
    var profiles by remember { mutableStateOf(store.read()) }
    var editing by remember { mutableStateOf<PetProfile?>(null) }
    var show by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Perfiles de mascotas", style = MaterialTheme.typography.titleLarge)
        profiles.forEach { p ->
            Card { Column(Modifier.padding(12.dp)) {
                Text("${p.name} · ${p.species}")
                Text("Edad: ${p.age.ifBlank { "sin indicar" }} meses · Peso: ${p.weight.ifBlank { "sin indicar" }} kg")
                Text(p.food)
                if(p.photo.isNotBlank()) {
                    val bitmap by produceState<android.graphics.Bitmap?>(null, p.photo) {
                        value = withContext(Dispatchers.IO) { runCatching {
                            context.contentResolver.openInputStream(Uri.parse(p.photo))?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 8 }) }
                        }.getOrNull() }
                    }
                    bitmap?.let { Image(it.asImageBitmap(), "Foto de ${p.name}", Modifier.size(96.dp)) }
                }
                TextButton(onClick = { editing = p; show = true }) { Text("Editar perfil") }
            } }
        }
        OutlinedButton(onClick = { editing = null; show = true }) { Text("Registrar mascota") }
    }
    if(show) {
        var name by remember { mutableStateOf(editing?.name ?: "") }
        var species by remember { mutableStateOf(editing?.species ?: "Perro") }
        var age by remember { mutableStateOf(editing?.age ?: "") }
        var weight by remember { mutableStateOf(editing?.weight ?: "") }
        var food by remember { mutableStateOf(editing?.food ?: "") }
        var photo by remember { mutableStateOf(editing?.photo ?: "") }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if(uri != null) { runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }.onSuccess { photo = uri.toString() } }
        }
        val valid = name.isNotBlank() && (age.isBlank() || age.toIntOrNull()?.let { it in 0..600 } == true) && (weight.isBlank() || weight.toDoubleOrNull()?.let { it.isFinite() && it > 0 && it <= 200 } == true)
        AlertDialog(onDismissRequest = { show = false }, title = { Text("Perfil") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Nombre") })
                Row { listOf("Perro", "Gato").forEach { s -> FilterChip(species == s, { species = s }, label = { Text(s) }) } }
                OutlinedTextField(age, { age = it.take(3) }, label = { Text("Edad aproximada en meses") })
                OutlinedTextField(weight, { weight = it.replace(',', '.').take(6) }, label = { Text("Peso en kg (opcional)") })
                OutlinedTextField(food, { food = it.take(160) }, label = { Text("Preferencias de alimentación") })
                TextButton(onClick = { picker.launch(arrayOf("image/*")) }) { Text(if(photo.isBlank()) "Elegir foto" else "Cambiar foto") }
                Text("Las porciones las configura el propietario; estos datos no calculan una dieta.")
            }
        }, confirmButton = { TextButton(enabled = valid, onClick = {
            val p = PetProfile(editing?.id ?: UUID.randomUUID().toString(), name.trim(), species, age, weight, food, photo)
            profiles = profiles.filterNot { it.id == p.id } + p; store.save(profiles); show = false
        }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = { show = false }) { Text("Cancelar") } })
    }
}
@Composable fun CalibrationGuide() {
    Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Calibración HX711", style = MaterialTheme.typography.titleMedium)
        Text("1. Deshabilita el actuador en Config.h y monta la tolva vacía.\n2. Anota el promedio raw del monitor serie como ZERO.\n3. Añade una masa conocida y calcula (raw con masa − ZERO) / gramos.\n4. Guarda el factor en Config.h, activa HX711_CALIBRATED y carga el firmware.\n5. Comprueba varias masas antes de habilitar el servo.")
        Text("La calibración se realiza por USB, con el mecanismo supervisado. Capacidad configurada: 2000 g; no es el límite de 10 kg de la celda.")
    } }
}
