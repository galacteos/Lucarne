package io.github.galacteos.lucarne.data

import android.content.Context
import android.content.SharedPreferences
import io.github.galacteos.lucarne.xmltv.ChannelLogos
import io.github.galacteos.lucarne.xmltv.Channel
import io.github.galacteos.lucarne.xmltv.Guide
import io.github.galacteos.lucarne.xmltv.Programme
import io.github.galacteos.lucarne.xmltv.XmltvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

sealed interface RefreshResult {
    data class Updated(val guide: Guide, val bytes: Long) : RefreshResult
    /** 304 partout : les caches restent valables. */
    data object NotModified : RefreshResult
    /** Vérification trop récente, aucun serveur contacté. */
    data object Skipped : RefreshResult
    data class Failed(val error: Throwable) : RefreshResult
}

/**
 * Tout se passe sur le téléphone : téléchargement, cache, parsing.
 *
 * Plusieurs sources XMLTV peuvent être déclarées — la TNT par défaut, et autant de
 * fichiers supplémentaires que voulu pour les chaînes absentes de la TNT. Elles sont
 * fusionnées en une seule grille.
 *
 * Économie de bande passante, les services étant gratuits :
 *  - requête conditionnelle ETag / If-Modified-Since, donc 304 sans corps ;
 *  - pas plus d'une vérification toutes les [MIN_CHECK_INTERVAL_MS] ;
 *  - User-Agent identifiable.
 */
class GuideRepository(context: Context) {

    private val app = context.applicationContext
    private val prefs: SharedPreferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Sources configurables : c'est la condition posée par F-Droid pour ne pas hériter
     * de l'étiquette « Tethered Network Services ».
     */
    var sources: List<String>
        get() = (prefs.getString(KEY_SOURCES, null) ?: DEFAULT_SOURCE_URL)
            .lines().map { it.trim() }.filter { it.isNotEmpty() }
        set(value) {
            val cleaned = value.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            prefs.edit().putString(KEY_SOURCES, cleaned.joinToString("\n")).apply()
            cached = null
            // Les fichiers devenus inutiles sont effacés, les autres gardent leur cache.
            val keep = cleaned.map { cacheFileFor(it).name }.toSet()
            app.filesDir.listFiles()
                ?.filter { it.name.startsWith(CACHE_PREFIX) && it.name !in keep }
                ?.forEach { it.delete() }
        }

    /**
     * Sélection explicite. Tant qu'elle n'a pas été amorcée (premier guide analysé),
     * toutes les chaînes passent ; ensuite elle fait foi, y compris vide — tout
     * décocher affiche donc une liste vide, ce qui est le comportement attendu.
     */
    var selectedChannels: Set<String>
        get() = prefs.getStringSet(KEY_CHANNELS, emptySet()).orEmpty().toSet()
        set(value) {
            prefs.edit()
                .putStringSet(KEY_CHANNELS, value.toSet())
                .putBoolean(KEY_CHANNELS_SET, true)
                .apply()
            cached = null
        }

    private val selectionInitialised: Boolean
        get() = prefs.getBoolean(KEY_CHANNELS_SET, false)

    /**
     * Affichage des vignettes. Chaque image est servie par l'éditeur d'origine
     * (programme-tv.net, bouygtel.fr…) : l'interrupteur coupe ces requêtes.
     */
    var showImages: Boolean
        get() = prefs.getBoolean(KEY_IMAGES, true)
        set(value) {
            prefs.edit().putBoolean(KEY_IMAGES, value).apply()
        }

    val lastUpdate: Long get() = prefs.getLong(KEY_LAST_UPDATE, 0L)

    /**
     * Toutes les chaînes présentes dans les fichiers téléchargés, sélection comprise
     * ou non : c'est la liste qu'affiche l'écran de réglages.
     */
    suspend fun availableChannels(): List<Channel> = withContext(Dispatchers.IO) {
        val channels = LinkedHashMap<String, Channel>()
        for (url in sources) {
            val file = cacheFileFor(url)
            if (!file.exists()) continue
            runCatching {
                openGuideStream(file).use { XmltvParser().parseChannels(it) }
            }.getOrNull()?.forEach { channel -> channels.putIfAbsent(channel.id, channel) }
        }
        channels.values
            .map { channel ->
                if (!channel.iconUrl.isNullOrBlank()) channel
                else channel.copy(iconUrl = ChannelLogos.get(app, channel.id, channel.name))
            }
            .sortedWith(compareBy({ it.number ?: Int.MAX_VALUE }, { it.name }))
    }

