package io.github.galacteos.lucarne.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import io.github.galacteos.lucarne.data.GuideRepository
import io.github.galacteos.lucarne.xmltv.Channel

/**
 * Réglages : sources XMLTV, chaînes retenues, vignettes.
 *
 * Les sources sont une liste : la TNT par défaut, et autant de fichiers que voulu pour
 * les chaînes qui n'y sont pas — Warner TV, Comedy Central, Comédie+ et le reste sont
 * dans le guide complet du même service. Une source ajoutée n'active aucune de ses
 * chaînes : elles apparaissent ici, décochées, et se cherchent par leur nom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onApplied: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { GuideRepository(context) }
    val scope = rememberCoroutineScope()

    var sources by remember { mutableStateOf(repo.sources) }
    var selected by remember { mutableStateOf(repo.selectedChannels) }
    var images by remember { mutableStateOf(repo.showImages) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var downloading by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { channels = repo.availableChannels() }

    if (picking) {
        ChannelPicker(
            channels = channels,
            selected = selected,
            onToggle = { id ->
                selected = if (id in selected) selected - id else selected + id
                repo.selectedChannels = selected
            },
            onSelectAll = { ids ->
                selected = selected + ids
                repo.selectedChannels = selected
            },
            onClearAll = { ids ->
                selected = selected - ids.toSet()
                repo.selectedChannels = selected
            },
            onBack = { picking = false },
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { onApplied(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                title = { Text("Réglages") },
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
                .padding(bottom = 32.dp),
        ) {
            SectionTitle("Sources XMLTV")
            Text(
                text = "Une URL par source, fusionnées en une seule grille.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            sources.forEachIndexed { index, url ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { value ->
                            sources = sources.toMutableList().also { it[index] = value }
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        label = { Text("Source " + (index + 1)) },
                    )
                    IconButton(
                        onClick = {
                            sources = sources.toMutableList().also { it.removeAt(index) }
                        },
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { sources = sources + "" }) { Text("Ajouter") }
                TextButton(
                    onClick = {
                        if (GuideRepository.FULL_SOURCE_URL !in sources) {
                            sources = sources + GuideRepository.FULL_SOURCE_URL
                        }
                    },
                ) { Text("Ajouter les chaînes françaises") }
            }
            Text(
                text = "Ce second fichier apporte les chaînes françaises hors TNT — " +
                    "Warner TV, Comedy Central, Comédie+… Elles n'apparaissent qu'une fois " +
                    "le fichier téléchargé.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        repo.sources = sources
                        downloading = true
                        repo.refresh(force = true)
                        channels = repo.availableChannels()
                        downloading = false
                        onApplied()
                    }
                },
                enabled = !downloading,
            ) {
                Text(if (downloading) "Téléchargement…" else "Appliquer et télécharger")
            }
            if (downloading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }

            SectionTitle("Vignettes")
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Afficher les images des programmes",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = images,
                    onCheckedChange = {
                        images = it
                        repo.showImages = it
                    },
                )
            }
            Text(
                text = "Chaque vignette est une requête chez l'éditeur qui l'héberge.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("Chaînes")
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { picking = true },
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Choisir les chaînes", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = selected.size.toString() + " cochées sur " +
                                channels.size + " disponibles",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
}
