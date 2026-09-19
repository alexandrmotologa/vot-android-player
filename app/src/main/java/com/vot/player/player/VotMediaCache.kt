package com.vot.player.player

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

object VotMediaCache {
    private const val CACHE_SIZE_BYTES = 100 * 1024 * 1024L // 100 MB LRU cache
    private const val CACHE_DIR_NAME = "media_cache"

    @Volatile
    private var instance: SimpleCache? = null

    fun getCache(context: Context): SimpleCache {
        return instance ?: synchronized(this) {
            instance ?: run {
                val cacheDir = File(context.applicationContext.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
                val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
                val evictor = LeastRecentlyUsedCacheEvictor(CACHE_SIZE_BYTES)
                SimpleCache(cacheDir, evictor, databaseProvider).also { instance = it }
            }
        }
    }
}
