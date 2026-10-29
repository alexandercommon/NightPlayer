package app.nightplayer

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.util.Locale

data class Track(val uri: Uri, val name: String)

private val audioExtensions = setOf("mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "flac", "wav", "wma", "mka", "amr")

fun scanFolder(context: Context, tree: Uri): List<Track> {
    val docId = DocumentsContract.getTreeDocumentId(tree)
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE
    )
    val result = mutableListOf<Track>()
    context.contentResolver.query(children, projection, null, null, null)?.use { c ->
        while (c.moveToNext()) {
            val id = c.getString(0) ?: continue
            val name = c.getString(1) ?: continue
            val mime = c.getString(2) ?: ""
            val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
            if (mime.startsWith("audio/") || ext in audioExtensions) {
                result.add(Track(DocumentsContract.buildDocumentUriUsingTree(tree, id), name))
            }
        }
    }
    return result.sortedWith { a, b -> naturalCompare(a.name, b.name) }
}

fun folderName(context: Context, tree: Uri): String {
    return try {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: ""
    } catch (e: Exception) {
        ""
    }
}

private val chunk = Regex("\\d+|\\D+")

fun naturalCompare(a: String, b: String): Int {
    val pa = chunk.findAll(a.lowercase(Locale.US)).map { it.value }.toList()
    val pb = chunk.findAll(b.lowercase(Locale.US)).map { it.value }.toList()
    for (i in 0 until minOf(pa.size, pb.size)) {
        val x = pa[i]
        val y = pb[i]
        val c = if (x[0].isDigit() && y[0].isDigit()) {
            val nx = x.trimStart('0')
            val ny = y.trimStart('0')
            if (nx.length != ny.length) nx.length - ny.length else nx.compareTo(ny)
        } else {
            x.compareTo(y)
        }
        if (c != 0) return c
    }
    return pa.size - pb.size
}

fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}

fun formatSpeed(v: Float): String = String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.') + "×"

fun roundSpeed(v: Float): Float = (Math.round(v * 20f) / 20f).coerceIn(0.5f, 2f)
