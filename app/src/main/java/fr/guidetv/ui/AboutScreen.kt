package fr.guidetv.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.guidetv.R
import fr.guidetv.data.GuideRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Écran « À propos » : licence de l'application, licences des composants
 * embarqués, et attribution des données. La GPL impose de transmettre sa
 * licence avec le programme, l'OFL impose d'accompagner la police de la sienne :
 * les deux textes sont dans res/raw et lisibles ici.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                title = { Text("À propos") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
        ) {
            Text("Lucarne", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Version " + version,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Section("Licence de l'application") {
                Body(
                    "Ce logiciel est distribué sous GNU General Public License v3.0 ou " +
                        "ultérieure. Il est fourni sans aucune garantie, dans la mesure permise " +
                        "par la loi. Son code source doit rester accessible à quiconque en reçoit " +
                        "une copie."
                )
                LicenceButton("Lire la GPL v3 en entier", R.raw.licence_gpl_3_0)
            }

            Section("Composants embarqués") {
                Body("AndroidX et Jetpack Compose — Apache License 2.0.")
                Body("Bibliothèque standard Kotlin — Apache License 2.0.")
                Body("Police Roboto Flex — SIL Open Font License 1.1.")
                Body(
                    "Table des logos de chaînes dérivée du projet XML TV Fr, déclaré " +
                        "sous licence MIT. Seules les URL sont embarquées."
                )
                LicenceButton("Lire la licence de la police", R.raw.licence_ofl_1_1)
            }

            Section("Données des programmes") {
                Body(
                    "Les grilles et les visuels proviennent de la source XMLTV configurée dans " +
                        "l'application, par défaut XML TV Fr. Ils restent la propriété de leurs " +
                        "éditeurs respectifs et ne sont ni stockés ni redistribués par cette " +
                        "application : chaque appareil les demande directement à la source."
                )
                GuideRepository(context).sources.forEach { source ->
                    Body("Source : " + source)
                }
            }

            Section("Vie privée") {
                Body(
                    "Aucun compte, aucune publicité, aucun traceur. Les seules requêtes réseau " +
                        "sont le téléchargement du fichier de programmes et, si les images sont " +
                        "activées, les visuels servis par les éditeurs."
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(6.dp))
    content()
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/** Le texte complet n'est chargé que si l'utilisateur le demande. */
@Composable
private fun LicenceButton(label: String, rawResId: Int) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    TextButton(onClick = { expanded = !expanded }) {
        Text(if (expanded) "Masquer" else label)
    }

    if (expanded) {
        val text by produceState(initialValue = "Chargement…", rawResId) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    context.resources.openRawResource(rawResId).bufferedReader().use { it.readText() }
                }.getOrElse { "Texte indisponible." }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}
