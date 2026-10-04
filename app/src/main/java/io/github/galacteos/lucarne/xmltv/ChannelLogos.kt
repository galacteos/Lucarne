package io.github.galacteos.lucarne.xmltv

import android.content.Context
import android.util.JsonReader
import io.github.galacteos.lucarne.R

/**
 * Table de logos embarquée, extraite des informations de chaînes du projet XML TV Fr
 * (2124 entrées). Elle sert de repli : si le fichier XMLTV ne porte pas d'icône pour
 * une chaîne, on retombe ici.
 *
 * Les visuels restent hébergés par leurs éditeurs ; seule la correspondance
 * identifiant → URL est embarquée.
 */
object ChannelLogos {

    /** Préfixe commun à la plupart des URL, retiré du fichier pour l'alléger. */
    private const val COMMON_PREFIX = "https://www.programme-tv.net/imgre/fit/~2~channel~"

    @Volatile
    private var cache: Map<String, String>? = null

    private fun expand(value: String): String =
        if (value.startsWith("~")) COMMON_PREFIX + value.substring(1) else value

    /**
     * Recherche par identifiant, puis par nom normalisé : le générateur écrit l'alias
     * de la chaîne dans le fichier, qui ne correspond pas toujours à sa clé interne.
     */
    fun get(context: Context, channelId: String, channelName: String? = null): String? {
        val table = load(context)
        table[channelId]?.let { return it }
        val name = channelName ?: return null
        return table[TntNumbering.normalize(name)]
    }

    private fun load(context: Context): Map<String, String> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val map = HashMap<String, String>(2300)
            runCatching {
                context.resources.openRawResource(R.raw.channel_logos).use { stream ->
                    JsonReader(stream.reader()).use { reader ->
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val key = reader.nextName()
                            val value = reader.nextString()
                            if (value.isNotBlank()) map[key] = expand(value)
                        }
                        reader.endObject()
                    }
                }
            }
            cache = map
            return map
        }
    }
}
