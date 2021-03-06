package me.melijn.melijnbot.database.socialmedia

import me.melijn.melijnbot.database.CacheDBDao
import me.melijn.melijnbot.database.DriverManager
import me.melijn.melijnbot.internals.services.twitter.TweetInfo
import me.melijn.melijnbot.internals.utils.splitIETEL
import java.sql.ResultSet
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class TwitterDao(driverManager: DriverManager) : CacheDBDao(driverManager) {

    override val table: String = "twitter_webhooks"
    override val tableStructure: String = "guild_id bigint, webhook_url varchar(256), excluded_tweet_types varchar(16)," +
        " handle varchar(16), twitter_user_id bigint, monthly_tweet_count bigint, last_tweet_id bigint," +
        " last_tweet_time bigint, month_start bigint, enabled boolean"
    override val primaryKey: String = "guild_id, handle"

    override val cacheName: String = "twitter"

    init {
        driverManager.registerTable(table, tableStructure, primaryKey)
    }

    fun store(twitterWebhook: TwitterWebhook) = twitterWebhook.apply {
        val excludedTypes = excludedTweetTypes.joinToString(",") { it.id.toString() }
        driverManager.executeUpdate(
            "INSERT INTO $table (guild_id, webhook_url, excluded_tweet_types," +
                " handle, twitter_user_id, monthly_tweet_count, last_tweet_id, " +
                " last_tweet_time, month_start, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT ($primaryKey)" +
                " DO UPDATE SET webhook_url = ?, excluded_tweet_types = ?, twitter_user_id = ?, monthly_tweet_count = ?," +
                " last_tweet_id = ?, last_tweet_time = ?, month_start = ?, enabled = ?",
            guildId, webhookUrl,
            excludedTypes, handle, twitterUserId, monthlyTweetCount, lastTweetId,
            lastTweetTime, monthStart, enabled,

            // UPDATE SET:
            webhookUrl, excludedTypes, twitterUserId, monthlyTweetCount, lastTweetId, lastTweetTime, monthStart,
            enabled
        )
    }

    suspend fun getAll(): List<TwitterWebhook> = suspendCoroutine {
        driverManager.executeQuery(
            "SELECT * FROM $table WHERE enabled = ?", handleWebhookResults(it),
            true
        )
    }

    suspend fun getAll(guildId: Long): List<TwitterWebhook> = suspendCoroutine {
        driverManager.executeQuery(
            "SELECT * FROM $table WHERE guild_id = ? AND enabled = ?", handleWebhookResults(it),
            guildId, true
        )
    }

    private fun handleWebhookResults(it: Continuation<List<TwitterWebhook>>) = { rs: ResultSet ->
        val webhooks = mutableListOf<TwitterWebhook>()

        while (rs.next()) {
            webhooks.add(
                TwitterWebhook(
                    rs.getLong("guild_id"),
                    rs.getString("webhook_url"),
                    rs.getString("excluded_tweet_types").splitIETEL(",").mapNotNull {
                        TweetInfo.TweetType.from(it.toInt())
                    }.toSet(),
                    rs.getString("handle"),
                    rs.getLong("twitter_user_id"),
                    rs.getLong("monthly_tweet_count"),
                    rs.getLong("last_tweet_id"),
                    rs.getLong("last_tweet_time"),
                    rs.getLong("month_start"),
                    rs.getBoolean("enabled")
                )
            )
        }

        it.resume(webhooks)
    }

    fun delete(guildId: Long, handle: String) {
        driverManager.executeUpdate(
            "DELETE FROM $table WHERE guild_id = ? AND handle = ?",
            guildId, handle
        )
    }
}

data class TwitterWebhook(
    val guildId: Long,
    val webhookUrl: String,
    var excludedTweetTypes: Set<TweetInfo.TweetType>,
    val handle: String,
    val twitterUserId: Long,
    var monthlyTweetCount: Long,
    var lastTweetId: Long,
    var lastTweetTime: Long,
    val monthStart: Long,
    val enabled: Boolean
)
