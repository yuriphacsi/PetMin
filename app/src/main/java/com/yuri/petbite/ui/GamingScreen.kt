
package com.yuri.petbite.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yuri.petbite.gaming.PetScene

private data class Challenge(
    val question: String,
    val options: List<String>,
    val answer: Int,
    val explanation: String
)

// PETMIN FUTURO: Trasladar los desafíos a una base de datos
// para incorporar niveles y contenido educativo adicional.
private val challenges = listOf(
    Challenge(
        "¿Qué eliges para su alimentación habitual?",
        listOf("Alimento completo para su especie y etapa", "Solo premios"),
        0,
        "La base debe ser alimento completo adecuado para perro o gato y su etapa."
    ),
    Challenge(
        "Encuentras chocolate. ¿Qué haces?",
        listOf("Se lo doy como premio", "Lo guardo fuera de su alcance"),
        1,
        "El chocolate puede intoxicar a perros y gatos."
    ),
    Challenge(
        "¿Compartimos comida con cebolla o ajo?",
        listOf("No, la apartamos", "Sí, si está cocinada"),
        0,
        "La cebolla y el ajo pueden dañar los glóbulos rojos."
    ),
    Challenge(
        "¿Qué hago con uvas y pasas?",
        listOf("Las uso como premio", "Las mantengo fuera de su alcance"),
        1,
        "Las uvas y pasas pueden causar daño renal en perros."
    ),
    Challenge(
        "¿Cómo decido los gramos diarios?",
        listOf("Solo por la apariencia", "Con indicaciones apropiadas y seguimiento"),
        1,
        "Las porciones deben considerar las necesidades individuales de la mascota."
    )
)

@Composable
fun GamingScreen() {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("petmin_gaming", Context.MODE_PRIVATE)
    }

    var name by remember {
        mutableStateOf(prefs.getString("name", "Luna") ?: "Luna")
    }
    var cat by remember {
        mutableStateOf(prefs.getBoolean("cat", false))
    }
    var completed by remember {
        mutableIntStateOf(prefs.getInt("completed", 0).coerceIn(0, 5))
    }
    var selected by remember { mutableStateOf<Int?>(null) }
    var feedback by remember { mutableStateOf("") }
    var rotation by remember { mutableFloatStateOf(0f) }

    val view = remember(context) { PetScene(context) }
    val owner = LocalLifecycleOwner.current

    // PETMIN HARDWARE: El videojuego no controla el dispensador real.
    // Sus acciones afectan únicamente a la mascota virtual.
    DisposableEffect(owner, view) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                else -> Unit
            }
        }

        owner.lifecycle.addObserver(observer)

        onDispose {
            owner.lifecycle.removeObserver(observer)
            view.onPause()
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "PetMin Gaming",
                style = MaterialTheme.typography.headlineMedium
            )
            Text("Aprende a cuidar · ${completed * 20} puntos · $completed/5 desafíos")
        }

        item {
            AndroidView(
                factory = { view },
                update = {
                    it.scene.cat = cat
                    it.scene.angle = rotation
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            )
        }

        item {
            Text("Girar habitación")
            Slider(
                value = rotation,
                onValueChange = { rotation = it },
                valueRange = -35f..35f
            )
        }

        item {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(30)
                    prefs.edit().putString("name", name).apply()
                },
                label = { Text("Nombre de tu mascota virtual") },
                singleLine = true
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(false, true).forEach { isCat ->
                    FilterChip(
                        selected = cat == isCat,
                        onClick = {
                            cat = isCat
                            prefs.edit().putBoolean("cat", cat).apply()
                        },
                        label = { Text(if (isCat) "Gato" else "Perro") }
                    )
                }
            }
        }

        item {
            Text(
                "${name.ifBlank { "Tu mascota" }} vive solo en el juego. " +
                        "Ninguna acción entrega alimento real."
            )
        }

        if (completed < challenges.size) {
            val challenge = challenges[completed]

            item {
                Text(
                    challenge.question,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            item {
                Column {
                    challenge.options.forEachIndexed { index, option ->
                        OutlinedButton(
                            onClick = {
                                selected = index
                                feedback = if (index == challenge.answer) {
                                    "¡Correcto! ${challenge.explanation}"
                                } else {
                                    "Inténtalo de nuevo. ${challenge.explanation}"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(option)
                        }
                    }
                }
            }

            item {
                Text(feedback)
            }

            item {
                Button(
                    enabled = selected == challenge.answer,
                    onClick = {
                        completed++
                        prefs.edit()
                            .putInt("completed", completed)
                            .apply()

                        view.scene.animate(
                            if (completed == 1) "eat" else "happy"
                        )

                        selected = null
                        feedback = ""
                    }
                ) {
                    Text("Guardar logro y continuar")
                }
            }
        } else {
            item {
                Text(
                    "¡Completaste los cinco desafíos! " +
                            "Tu progreso está guardado."
                )
            }

            item {
                Row {
                    Button(
                        onClick = { view.scene.animate("happy") }
                    ) {
                        Text("Celebrar")
                    }

                    TextButton(
                        onClick = { view.scene.animate("eat") }
                    ) {
                        Text("Comida virtual")
                    }
                }
            }
        }

        item {
            Text(
                "Contenido educativo: ASPCA, People Foods to Avoid " +
                        "Feeding Your Pets. Ante una ingestión sospechosa, " +
                        "contacta a un veterinario. No esperes síntomas.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
