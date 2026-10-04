package fr.guidetv.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.guidetv.R
import fr.guidetv.data.GuideRepository
import fr.guidetv.data.RefreshResult
import fr.guidetv.xmltv.Guide
import fr.guidetv.xmltv.Programme
import fr.guidetv.xmltv.Slot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Rayon des vignettes : assez pour s'accorder aux cartes, assez peu pour rogner l'image. */
private val ThumbnailShape =
    RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = 0.dp, bottomEnd = 0.dp)
private val ThumbnailWidth = 148.dp
private val ChannelStripWidth = 52.dp

enum class Mode(val label: String) {
    NOW("En cours"),
    TONIGHT("Soirée"),
    SECOND("2e partie"),
}

data class GuideUiState(
    val guide: Guide? = null,
    val mode: Mode = Mode.NOW,
    val loading: Boolean = false,
    val message: String? = null,
    val showImages: Boolean = true,
    val selected: Slot? = null,
    val day: LocalDate? = null,
    val showAbout: Boolean = false,
    val showSettings: Boolean = false,
    val channelOf: Slot? = null,
)

class GuideViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = GuideRepository(app)
    private val _state = MutableStateFlow(GuideUiState(showImages = repo.showImages, loading = true))
    val state: StateFlow<GuideUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(guide = repo.loadCached(), loading = true) }
            refresh()
        }
    }

    fun setMode(mode: Mode) = _state.update { it.copy(mode = mode) }

    fun select(slot: Slot?) = _state.update { it.copy(selected = slot) }

    fun setDay(day: LocalDate) = _state.update { it.copy(day = day) }

    fun setAbout(visible: Boolean) = _state.update { it.copy(showAbout = visible) }

    fun setSettings(visible: Boolean) = _state.update { it.copy(showSettings = visible) }

    /** Après un changement de sources ou de chaînes : relire le cache, puis vérifier. */
    fun reload() {
        viewModelScope.launch {
            _state.update { it.copy(guide = repo.loadCached()) }
            refresh(force = true)
        }
    }

    /** La fiche est refermée au passage : sinon elle resterait prioritaire à l'affichage. */
    fun openChannel(slot: Slot) = _state.update { it.copy(channelOf = slot, selected = null) }

    fun closeChannel() = _state.update { it.copy(channelOf = null) }

    /** Réglage conservé pour l'écran de réglages : les vignettes sont des requêtes
     *  vers les serveurs des éditeurs, il faut pouvoir les couper. */
    fun setShowImages(enabled: Boolean) {
        repo.showImages = enabled
        _state.update { it.copy(showImages = enabled) }
    }

    /** Le message brut du système ne dit rien à personne : on le traduit. */
    private fun humanError(error: Throwable): String = when (error) {
        is UnknownHostException -> "Source injoignable : pas de connexion, ou DNS indisponible."
        is SocketTimeoutException -> "La source ne répond pas."
        is IOException -> error.message ?: "Téléchargement impossible."
        else -> error.message ?: "Téléchargement impossible."
    }

    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            when (val result = repo.refresh(force)) {
                is RefreshResult.Updated ->
                    _state.update { it.copy(guide = result.guide, loading = false) }
                RefreshResult.NotModified, RefreshResult.Skipped ->
                    _state.update { it.copy(loading = false) }
                is RefreshResult.Failed ->
                    _state.update { it.copy(loading = false, message = humanError(result.error)) }
            }
        }
    }
}

