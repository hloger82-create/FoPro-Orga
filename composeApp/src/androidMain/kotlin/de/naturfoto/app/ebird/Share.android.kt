package de.naturfoto.app.ebird

import android.content.Intent
import androidx.core.content.FileProvider
import de.naturfoto.app.data.Supabase
import java.io.File

actual fun shareCsvFile(fileName: String, content: String) {
    val ctx = currentContext ?: return
    val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, fileName)
    file.writeText(content)
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(intent, "eBird-CSV teilen"))
}

internal object ContextHolder {
    var ctx: android.content.Context? = null
}

internal val currentContext: android.content.Context? get() = ContextHolder.ctx
