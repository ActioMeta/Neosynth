package com.example.neosynth.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
object MediaCacheManager {
    private var cache: Cache? = null
    private var databaseProvider: StandaloneDatabaseProvider? = null

    @Synchronized
    fun getCache(context: Context, limitBytes: Long = 1024L * 1024 * 1024): Cache { // Default 1GB
        if (cache == null) {
            val cacheDir = File(context.applicationContext.cacheDir, "media_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val dbProvider = StandaloneDatabaseProvider(context.applicationContext)
            databaseProvider = dbProvider
            val evictor = LeastRecentlyUsedCacheEvictor(limitBytes)
            cache = SimpleCache(cacheDir, evictor, dbProvider)
        }
        return cache!!
    }
}
