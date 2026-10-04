package fr.guidetv.xmltv

import java.text.Normalizer

/**
 * Numéros de canal de la TNT métropolitaine.
 *
 * Source : Arcom, numérotation des services nationaux en vigueur depuis le
 * 6 juin 2025 (services 1 à 26). À revoir à chaque décision de l'Arcom.
 *
 * Sert de repli : si le fichier XMLTV fournit lui-même un <display-name>
 * numérique, c'est lui qui gagne.
 */
object TntNumbering {

    private val byName: Map<String, Int> = mapOf(
        "tf1" to 1,
        "france2" to 2,
        "france3" to 3,
        "france4" to 4,
        "france5" to 5,
        "m6" to 6,
        "arte" to 7,
        "lcp" to 8,
        "lcpan" to 8,
        "publicsenat" to 8,
        "lcpassembleenationalepublicsenat" to 8,
        "w9" to 9,
        "tmc" to 10,
        "tfx" to 11,
        "gulli" to 12,
        "bfmtv" to 13,
        "cnews" to 14,
        "lci" to 15,
        "franceinfo" to 16,
        "cstar" to 17,
        "t18" to 18,
        "novo19" to 19,
        "tf1seriesfilms" to 20,
        "lequipe" to 21,
        "6ter" to 22,
        "rmcstory" to 23,
        "rmcdecouverte" to 24,
        "rmclife" to 25,
        "parispremiere" to 26,
    )

    fun forName(name: String): Int? = byName[normalize(name)]

    /** "L'Équipe" -> "lequipe", "franceinfo:" -> "franceinfo", "6Ter" -> "6ter". */
    internal fun normalize(raw: String): String =
        Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("[^a-z0-9]"), "")
}
