package fr.guidetv.xmltv

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Une chaîne du guide (élément <channel> XMLTV). */
data class Channel(
    val id: String,
    val name: String,
    /** Numéro de canal : <display-name> numérique du fichier, sinon table Arcom. */
    val number: Int? = null,
    val iconUrl: String? = null,
)

/** Une diffusion (élément <programme> XMLTV). */
data class Programme(
    val channelId: String,
    val start: Instant,
    val stop: Instant?,
    val title: String,
    /** XMLTV autorise plusieurs <sub-title> : titre d'épisode, accroche… */
    val subTitles: List<String> = emptyList(),
    /** <episode-num>, mis en forme : « Saison 1, épisode 5 ». */
    val episode: String? = null,
    val description: String? = null,
    val category: String? = null,
    val iconUrl: String? = null,
) {
    val subTitle: String? get() = subTitles.firstOrNull()

    /** Durée en minutes, null si le fichier ne donne pas de fin. */
    fun durationMinutes(): Long? =
        stop?.let { (it.toEpochMilli() - start.toEpochMilli()) / 60000L }

    fun isOnAir(at: Instant): Boolean = start <= at && (stop == null || at < stop)

    /** Avancement 0f..1f, null si la durée est inconnue. */
    fun progress(at: Instant): Float? {
        val end = stop ?: return null
        val total = end.toEpochMilli() - start.toEpochMilli()
        if (total <= 0L) return null
        val done = at.toEpochMilli() - start.toEpochMilli()
        return (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }
}

data class Slot(val channel: Channel, val programme: Programme)

data class Neighbours(val before: List<Programme>, val after: List<Programme>)

/**
 * Guide chargé en mémoire.
 *
 * Volume TNT : ~30 chaînes x 5 jours, soit quelques milliers de diffusions.
 * Toutes les requêtes sont locales, il n'y a aucun appel réseau ici.
 */
class Guide(
    channels: List<Channel>,
    programmes: List<Programme>,
) {
    /** Ordre de la télécommande : par numéro quand il est connu, puis par nom. */
    val channels: List<Channel> =
        channels.sortedWith(compareBy({ it.number ?: Int.MAX_VALUE }, { it.name }))

    private val byChannel: Map<String, List<Programme>> =
        programmes.groupBy { it.channelId }.mapValues { (_, list) -> list.sortedBy { it.start } }

    private val starts: List<Instant> = programmes.map { it.start }.sorted()

    val programmeCount: Int = programmes.size

    /** Jusqu'où va le guide téléchargé, pour afficher « données jusqu'au … ». */
    val coverageEnd: Instant? = programmes.maxOfOrNull { it.stop ?: it.start }

    /** Ce qui passe à l'instant [at] : une ligne par chaîne qui diffuse. */
    fun onAir(at: Instant = Instant.now()): List<Slot> =
        channels.mapNotNull { channel ->
            byChannel[channel.id]?.let { list -> findAt(list, at) }?.let { Slot(channel, it) }
        }

    /**
     * Première partie de soirée.
     *
     * Ce qui passe à [time] convient dans la plupart des cas, mais certaines chaînes
     * glissent un intermède de cinq minutes juste avant le vrai programme. On retient
     * donc la diffusion en cours si elle dure au moins [minMinutes], sinon la première
     * de la plage du soir qui atteint cette durée.
     */
    fun primeTime(
        day: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        time: LocalTime = DEFAULT_PRIME_TIME,
        minMinutes: Long = MIN_PRIME_MINUTES,
    ): List<Slot> {
        val at = day.atTime(time).atZone(zone).toInstant()
        val from = day.atTime(EVENING_FROM).atZone(zone).toInstant()
        val to = day.atTime(EVENING_TO).atZone(zone).toInstant()

        return channels.mapNotNull { channel ->
            val list = byChannel[channel.id] ?: return@mapNotNull null
            val current = findAt(list, at)
            val chosen = current?.takeIf { (it.durationMinutes() ?: 0L) >= minMinutes }
                ?: list.firstOrNull {
                    it.start >= from && it.start <= to && (it.durationMinutes() ?: 0L) >= minMinutes
                }
                ?: current
            chosen?.let { Slot(channel, it) }
        }
    }

    /**
     * Deuxième partie de soirée : ce qui suit la première, intermèdes écartés de la
     * même façon.
     */
    fun secondPart(
        day: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        time: LocalTime = DEFAULT_PRIME_TIME,
        minMinutes: Long = MIN_PRIME_MINUTES,
    ): List<Slot> {
        val prime = primeTime(day, zone, time, minMinutes).associateBy { it.channel.id }
        val at = day.atTime(time).atZone(zone).toInstant()

        return channels.mapNotNull { channel ->
            val list = byChannel[channel.id] ?: return@mapNotNull null
            val after = prime[channel.id]?.programme?.stop ?: at
            val next = list.firstOrNull {
                it.start >= after && (it.durationMinutes() ?: 0L) >= minMinutes
            } ?: list.firstOrNull { it.start >= after }
            next?.let { Slot(channel, it) }
        }
    }

    /** Ce qui précède et ce qui suit une diffusion, sur la même chaîne. */
    fun around(channelId: String, start: Instant, before: Int = 3, after: Int = 3): Neighbours {
        val list = byChannel[channelId].orEmpty()
        val index = list.indexOfFirst { it.start == start }
        if (index < 0) return Neighbours(emptyList(), emptyList())
        return Neighbours(
            before = list.subList((index - before).coerceAtLeast(0), index),
            after = list.subList((index + 1).coerceAtMost(list.size), (index + 1 + after).coerceAtMost(list.size)),
        )
    }

    /** Toutes les diffusions, pour fusionner plusieurs fichiers. */
    fun allProgrammes(): List<Programme> = byChannel.values.flatten()

    /** Jours couverts par le fichier téléchargé, pour le sélecteur de date. */
    fun days(zone: ZoneId = ZoneId.systemDefault()): List<LocalDate> =
        starts.map { it.atZone(zone).toLocalDate() }.distinct().sorted()

    /** Les [count] diffusions qui suivent [after] sur une chaîne (2e partie de soirée, etc.). */
    fun upcoming(channelId: String, after: Instant = Instant.now(), count: Int = 3): List<Programme> =
        byChannel[channelId].orEmpty().asSequence().filter { it.start > after }.take(count).toList()

    /** Grille d'une chaîne pour une journée, découpée à 05:00 comme dans les magazines. */
    fun daySchedule(
        channelId: String,
        day: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        dayStart: LocalTime = DEFAULT_DAY_START,
    ): List<Programme> {
        val from = day.atTime(dayStart).atZone(zone).toInstant()
        val to = day.plusDays(1).atTime(dayStart).atZone(zone).toInstant()
        return byChannel[channelId].orEmpty().filter { it.start >= from && it.start < to }
    }

    /** Recherche dichotomique sur une liste triée par début. */
    private fun findAt(sorted: List<Programme>, at: Instant): Programme? {
        var lo = 0
        var hi = sorted.size - 1
        var candidate: Programme? = null
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid].start <= at) {
                candidate = sorted[mid]
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return candidate?.takeIf { it.isOnAir(at) }
    }

    companion object {
        /** Début de 1re partie de soirée en France depuis le décalage de 2017. */
        val DEFAULT_PRIME_TIME: LocalTime = LocalTime.of(21, 10)

        /** En deçà, c'est un intermède, pas une première partie de soirée. */
        const val MIN_PRIME_MINUTES: Long = 25L
        private val EVENING_FROM: LocalTime = LocalTime.of(20, 45)
        private val EVENING_TO: LocalTime = LocalTime.of(22, 30)
        val DEFAULT_DAY_START: LocalTime = LocalTime.of(5, 0)
        val EMPTY = Guide(emptyList(), emptyList())
    }
}

/**
 * Horodatage XMLTV : "20260927203000 +0200".
 * Le décalage est facultatif dans la spec ; sans lui, on retombe sur le fuseau local.
 */
object XmltvTime {

    private val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    fun parse(raw: String?, fallbackZone: ZoneId = ZoneId.systemDefault()): Instant? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null

        val digits = value.takeWhile { it.isDigit() }
        if (digits.length < 8) return null

        val local = try {
            LocalDateTime.parse(digits.take(14).padEnd(14, '0'), FORMAT)
        } catch (e: Exception) {
            return null
        }

        val offset = value.drop(digits.length).trim().replace(":", "")
        if (offset.isEmpty()) return local.atZone(fallbackZone).toInstant()

        return try {
            local.toInstant(ZoneOffset.of(offset))
        } catch (e: Exception) {
            local.atZone(fallbackZone).toInstant()
        }
    }
}