@Composable
fun GuideScreen(vm: GuideViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    // Remembered hors de la liste : la position survit à l'aller-retour vers la fiche.
    val listState = rememberLazyListState()

    // Horloge : « en cours » se recalcule sur le téléphone, pas au téléchargement.
    val now by produceState(initialValue = Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(30_000)
        }
    }

    val selected = state.selected
    val channelOf = state.channelOf
    val guide = state.guide
    if (state.showAbout) {
        BackHandler { vm.setAbout(false) }
        AboutScreen(onBack = { vm.setAbout(false) })
    } else if (state.showSettings) {
        BackHandler {
            vm.setSettings(false)
            vm.reload()
        }
        SettingsScreen(
            onBack = { vm.setSettings(false) },
            onApplied = { vm.reload() },
        )
    } else if (selected != null) {
        BackHandler { vm.select(null) }
        ProgrammeDetail(
            slot = selected,
            zone = zone,
            showImages = state.showImages,
            onBack = { vm.select(null) },
            onChannel = { vm.openChannel(selected) },
        )
    } else if (channelOf != null && guide != null) {
        BackHandler { vm.closeChannel() }
        ChannelScreen(
            guide = guide,
            slot = channelOf,
            zone = zone,
            onSelect = { vm.select(it) },
            onBack = { vm.closeChannel() },
        )
    } else {
        GuideList(state = state, now = now, zone = zone, vm = vm, listState = listState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuideList(
    state: GuideUiState,
    now: Instant,
    zone: ZoneId,
    vm: GuideViewModel,
    listState: LazyListState,
) {
    val day = state.day ?: LocalDate.now(zone)
    val slots = remember(state.guide, state.mode, now, day) {
        val guide = state.guide
        when {
            guide == null -> emptyList()
            state.mode == Mode.NOW -> guide.onAir(now)
            state.mode == Mode.TONIGHT -> guide.primeTime(day, zone)
            else -> guide.secondPart(day, zone)
        }
    }
    // Le fichier contient encore la veille : on ne propose jamais une date passée.
    val days = remember(state.guide, zone) {
        val today = LocalDate.now(zone)
        state.guide?.days(zone).orEmpty().filter { !it.isBefore(today) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            // Barre fixe : pas de scrollBehavior, donc rien ne se rétracte au défilement.
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.clickable { vm.setAbout(true) },
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(50.dp),
                        )
                        Text(
                            text = "Lucarne",
                            style = WordmarkStyle,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh(force = true) }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Actualiser")
                    }
                    IconButton(onClick = { vm.setSettings(true) }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Réglages",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Choix exclusif : Material 3 prescrit les boutons segmentés, pas des puces.
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Mode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick = { vm.setMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, Mode.entries.size),
                        // Sans ce vidage, la coche de sélection mange la largeur du libellé.
                        icon = {},
                        label = {
                            Text(
                                text = mode.label,
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                    )
                }
            }

            if (state.mode != Mode.NOW && days.isNotEmpty()) {
                DaySelector(
                    days = days,
                    selected = day,
                    onSelect = { vm.setDay(it) },
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                )
            }

            state.message?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            Box(Modifier.fillMaxSize()) {
                val scope = rememberCoroutineScope()

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(slots, key = { it.channel.id }) { slot ->
                        ProgrammeRow(
                            slot = slot,
                            now = now,
                            zone = zone,
                            showImages = state.showImages,
                            onClick = { vm.select(slot) },
                            onOpenChannel = { vm.openChannel(slot) },
                        )
                    }
                    if (slots.isEmpty() && !(state.guide == null && state.message != null)) {
                        item {
                            Text(
                                text = when {
                                    state.loading -> "Chargement du guide…"
                                    state.guide == null ->
                                        "Guide indisponible. Vérifie l'URL de la source dans les réglages."
                                    state.mode == Mode.NOW -> "Aucun programme en cours dans le guide téléchargé."
                                    else -> "Aucun programme ce jour-là dans le guide téléchargé."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            )
                        }
                    }
                }

                if (state.loading) {
                    LinearProgressIndicator(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                    )
                }

                if (state.loading && state.guide == null) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                    )
                }

                val showTop by remember {
                    derivedStateOf { listState.firstVisibleItemIndex > 2 }
                }
                ScrollToTopButton(
                    visible = showTop,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                )
            }
        }
    }
}

/**
 * Bouton de retour en tête de liste. Il n'apparaît qu'une fois la liste descendue,
 * pour ne pas occuper l'écran en permanence.
 */
