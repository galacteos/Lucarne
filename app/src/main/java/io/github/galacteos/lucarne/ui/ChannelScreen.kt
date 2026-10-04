package io.github.galacteos.lucarne.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.galacteos.lucarne.xmltv.Guide
import io.github.galacteos.lucarne.xmltv.Programme
import io.github.galacteos.lucarne.xmltv.Slot
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Grille d'une chaîne, autour de la diffusion d'où l'on vient : ce qui précède et
 * ce qui suit. La journée télé est découpée à 05:00, comme dans les magazines, et
 * la journée suivante est ajoutée à la suite pour ne pas s'arrêter au milieu de la nuit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    guide: Guide,
    slot: Slot,
    zone: ZoneId,
    onSelect: (Slot) -> Unit,
    onBack: () -> Unit,
) {
    val hourFormat = remember(zone) { DateTimeFormatter.ofPattern("HH:mm").withZone(zone) }

    val programmes = remember(guide, slot, zone) {
        val first = gridDay(slot.programme.start, zone)
        guide.daySchedule(slot.channel.id, first, zone) +
            guide.daySchedule(slot.channel.id, first.plusDays(1), zone)
    }
    val anchorIndex = remember(programmes, slot) {
        programmes.indexOfFirst { it.start == slot.programme.start }.coerceAtLeast(0)
    }

    val listState = rememberLazyListState()
    // Une seule diffusion visible au-dessus : c'est le contexte utile, le reste se
    // trouve en remontant.
    LaunchedEffect(anchorIndex) {
        listState.scrollToItem((anchorIndex - 1).coerceAtLeast(0))
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
                title = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChannelBadge(number = slot.channel.number)
                        Text(
                            text = slot.channel.name,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
      Box(Modifier.padding(padding).fillMaxSize()) {
        // Marge basse généreuse : même la dernière diffusion peut remonter en haut.
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = screenHeight),
        ) {
            itemsIndexed(programmes) { _, programme ->
                ScheduleRow(
                    programme = programme,
                    current = programme.start == slot.programme.start,
                    hourFormat = hourFormat,
                    onClick = { onSelect(Slot(slot.channel, programme)) },
                )
            }
            if (programmes.isEmpty()) {
                item {
                    Text(
                        text = "Aucune grille pour cette chaîne dans le fichier téléchargé.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }

        val scope = rememberCoroutineScope()
        // Retour à la diffusion d'origine, et non en tête de fichier : c'est le repère
        // qu'on vient de quitter en faisant défiler.
        val target = (anchorIndex - 1).coerceAtLeast(0)
        val showTop by remember(target) {
            derivedStateOf { listState.firstVisibleItemIndex != target }
        }
        ScrollToTopButton(
            visible = showTop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            onClick = { scope.launch { listState.animateScrollToItem(target) } },
        )
      }
    }
}

@Composable
internal fun ScheduleRow(
    programme: Programme,
    current: Boolean,
    hourFormat: DateTimeFormatter,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (current) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface,
        contentColor = if (current) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = hourFormat.format(programme.start),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.width(62.dp),
            )
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = programme.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = buildString {
                    programme.stop?.let { stop ->
                        val minutes = Duration.between(programme.start, stop).toMinutes()
                        if (minutes > 0) append(minutes).append(" min")
                    }
                    programme.category?.let {
                        if (isNotEmpty()) append("  ·  ")
                        append(it)
                    }
                }
                if (details.isNotEmpty()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    HorizontalDivider()
}

/** La journée télé commence à 05:00 : une diffusion de 00:30 appartient à la veille. */
internal fun gridDay(instant: Instant, zone: ZoneId): LocalDate {
    val local = instant.atZone(zone)
    return if (local.toLocalTime() < LocalTime.of(5, 0)) local.toLocalDate().minusDays(1)
    else local.toLocalDate()
}
