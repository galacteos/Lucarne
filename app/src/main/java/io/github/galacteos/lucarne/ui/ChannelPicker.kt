package io.github.galacteos.lucarne.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.galacteos.lucarne.xmltv.Channel
import io.github.galacteos.lucarne.xmltv.TntNumbering

/** Fond clair derrière les logos : beaucoup sont dessinés en sombre sur transparent. */
internal val LogoBackground = Color(0xFFF1F3F5)

/** Hauteur unique : la grille reste régulière quelle que soit la longueur des noms. */
private val CellHeight = 112.dp

/**
 * Choix des chaînes : recherche juste au-dessus de la grille, chaînes cochées en tête.
 *
 * L'ordre n'est figé qu'à l'ouverture et à chaque recherche : cocher une chaîne ne la
 * fait pas sauter ailleurs sous le doigt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelPicker(
    channels: List<Channel>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: (List<String>) -> Unit,
    onClearAll: (List<String>) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val gridState = rememberLazyGridState()
    val pinned = remember(channels, query) { selected }

    val shown = remember(channels, query, pinned) {
        val needle = TntNumbering.normalize(query)
        val filtered =
            if (query.isBlank()) channels
            else channels.filter { TntNumbering.normalize(it.name).contains(needle) }
        filtered.sortedWith(
            compareBy(
                { if (it.id in pinned) 0 else 1 },
                { it.number ?: Int.MAX_VALUE },
                { it.name },
            ),
        )
    }

    BackHandler { onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                title = { Text("Chaînes") },
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
                .imePadding(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                label = { Text("Rechercher une chaîne") },
            )

            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selected.size.toString() + " / " + channels.size,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Spacer(Modifier.height(0.dp))
                TextButton(onClick = { onSelectAll(shown.map { it.id }) }) { Text("Tout cocher") }
                TextButton(onClick = { onClearAll(shown.map { it.id }) }) { Text("Tout décocher") }
            }

            Box(Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 118.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.id }) { channel ->
                        ChannelCell(
                            channel = channel,
                            checked = channel.id in selected,
                            onToggle = { onToggle(channel.id) },
                        )
                    }
                }

                if (shown.isEmpty()) {
                    Text(
                        text = if (channels.isEmpty()) {
                            "La liste apparaîtra après le premier téléchargement."
                        } else {
                            "Aucune chaîne ne correspond."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelCell(
    channel: Channel,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(CellHeight)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onToggle),
        color = if (checked) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (checked) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(LogoBackground),
            ) {
                // Le logo s'affiche toujours ici : c'est lui qui permet de reconnaître
                // la chaîne, indépendamment du réglage des vignettes de programmes.
                RemoteImage(
                    url = channel.iconUrl,
                    targetWidth = 96.dp,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                )
                if (checked) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(1.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            Text(
                text = (channel.number?.let { "$it · " } ?: "") + channel.name,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 13.sp,
            )
        }
    }
}