    /** Affichage immédiat au lancement, sans attendre le réseau. */
    suspend fun loadCached(): Guide? = withContext(Dispatchers.IO) {
        runCatching { parseAll() }.getOrNull()?.takeIf { it.programmeCount > 0 }
    }

    suspend fun refresh(force: Boolean = false): RefreshResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val sinceLastCheck = now - prefs.getLong(KEY_LAST_CHECK, 0L)
        val hasCache = sources.any { cacheFileFor(it).exists() }
        if (!force && hasCache && sinceLastCheck < MIN_CHECK_INTERVAL_MS) {
            return@withContext RefreshResult.Skipped
        }

        var bytes = 0L
        var fresh = false
        var failure: Throwable? = null

        for (url in sources) {
            try {
                when (val outcome = download(url)) {
                    is Download.NotModified -> Unit
                    is Download.Fresh -> {
                        fresh = true
                        bytes += outcome.bytes
                    }
                }
            } catch (e: Exception) {
                failure = e
            }
        }

        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

        when {
            fresh -> {
                prefs.edit().putLong(KEY_LAST_UPDATE, now).apply()
                var guide = parseAll()
                // Premier guide analysé : la sélection est figée sur ce qui vient
                // d'arriver, pour qu'une source ajoutée ensuite n'active pas d'office
                // ses milliers de chaînes.
                if (!selectionInitialised && guide.channels.isNotEmpty()) {
                    selectedChannels = guide.channels.map { it.id }.toSet()
                    guide = parseAll()
                }
                RefreshResult.Updated(guide, bytes)
            }
            failure != null && !hasCache -> RefreshResult.Failed(failure)
            failure != null -> RefreshResult.Failed(failure)
            else -> RefreshResult.NotModified
        }
    }

    /**
     * Fusionne les fichiers en cache en une seule grille, doublons écartés.
     *
     * Le résultat est gardé en mémoire pour la durée du processus : revenir dans
     * l'application ne relit pas 7 Mo de XML. La signature tient compte des fichiers,
     * de leur date et de la sélection de chaînes.
     */
    private fun parseAll(): Guide {
        val signature = sources.joinToString("|") { url ->
            val file = cacheFileFor(url)
            url + ":" + file.lastModified() + ":" + file.length()
        } + "#" + selectedChannels.hashCode()
        cached?.let { (key, guide) -> if (key == signature) return guide }

        val snapshot = File(app.filesDir, SNAPSHOT_NAME)
        val guide = GuideSnapshot.read(snapshot, signature)
            ?: parseAllUncached().also { GuideSnapshot.write(snapshot, signature, it) }
        cached = signature to guide
        return guide
    }

    private fun parseAllUncached(): Guide {
        val filter = if (selectionInitialised) selectedChannels else null
        // La veille reste utile pour une diffusion commencée avant minuit, pas au-delà.
        val horizon = java.time.Instant.now().minusSeconds(24 * 3600)
        // Au-delà de quatre jours, la donnée est rarement consultée et coûte en mémoire.
        val horizonEnd = java.time.Instant.now().plusSeconds(4 * 24 * 3600)
        val channels = LinkedHashMap<String, Channel>()
        val programmes = LinkedHashMap<String, Programme>()

        for (url in sources) {
            val file = cacheFileFor(url)
            if (!file.exists()) continue
            val guide = runCatching {
                openGuideStream(file).use {
                    XmltvParser(
                        keepChannels = filter,
                        notBefore = horizon,
                        notAfter = horizonEnd,
                    ).parse(it)
                }
            }.getOrNull() ?: continue

            guide.channels.forEach { channel -> channels.putIfAbsent(channel.id, channel) }
            guide.allProgrammes().forEach { programme ->
                programmes.putIfAbsent(programme.channelId + "@" + programme.start, programme)
            }
        }

        val completed = channels.values.map { channel ->
            if (!channel.iconUrl.isNullOrBlank()) channel
            else channel.copy(iconUrl = ChannelLogos.get(app, channel.id, channel.name))
        }
        return Guide(completed, programmes.values.toList())
    }

    /** Le format réel est décidé sur les octets, pas sur l'extension de l'URL. */
    private fun openGuideStream(file: File): InputStream {
        val raw = BufferedInputStream(FileInputStream(file), BUFFER)
        raw.mark(4)
        val head = ByteArray(4)
        val read = raw.read(head)
        raw.reset()
        return when {
            read >= 2 && head[0] == 0x1F.toByte() && head[1] == 0x8B.toByte() ->
                GZIPInputStream(raw, BUFFER)
            read >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() ->
                ZipInputStream(raw).also { it.nextEntry } // se placer sur la 1re entrée
            else -> raw // XML brut ; le .xz n'est pas géré sans bibliothèque tierce
        }
    }

    private fun cacheFileFor(url: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        return File(app.filesDir, CACHE_PREFIX + digest.joinToString("") { "%02x".format(it) })
    }

    private fun download(source: String): Download {
        var url = URL(source)
        var redirects = 0
        val cacheFile = cacheFileFor(source)
        val etagKey = KEY_ETAG + cacheFile.name
        val modifiedKey = KEY_LAST_MODIFIED + cacheFile.name

        while (true) {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                // On gère les redirections à la main : HttpURLConnection ne suit pas http <-> https.
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", USER_AGENT)
                // Le corps est déjà compressé : pas de gzip de transport en plus.
                setRequestProperty("Accept-Encoding", "identity")
                if (cacheFile.exists()) {
                    prefs.getString(etagKey, null)?.let { setRequestProperty("If-None-Match", it) }
                    prefs.getString(modifiedKey, null)?.let { setRequestProperty("If-Modified-Since", it) }
                }
            }

            try {
                when (val code = conn.responseCode) {
                    HttpURLConnection.HTTP_NOT_MODIFIED -> return Download.NotModified

                    HttpURLConnection.HTTP_OK -> {
                        val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
                        val bytes = conn.inputStream.use { input ->
                            FileOutputStream(tmp).use { out -> input.copyTo(out, BUFFER) }
                        }
                        if (!tmp.renameTo(cacheFile)) {
                            tmp.copyTo(cacheFile, overwrite = true)
                            tmp.delete()
                        }
                        prefs.edit()
                            .putString(etagKey, conn.getHeaderField("ETag"))
                            .putString(modifiedKey, conn.getHeaderField("Last-Modified"))
                            .apply()
                        return Download.Fresh(bytes)
                    }

                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    307, 308 -> {
                        val location = conn.getHeaderField("Location")
                            ?: throw IOException("Redirection sans en-tête Location")
                        if (++redirects > MAX_REDIRECTS) throw IOException("Trop de redirections")
                        url = URL(url, location)
                    }

                    else -> throw IOException("HTTP $code")
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    private sealed interface Download {
        data object NotModified : Download
        data class Fresh(val bytes: Long) : Download
    }

    companion object {

        /** Guide déjà analysé, partagé par toutes les instances du processus. */
        @Volatile
        private var cached: Pair<String, Guide>? = null

        /**
         * À VÉRIFIER : copier l'URL exacte du fichier TNT (.xml.gz) depuis
         * https://xmltvfr.fr/xmltv.php (bouton « Copier l'URL »).
         */
        const val DEFAULT_SOURCE_URL = "https://xmltvfr.fr/xmltv/xmltv_tnt.xml.gz"

        /**
         * Guide français du même service : les chaînes hors TNT sans la totalité du
         * catalogue mondial, donc bien plus léger que le fichier complet.
         */
        const val FULL_SOURCE_URL = "https://xmltvfr.fr/xmltv/xmltv_fr.xml.gz"

        /** Mets l'URL de ton dépôt : le mainteneur de la source doit pouvoir t'identifier. */
        const val USER_AGENT = "Lucarne/1.0 (+https://github.com/galacteos/Lucarne)"

        private const val PREFS = "guide"
        private const val KEY_SOURCES = "sources"
        private const val KEY_CHANNELS = "channels"
        private const val KEY_CHANNELS_SET = "channels_set"
        private const val KEY_IMAGES = "show_images"
        private const val KEY_ETAG = "etag_"
        private const val KEY_LAST_MODIFIED = "last_modified_"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_LAST_UPDATE = "last_update"
        private const val CACHE_PREFIX = "guide_"
        private const val SNAPSHOT_NAME = "guide-snapshot.bin"
        private const val MIN_CHECK_INTERVAL_MS = 15L * 60L * 1000L
        private const val MAX_REDIRECTS = 5
        private const val BUFFER = 64 * 1024
    }
}
