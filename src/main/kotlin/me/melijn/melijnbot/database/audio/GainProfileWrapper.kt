package me.melijn.melijnbot.database.audio

import com.google.common.cache.CacheBuilder
import kotlinx.coroutines.future.await
import me.melijn.melijnbot.database.IMPORTANT_CACHE
import me.melijn.melijnbot.internals.threading.TaskManager
import me.melijn.melijnbot.internals.utils.loadingCacheFrom
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class GainProfileWrapper(private val gainProfileDao: GainProfileDao) {

    val gainProfileCache = CacheBuilder.newBuilder()
        .expireAfterAccess(IMPORTANT_CACHE, TimeUnit.MINUTES)
        .build(loadingCacheFrom<Long, Map<String, GainProfile>> { id ->
            getGainProfile(id)
        })

    private fun getGainProfile(id: Long): CompletableFuture<Map<String, GainProfile>> {
        val future = CompletableFuture<Map<String, GainProfile>>()

       TaskManager.async {
            val profileMap = gainProfileDao.get(id)
            future.complete(profileMap)
        }

        return future
    }

    suspend fun add(id: Long, name: String, bands: FloatArray) {
        val map = gainProfileCache[id].await().toMutableMap()
        map[name] = GainProfile.fromString(bands.joinToString(","))

        gainProfileCache.put(id, CompletableFuture.completedFuture(map))
        gainProfileDao.insert(id, name, bands.joinToString(","))
    }

    suspend fun remove(guildId: Long, profileName: String) {
        val map = gainProfileCache.get(guildId).await().toMutableMap()
        map.remove(profileName)
        gainProfileDao.delete(guildId, profileName)
        gainProfileCache.put(guildId, CompletableFuture.completedFuture(map))
    }
}