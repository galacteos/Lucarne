package io.github.galacteos.lucarne.data

import io.github.galacteos.lucarne.xmltv.Channel
import io.github.galacteos.lucarne.xmltv.Guide
import io.github.galacteos.lucarne.xmltv.Programme
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant

/**
 * Instantané binaire de la grille déjà analysée.
 *
 * Analyser le XMLTV coûte cher — quelques secondes sur le guide complet — et le
 * résultat ne change qu'au téléchargement suivant. On le relit donc depuis un fichier
 * compact, écrit après chaque analyse. La signature (sources, dates des fichiers,
 * sélection de chaînes) garantit qu'un instantané périmé est ignoré.
 */
internal object GuideSnapshot {

    private const val MAGIC = 0x4C554331 // "LUC1"
    private const val VERSION = 1

    fun read(file: File, signature: String): Guide? {
        if (!file.exists()) return null
        return runCatching {
            DataInputStream(BufferedInputStream(FileInputStream(file), 64 * 1024)).use { input ->
                if (input.readInt() != MAGIC) return null
                if (input.readInt() != VERSION) return null
                if (input.readUTF() != signature) return null

                val channelCount = input.readInt()
                val channels = ArrayList<Channel>(channelCount)
                repeat(channelCount) {
                    val id = input.readUTF()
                    val name = input.readUTF()
                    val number = input.readInt().takeIf { value -> value > 0 }
                    val icon = input.readUTF().takeIf { value -> value.isNotEmpty() }
                    channels.add(Channel(id = id, name = name, number = number, iconUrl = icon))
                }

                val programmeCount = input.readInt()
                val programmes = ArrayList<Programme>(programmeCount)
                repeat(programmeCount) {
                    val channelIndex = input.readInt()
                    val start = Instant.ofEpochMilli(input.readLong())
                    val stopMillis = input.readLong()
                    val title = input.readUTF()
                    val subCount = input.readInt()
                    val subTitles = if (subCount <= 0) emptyList() else List(subCount) { input.readUTF() }
                    val episode = input.readUTF()
                    val description = input.readUTF()
                    val category = input.readUTF()
                    val icon = input.readUTF()
                    programmes.add(
                        Programme(
                            channelId = channels[channelIndex].id,
                            start = start,
                            stop = stopMillis.takeIf { value -> value > 0 }
                                ?.let(Instant::ofEpochMilli),
                            title = title,
                            subTitles = subTitles,
                            episode = episode.takeIf { value -> value.isNotEmpty() },
                            description = description.takeIf { value -> value.isNotEmpty() },
                            category = category.takeIf { value -> value.isNotEmpty() },
                            iconUrl = icon.takeIf { value -> value.isNotEmpty() },
                        ),
                    )
                }
                Guide(channels, programmes)
            }
        }.getOrNull()
    }

    fun write(file: File, signature: String, guide: Guide) {
        runCatching {
            val temp = File(file.path + ".tmp")
            val indexOf = guide.channels.withIndex().associate { (index, channel) -> channel.id to index }
            DataOutputStream(BufferedOutputStream(FileOutputStream(temp), 64 * 1024)).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(VERSION)
                out.writeUTF(signature)

                out.writeInt(guide.channels.size)
                guide.channels.forEach { channel ->
                    out.writeUTF(channel.id)
                    out.writeUTF(channel.name)
                    out.writeInt(channel.number ?: 0)
                    out.writeUTF(channel.iconUrl.orEmpty())
                }

                val programmes = guide.allProgrammes()
                out.writeInt(programmes.size)
                programmes.forEach { programme ->
                    out.writeInt(indexOf[programme.channelId] ?: 0)
                    out.writeLong(programme.start.toEpochMilli())
                    out.writeLong(programme.stop?.toEpochMilli() ?: -1L)
                    out.writeUTF(programme.title.take(MAX_TEXT))
                    out.writeInt(programme.subTitles.size)
                    programme.subTitles.forEach { out.writeUTF(it.take(MAX_TEXT)) }
                    out.writeUTF(programme.episode.orEmpty().take(MAX_TEXT))
                    out.writeUTF(programme.description.orEmpty().take(MAX_TEXT))
                    out.writeUTF(programme.category.orEmpty().take(MAX_TEXT))
                    out.writeUTF(programme.iconUrl.orEmpty().take(MAX_TEXT))
                }
            }
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        }
    }

    /** writeUTF plafonne à 64 Ko par chaîne ; aucun résumé n'en approche. */
    private const val MAX_TEXT = 8000
}
