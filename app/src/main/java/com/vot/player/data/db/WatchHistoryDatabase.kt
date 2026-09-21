package com.vot.player.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WatchHistoryItem(
    val videoId: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String,
    val lastPositionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis(),
    val preferredVoice: String = "standard",
    val originalVolume: Float = 0.20f,
    val voiceVolume: Float = 1.0f
)

class WatchHistoryDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "vot_watch_history.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_HISTORY = "watch_history"

        private const val COL_VIDEO_ID = "video_id"
        private const val COL_URL = "url"
        private const val COL_TITLE = "title"
        private const val COL_THUMBNAIL = "thumbnail_url"
        private const val COL_POSITION = "last_position_ms"
        private const val COL_DURATION = "duration_ms"
        private const val COL_UPDATED_AT = "updated_at"
        private const val COL_VOICE = "preferred_voice"
        private const val COL_ORIG_VOL = "original_volume"
        private const val COL_VOICE_VOL = "voice_volume"

        @Volatile
        private var instance: WatchHistoryDatabase? = null

        fun getInstance(context: Context): WatchHistoryDatabase {
            return instance ?: synchronized(this) {
                instance ?: WatchHistoryDatabase(context).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableSql = """
            CREATE TABLE $TABLE_HISTORY (
                $COL_VIDEO_ID TEXT PRIMARY KEY,
                $COL_URL TEXT NOT NULL,
                $COL_TITLE TEXT NOT NULL,
                $COL_THUMBNAIL TEXT NOT NULL,
                $COL_POSITION INTEGER NOT NULL DEFAULT 0,
                $COL_DURATION INTEGER NOT NULL DEFAULT 0,
                $COL_UPDATED_AT INTEGER NOT NULL,
                $COL_VOICE TEXT NOT NULL DEFAULT 'standard',
                $COL_ORIG_VOL REAL NOT NULL DEFAULT 0.20,
                $COL_VOICE_VOL REAL NOT NULL DEFAULT 1.0
            )
        """.trimIndent()
        db.execSQL(createTableSql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        onCreate(db)
    }

    suspend fun saveOrUpdate(item: WatchHistoryItem) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val existing = getHistoryItem(item.videoId)
        val finalPosition = if (item.lastPositionMs > 0L) {
            item.lastPositionMs
        } else {
            existing?.lastPositionMs ?: 0L
        }
        val finalDuration = if (item.durationMs > 0L) {
            item.durationMs
        } else {
            existing?.durationMs ?: 0L
        }
        val values = ContentValues().apply {
            put(COL_VIDEO_ID, item.videoId)
            put(COL_URL, item.url)
            put(COL_TITLE, item.title)
            put(COL_THUMBNAIL, item.thumbnailUrl)
            put(COL_POSITION, finalPosition)
            put(COL_DURATION, finalDuration)
            put(COL_UPDATED_AT, item.updatedAt)
            put(COL_VOICE, item.preferredVoice)
            put(COL_ORIG_VOL, item.originalVolume)
            put(COL_VOICE_VOL, item.voiceVolume)
        }
        db.insertWithOnConflict(TABLE_HISTORY, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun updatePosition(videoId: String, positionMs: Long, durationMs: Long) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_POSITION, positionMs)
            if (durationMs > 0) {
                put(COL_DURATION, durationMs)
            }
            put(COL_UPDATED_AT, System.currentTimeMillis())
        }
        val rows = db.update(TABLE_HISTORY, values, "$COL_VIDEO_ID = ?", arrayOf(videoId))
        if (rows == 0 && positionMs > 0L) {
            values.put(COL_VIDEO_ID, videoId)
            values.put(COL_URL, "https://www.youtube.com/watch?v=$videoId")
            values.put(COL_TITLE, "YouTube Video")
            values.put(COL_THUMBNAIL, "https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
            values.put(COL_VOICE, "standard")
            values.put(COL_ORIG_VOL, 0.20f)
            values.put(COL_VOICE_VOL, 1.0f)
            db.insertWithOnConflict(TABLE_HISTORY, null, values, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    suspend fun getHistoryItem(videoId: String): WatchHistoryItem? = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_HISTORY,
            null,
            "$COL_VIDEO_ID = ?",
            arrayOf(videoId),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                return@withContext WatchHistoryItem(
                    videoId = it.getString(it.getColumnIndexOrThrow(COL_VIDEO_ID)),
                    url = it.getString(it.getColumnIndexOrThrow(COL_URL)),
                    title = it.getString(it.getColumnIndexOrThrow(COL_TITLE)),
                    thumbnailUrl = it.getString(it.getColumnIndexOrThrow(COL_THUMBNAIL)),
                    lastPositionMs = it.getLong(it.getColumnIndexOrThrow(COL_POSITION)),
                    durationMs = it.getLong(it.getColumnIndexOrThrow(COL_DURATION)),
                    updatedAt = it.getLong(it.getColumnIndexOrThrow(COL_UPDATED_AT)),
                    preferredVoice = it.getString(it.getColumnIndexOrThrow(COL_VOICE)),
                    originalVolume = it.getFloat(it.getColumnIndexOrThrow(COL_ORIG_VOL)),
                    voiceVolume = it.getFloat(it.getColumnIndexOrThrow(COL_VOICE_VOL))
                )
            }
        }
        null
    }

    suspend fun getAllHistory(): List<WatchHistoryItem> = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val items = mutableListOf<WatchHistoryItem>()
        val cursor = db.query(
            TABLE_HISTORY,
            null,
            null,
            null,
            null,
            null,
            "$COL_UPDATED_AT DESC",
            "50"
        )
        cursor.use {
            while (it.moveToNext()) {
                items.add(
                    WatchHistoryItem(
                        videoId = it.getString(it.getColumnIndexOrThrow(COL_VIDEO_ID)),
                        url = it.getString(it.getColumnIndexOrThrow(COL_URL)),
                        title = it.getString(it.getColumnIndexOrThrow(COL_TITLE)),
                        thumbnailUrl = it.getString(it.getColumnIndexOrThrow(COL_THUMBNAIL)),
                        lastPositionMs = it.getLong(it.getColumnIndexOrThrow(COL_POSITION)),
                        durationMs = it.getLong(it.getColumnIndexOrThrow(COL_DURATION)),
                        updatedAt = it.getLong(it.getColumnIndexOrThrow(COL_UPDATED_AT)),
                        preferredVoice = it.getString(it.getColumnIndexOrThrow(COL_VOICE)),
                        originalVolume = it.getFloat(it.getColumnIndexOrThrow(COL_ORIG_VOL)),
                        voiceVolume = it.getFloat(it.getColumnIndexOrThrow(COL_VOICE_VOL))
                    )
                )
            }
        }
        items
    }

    suspend fun deleteItem(videoId: String) = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_HISTORY, "$COL_VIDEO_ID = ?", arrayOf(videoId))
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_HISTORY, null, null)
    }
}
