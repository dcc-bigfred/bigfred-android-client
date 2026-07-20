package com.dccbigfred.android.models

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Copies the prebuilt catalog from assets and opens it read-only.
 */
class ModelsDatabase private constructor(
    private val db: SQLiteDatabase,
) {
    fun readable(): SQLiteDatabase = db

    fun close() {
        db.close()
    }

    companion object {
        private const val ASSET_DB = "models/models.db"
        private const val DB_NAME = "models.db"

        @Volatile
        private var instance: ModelsDatabase? = null

        fun get(context: Context): ModelsDatabase {
            return instance ?: synchronized(this) {
                instance ?: open(context.applicationContext).also { instance = it }
            }
        }

        private fun open(context: Context): ModelsDatabase {
            val dest = context.getDatabasePath(DB_NAME)
            dest.parentFile?.mkdirs()
            ensureCopied(context, dest)
            val db = SQLiteDatabase.openDatabase(
                dest.path,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            return ModelsDatabase(db)
        }

        private fun ensureCopied(context: Context, dest: File) {
            val assetVersion = readAssetUserVersion(context)
            val needCopy = !dest.exists() ||
                dest.length() == 0L ||
                readFileUserVersion(dest) != assetVersion
            if (!needCopy) return

            dest.delete()
            context.assets.open(ASSET_DB).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
        }

        /** SQLite user_version is a big-endian int at header offset 60. */
        private fun readAssetUserVersion(context: Context): Int {
            context.assets.open(ASSET_DB).use { input ->
                val header = ByteArray(100)
                val read = input.read(header)
                if (read < 64) return -1
                return ByteBuffer.wrap(header, 60, 4)
                    .order(ByteOrder.BIG_ENDIAN)
                    .int
            }
        }

        private fun readFileUserVersion(file: File): Int {
            if (!file.exists() || file.length() < 100L) return -1
            return file.inputStream().use { input ->
                val header = ByteArray(100)
                val read = input.read(header)
                if (read < 64) return@use -1
                ByteBuffer.wrap(header, 60, 4)
                    .order(ByteOrder.BIG_ENDIAN)
                    .int
            }
        }
    }
}
