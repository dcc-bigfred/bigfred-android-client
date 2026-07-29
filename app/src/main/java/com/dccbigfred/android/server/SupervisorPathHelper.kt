package com.dccbigfred.android.server

import android.content.Context
import java.io.File

/**
 * Stage 2 scaffolding: expose supervisord/supervisorctl on PATH under [Context.getCodeCacheDir].
 * Stage 1 still runs loco-server with `--no-supervisor`.
 */
object SupervisorPathHelper {
    private const val BIN_SUBDIR = "bin"

    fun installSupervisorBinaries(context: Context): String? {
        val binDir = File(context.codeCacheDir, BIN_SUBDIR)
        binDir.mkdirs()
        var installed = false
        listOf(
            NativeBinaries.SUPERVISORD to "supervisord",
            NativeBinaries.SUPERVISORCTL to "supervisorctl",
        ).forEach { (libName, destName) ->
            val src = NativeBinaries.file(context, libName)
            if (!src.isFile) return@forEach
            copyExecutable(src, File(binDir, destName))
            installed = true
        }
        return if (installed) binDir.absolutePath else null
    }

    fun prependPath(existingPath: String?, binDir: String): String {
        val trimmed = binDir.trimEnd('/')
        return when {
            existingPath.isNullOrBlank() -> trimmed
            existingPath.split(':').any { it == trimmed } -> existingPath
            else -> "$trimmed:$existingPath"
        }
    }

    private fun copyExecutable(source: File, dest: File) {
        source.inputStream().use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest.setExecutable(true, false)
    }
}
