package fr.guidetv.xmltv

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * Parsing XMLTV en flux.
 *
 * La mémoire ne dépend pas de la taille du fichier mais du nombre de diffusions
 * conservées : avec [keepChannels] on peut viser le guide complet de xmltvfr
 * (139 Mo bruts) sans tout garder. null = on garde tout (cas TNT, ~7 Mo bruts).
 *
 * Un fichier tronqué (coupure réseau) ne lève pas d'exception : on renvoie ce
 * qui a pu être lu.
 */
class XmltvParser(
    private val keepChannels: Set<String>? = null,
    private val preferredLang: String = "fr",
    /** Diffusions terminées avant cet instant : ignorées, pour alléger la mémoire. */
    private val notBefore: java.time.Instant? = null,
    /** Diffusions commençant après cet instant : ignorées de même. */
    private val notAfter: java.time.Instant? = null,
) {

    /**
     * Liste des chaînes seulement. XMLTV place tous les <channel> avant les
     * <programme> : on s'arrête au premier, ce qui rend l'opération rapide même sur
     * le guide complet.
     */
    fun parseChannels(input: InputStream): List<Channel> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT &&
            !(event == XmlPullParser.START_TAG && parser.name == "tv")
        ) {
            event = parser.next()
        }
        if (event == XmlPullParser.END_DOCUMENT) return emptyList()

        val channels = ArrayList<Channel>(64)
        while (true) {
            val ev = parser.next()
            if (ev == XmlPullParser.END_TAG || ev == XmlPullParser.END_DOCUMENT) break
            if (ev != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "channel" -> readChannel(parser)?.let { channels.add(it) }
                "programme" -> return channels
                else -> skip(parser)
            }
        }
        return channels
    }

    fun parse(input: InputStream): Guide {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        // Se positionner sur <tv>, en sautant déclaration XML, DOCTYPE et commentaires.
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT &&
            !(event == XmlPullParser.START_TAG && parser.name == "tv")
        ) {
            event = parser.next()
        }
        if (event == XmlPullParser.END_DOCUMENT) return Guide.EMPTY

        val channels = LinkedHashMap<String, Channel>()
        val programmes = ArrayList<Programme>(8192)

        while (true) {
            val ev = parser.next()
            if (ev == XmlPullParser.END_TAG || ev == XmlPullParser.END_DOCUMENT) break
            if (ev != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "channel" -> readChannel(parser)?.let { if (keep(it.id)) channels[it.id] = it }
                "programme" -> {
                    // Les attributs suffisent à décider : inutile de lire titre, résumé
                    // et catégories d'une diffusion qu'on va jeter. Sur le guide complet,
                    // c'est l'essentiel du temps de chargement.
                    val channelId = parser.getAttributeValue(null, "channel")?.trim().orEmpty()
                    val start = XmltvTime.parse(parser.getAttributeValue(null, "start"))
                    val stop = XmltvTime.parse(parser.getAttributeValue(null, "stop"))
                    val end = stop ?: start
                    val tooOld = notBefore != null && end != null && end < notBefore
                    val tooFar = notAfter != null && start != null && start > notAfter
                    if (channelId.isEmpty() || start == null || tooOld || tooFar || !keep(channelId)) {
                        skip(parser)
                    } else {
                        readProgramme(parser, channelId, start, stop)?.let { programmes.add(it) }
                    }
                }
                else -> skip(parser)
            }
        }

        programmes.trimToSize()
        return Guide(channels.values.toList(), programmes)
    }

    private fun readChannel(parser: XmlPullParser): Channel? {
        val id = parser.getAttributeValue(null, "id")?.trim().orEmpty()
        val names = ArrayList<String>(3)
        var icon: String? = null

        while (true) {
            val ev = parser.next()
            if (ev == XmlPullParser.END_TAG || ev == XmlPullParser.END_DOCUMENT) break
            if (ev != XmlPullParser.START_TAG) continue
            when (parser.name) {
                // XMLTV autorise plusieurs <display-name> : nom, numéro, variantes.
                "display-name" -> readText(parser).let { if (it.isNotBlank()) names.add(it) }
                "icon" -> {
                    if (icon == null) icon = parser.getAttributeValue(null, "src")
                    skip(parser)
                }
                else -> skip(parser)
            }
        }

        if (id.isEmpty()) return null

        val name = names.firstOrNull { it.toIntOrNull() == null } ?: id
        val number = names.firstNotNullOfOrNull { it.toIntOrNull() } ?: TntNumbering.forName(name)

        return Channel(id = id, name = name, number = number, iconUrl = icon)
    }

    private fun readProgramme(
        parser: XmlPullParser,
        channelId: String,
        start: java.time.Instant,
        stop: java.time.Instant?,
    ): Programme? {
        var title: String? = null
        var titleLang: String? = null
        val subTitles = ArrayList<String>(2)
        var episode: String? = null
        var description: String? = null
        var descriptionLang: String? = null
        var category: String? = null
        var icon: String? = null

        while (true) {
            val ev = parser.next()
            if (ev == XmlPullParser.END_TAG || ev == XmlPullParser.END_DOCUMENT) break
            if (ev != XmlPullParser.START_TAG) continue

            // Les attributs se lisent AVANT readText/skip, qui déplacent le curseur.
            val lang = parser.getAttributeValue(null, "lang")
            when (parser.name) {
                "title" -> {
                    val text = readText(parser)
                    if (text.isNotBlank() && (title == null || (titleLang != preferredLang && lang == preferredLang))) {
                        title = text
                        titleLang = lang
                    }
                }
                "sub-title" -> {
                    val text = readText(parser)
                    if (text.isNotBlank() && text !in subTitles) subTitles.add(text)
                }
                "episode-num" -> {
                    val system = parser.getAttributeValue(null, "system")
                    val text = readText(parser)
                    if (episode == null) episode = formatEpisode(system, text)
                }
                "desc" -> {
                    val text = readText(parser)
                    if (text.isNotBlank() &&
                        (description == null || (descriptionLang != preferredLang && lang == preferredLang))
                    ) {
                        description = text
                        descriptionLang = lang
                    }
                }
                "category" -> {
                    val text = readText(parser)
                    if (category.isNullOrBlank() && text.isNotBlank()) category = text
                }
                "icon" -> {
                    if (icon == null) icon = parser.getAttributeValue(null, "src")
                    skip(parser)
                }
                // credits, episode-num, rating, star-rating… : ignorés pour l'instant
                else -> skip(parser)
            }
        }

        val cleanTitle = title?.trim().orEmpty()
        if (cleanTitle.isEmpty()) return null

        return Programme(
            channelId = channelId,
            start = start,
            stop = stop,
            title = cleanTitle,
            subTitles = subTitles.toList(),
            episode = episode,
            description = description?.takeIf { it.isNotBlank() },
            category = category?.takeIf { it.isNotBlank() },
            iconUrl = icon?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * <episode-num system="xmltv_ns">0.4.0/2</episode-num> -> « Saison 1, épisode 5 ».
     * Les autres systèmes (onscreen…) sont repris tels quels.
     */
    private fun formatEpisode(system: String?, raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        if (system != "xmltv_ns") return value

        val parts = value.split(".")
        val season = parts.getOrNull(0)?.substringBefore("/")?.trim()?.toIntOrNull()?.plus(1)
        val episode = parts.getOrNull(1)?.substringBefore("/")?.trim()?.toIntOrNull()?.plus(1)
        return when {
            season != null && episode != null -> "Saison " + season + ", épisode " + episode
            episode != null -> "Épisode " + episode
            season != null -> "Saison " + season
            else -> null
        }
    }

    /** Lit le texte de l'élément courant et laisse le curseur sur son END_TAG. */
    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        while (true) {
            when (parser.next()) {
                XmlPullParser.TEXT -> sb.append(parser.text.orEmpty())
                XmlPullParser.ENTITY_REF -> sb.append(parser.text.orEmpty())
                XmlPullParser.START_TAG -> skip(parser)
                XmlPullParser.END_TAG -> return sb.toString().trim()
                XmlPullParser.END_DOCUMENT -> return sb.toString().trim()
            }
        }
    }

    /** Saute l'élément courant, sous-arbre compris. */
    private fun skip(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private fun keep(channelId: String): Boolean = keepChannels == null || channelId in keepChannels
}