/**
 * Bouton de retour en tête de liste. Il n'apparaît qu'une fois la liste descendue,
 * pour ne pas occuper l'écran en permanence.
 */
@Composable
internal fun ScrollToTopButton(
    visible: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        SmallFloatingActionButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Revenir en haut",
            )
        }
    }
}

@Composable
private fun ProgrammeRow(
    slot: Slot,
    now: Instant,
    zone: ZoneId,
    showImages: Boolean,
    onClick: () -> Unit,
    onOpenChannel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        ChannelStrip(number = slot.channel.number, onClick = onOpenChannel)
        Box(Modifier.weight(1f)) {
            ProgrammeCardContent(
                slot = slot,
                now = now,
                zone = zone,
                showImages = showImages,
                onClick = onClick,
                onOpenChannel = onOpenChannel,
            )
        }
    }
}

/** Bande fixe à gauche : le numéro reste lisible, volet ouvert ou non. */
@Composable
private fun ChannelStrip(number: Int?, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .width(ChannelStripWidth)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        // Aligné sur la première ligne de la carte.
        Box(
            modifier = Modifier.padding(top = 10.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            ChannelBadge(number = number)
        }
    }
}

/** Repli visible quand il n'y a ni image ni logo, ou que le chargement a échoué. */
@Composable
private fun ChannelTile(number: Int?) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = number?.toString() ?: "—",
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
internal fun ChannelBadge(number: Int?, modifier: Modifier = Modifier) {
    if (number == null) return
    Surface(
        // Largeur minimale : les numéros restent alignés d'une carte à l'autre.
        modifier = modifier.widthIn(min = 30.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun DaySelector(
    days: List<LocalDate>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Une seule ligne, défilable : tout est visible d'un coup d'œil, un geste pour choisir.
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(days, key = { it.toString() }) { day ->
            FilterChip(
                selected = day == selected,
                onClick = { onSelect(day) },
                shape = MaterialTheme.shapes.small,
                label = { Text(text = dayLabel(day, short = true), maxLines = 1, softWrap = false) },
            )
        }
    }
}

private fun dayLabel(day: LocalDate, short: Boolean = false): String {
    val today = LocalDate.now()
    val pattern = if (short) "EEE d" else "EEEE d MMMM"
    return when (day) {
        today -> if (short) "Auj." else "Aujourd'hui"
        today.plusDays(1) -> "Demain"
        else -> DateTimeFormatter.ofPattern(pattern, Locale.FRENCH)
            .format(day)
            .replace(".", "")
            .replaceFirstChar { it.uppercase() }
    }
}

@Composable
private fun ProgrammeCardContent(
    slot: Slot,
    now: Instant,
    zone: ZoneId,
    showImages: Boolean,
    onClick: () -> Unit,
    onOpenChannel: (() -> Unit)? = null,
) {
    val formatter = remember(zone) { DateTimeFormatter.ofPattern("HH:mm").withZone(zone) }
    val programme = slot.programme

    val imageUrl = programme.iconUrl?.takeIf { it.isNotBlank() }
    val logoUrl = slot.channel.iconUrl?.takeIf { it.isNotBlank() }
    val shownUrl = if (showImages) imageUrl ?: logoUrl else null
    val fallbackUrl = if (showImages && imageUrl != null) logoUrl else null
    // Un logo se cadre en entier, une image d'émission se recadre.
    var showingLogo by remember(imageUrl, logoUrl) { mutableStateOf(imageUrl == null) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = slot.channel.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (onOpenChannel == null) Modifier
                    else Modifier.clickable(onClick = onOpenChannel),
                )

                Spacer(Modifier.height(2.dp))

                // Retrait fixe : le bloc tombe plus au centre de l'écran, et reste
                // sur la même marge d'une carte à l'autre. Le titre n'est pas tronqué.
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = programme.title,
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Text(
                        text = scheduleLine(programme, now, formatter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )

                    programme.category?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

            }

            // Une seule place pour le visuel : image de l'émission, sinon logo de la
            // chaîne, sinon pavé numéroté — y compris si le chargement échoue.
            RemoteImage(
                url = shownUrl,
                targetWidth = ThumbnailWidth,
                contentScale = if (showingLogo) ContentScale.Fit else ContentScale.Crop,
                fallbackUrl = fallbackUrl,
                onFallbackUsed = { showingLogo = it || imageUrl == null },
                placeholder = { ChannelTile(number = slot.channel.number) },
                modifier = Modifier
                    .width(ThumbnailWidth)
                    .aspectRatio(16f / 9f)
                    .clip(ThumbnailShape)
                    .then(
                        // Beaucoup de logos sont dessinés en sombre sur fond transparent.
                        if (showingLogo) Modifier.background(LogoBackground) else Modifier,
                    ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgrammeDetail(
    slot: Slot,
    zone: ZoneId,
    showImages: Boolean,
    onBack: () -> Unit,
    onChannel: () -> Unit,
) {
    val programme = slot.programme
    val hourFormat = remember(zone) { DateTimeFormatter.ofPattern("HH:mm").withZone(zone) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                        )
                    }
                },
                title = {
                    Row(
                        modifier = Modifier.clickable(onClick = onChannel),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChannelBadge(number = slot.channel.number)
                        Text(
                            text = slot.channel.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
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
                .padding(start = 20.dp, end = 20.dp, bottom = 40.dp),
        ) {
            if (showImages) {
                RemoteImage(
                    url = programme.iconUrl?.takeIf { it.isNotBlank() }
                        ?: slot.channel.iconUrl?.takeIf { it.isNotBlank() },
                    targetWidth = 520.dp,
                    placeholder = { ChannelTile(number = slot.channel.number) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .aspectRatio(16f / 9f)
                        .clip(MaterialTheme.shapes.medium),
                )
                Spacer(Modifier.height(12.dp))
            }

            Text(
                text = programme.title,
                style = MaterialTheme.typography.headlineSmall,
            )

            programme.subTitles.forEach { subTitle ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(10.dp))

            // Deux lignes serrées plutôt qu'un pavé : le résumé reste visible sans défiler.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(
                        text = buildString {
                            append(hourFormat.format(programme.start))
                            programme.stop?.let { stop ->
                                append(" – ").append(hourFormat.format(stop))
                                val minutes = Duration.between(programme.start, stop).toMinutes()
                                if (minutes > 0) append("  ·  ").append(humanDuration(minutes))
                            }
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = buildString {
                            append(relativeDay(programme.start, zone))
                            programme.category?.let { append("  ·  ").append(it) }
                            programme.episode?.let { append("  ·  ").append(it) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Résumé",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = programme.description
                    ?: "La source ne fournit pas de résumé pour cette diffusion.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/**
 * « 21:10 · 1 h 35 » avant diffusion, « 21:10 · reste 42 min » pendant.
 * L'heure de début est toujours en tête : c'est le repère commun aux deux modes.
 */
private fun scheduleLine(programme: Programme, now: Instant, formatter: DateTimeFormatter): String {
    val start = formatter.format(programme.start)
    val stop = programme.stop ?: return start
    return if (programme.isOnAir(now)) {
        val left = Duration.between(now, stop).toMinutes()
        if (left > 0) start + "  ·  reste " + humanDuration(left) else start
    } else {
        start + "  ·  " + humanDuration(Duration.between(programme.start, stop).toMinutes())
    }
}

private fun humanDuration(minutes: Long): String {
    if (minutes < 60) return minutes.toString() + " min"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0L) hours.toString() + " h"
    else hours.toString() + " h " + rest.toString().padStart(2, '0')
}

/** « Aujourd'hui », « Demain », sinon la date complète en clair. */
private fun relativeDay(instant: Instant, zone: ZoneId): String {
    val day = instant.atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val full = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
        .format(day)
        .replaceFirstChar { it.uppercase() }
    return when (day) {
        today -> "Aujourd'hui  ·  " + full
        today.plusDays(1) -> "Demain  ·  " + full
        else -> full
    }
}

