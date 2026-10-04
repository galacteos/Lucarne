package io.github.galacteos.lucarne.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.galacteos.lucarne.data.GuideRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Chargeur d'images minimal : cache mémoire (LRU) + cache disque, décodage
 * sous-échantillonné. Pas de dépendance tierce, pour rester cohérent avec le
 * reste du projet. Si tu veux annulation fine, animations et gestion fine du
 * réseau, c'est le moment de passer à Coil (Apache 2.0) et de supprimer ce
 * fichier : `io.coil-kt:coil-compose`.
 *
 * Note vie privée : chaque vignette est une requête chez l'éditeur qui héberge
 * l'image (programme-tv.net, bouygtel.fr…). D'où l'interrupteur dans l'appli.
 */
object ThumbnailLoader {

    private const val TAG = "ThumbnailLoader"

    private val memory = object : LruCache<String, Bitmap>(
        ((Runtime.getRuntime().maxMemory() / 1024L) / 8L).toInt().coerceAtLeast(4 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun load(context: Context, url: String, targetWidthPx: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            memory.get(url)?.let { return@withContext it }
            val file = cacheFile(context, url)
            val bitmap = runCatching {
                if (!file.exists() || file.length() == 0L) download(url, file)
                decode(file, targetWidthPx)
            }.onFailure {
                // Visible dans Logcat : distingue un 403 de l'éditeur d'une URL absente.
                Log.w(TAG, "Image non chargée : " + url, it)
                file.delete()
            }.getOrNull()
            bitmap?.also { memory.put(url, it) }
        }

    private fun cacheFile(context: Context, url: String): File {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val digest = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        return File(dir, digest.joinToString("") { "%02x".format(it) })
    }

    private fun download(url: String, target: File) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", GuideRepository.USER_AGENT)
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) error("HTTP " + conn.responseCode)
            val tmp = File(target.path + ".tmp")
            conn.inputStream.use { input -> FileOutputStream(tmp).use { out -> input.copyTo(out, 32 * 1024) } }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } finally {
            conn.disconnect()
        }
        trim(target.parentFile)
    }

    /** Garde le cache disque sous ~20 Mo, en supprimant les fichiers les plus anciens. */
    private fun trim(dir: File?, maxBytes: Long = 20L * 1024 * 1024) {
        val files = dir?.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= maxBytes) break
            total -= file.length()
            file.delete()
        }
    }

    private fun decode(file: File, targetWidthPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        val target = targetWidthPx.coerceAtLeast(1)
        while (bounds.outWidth / (sample * 2) >= target) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

/** Image distante, avec un simple aplat en attendant le décodage. */
@Composable
fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    targetWidth: Dp = 320.dp,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackUrl: String? = null,
    onFallbackUsed: (Boolean) -> Unit = {},
    placeholder: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val targetPx = with(LocalDensity.current) { targetWidth.roundToPx() }
    val bitmap by produceState<Bitmap?>(initialValue = null, url, fallbackUrl, targetPx) {
        // Une URL présente mais cassée est fréquente dans les guides : on retombe
        // alors sur le logo de la chaîne plutôt que d'afficher un cadre vide.
        val first = url?.takeIf { it.isNotBlank() }
            ?.let { ThumbnailLoader.load(context, it, targetPx) }
        if (first != null) {
            onFallbackUsed(false)
            value = first
        } else {
            val second = fallbackUrl?.takeIf { it.isNotBlank() }
                ?.let { ThumbnailLoader.load(context, it, targetPx) }
            onFallbackUsed(second != null)
            value = second
        }
    }

    Box(modifier) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            // URL absente, téléchargement en cours ou échec : le repli occupe la place.
            placeholder()
        }
    }
}
