package com.dccbigfred.android.server

import android.content.Context
import android.content.pm.ApplicationInfo
import java.io.File

/** Resolves extracted jniLibs executables under [ApplicationInfo.nativeLibraryDir]. */
object NativeBinaries {
    const val LOCO_SERVER = "libloco-server.so"
    const val VALKEY = "libvalkey-server.so"
    const val MICROINIT = "libmicroinit.so"

    fun dir(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir)

    fun file(context: Context, name: String): File =
        File(dir(context), name)

    fun require(context: Context, name: String): File {
        val f = file(context, name)
        if (!f.isFile) {
            throw IllegalStateException("Native binary missing: ${f.absolutePath}")
        }
        return f
    }
}
