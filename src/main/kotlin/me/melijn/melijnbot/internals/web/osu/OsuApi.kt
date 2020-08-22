package me.melijn.melijnbot.internals.web.osu

import io.ktor.client.*
import io.ktor.client.request.*
import me.melijn.melijnbot.internals.translation.OSU_URL
import net.dv8tion.jda.api.utils.data.DataArray


class OsuApi(val httpClient: HttpClient, private val apiKey: String) {
    // k - api key (required).
    // u - specify a user_id or a username to return best scores from (required).
    // m - mode (0 = osu!, 1 = Taiko, 2 = CtB, 3 = osu!mania). Optional, default value is 0.
    // type - specify if u is a user_id or a username. Use string for usernames or id for user_ids. Optional, default behavior is automatic recognition (may be problematic for usernames made up of digits only).
    suspend fun getUserInfo(name: String, gameMode: Int = 0): OsuUser? {
        val result = httpClient.get<String>("$OSU_URL/get_user?k=$apiKey&u=$name&type=string&m=$gameMode")
        if (result.isEmpty()) return null
        val data = DataArray.fromJson(result)
        if (data.isEmpty) return null
        val jsonUser = data.getObject(0)

        return OsuUser(
            jsonUser.getString("user_id"),
            jsonUser.getString("username"),
            jsonUser.getString("join_date"),
            jsonUser.getString("playcount").toLong(),
            jsonUser.getString("level").toFloat(),
            jsonUser.getString("accuracy").toFloat(),
            jsonUser.getString("count_rank_ssh").toLong(),
            jsonUser.getString("count_rank_ss").toLong(),
            jsonUser.getString("count_rank_sh").toLong(),
            jsonUser.getString("count_rank_s").toLong(),
            jsonUser.getString("count_rank_a").toLong(),
            jsonUser.getString("country"),
            jsonUser.getString("total_seconds_played").toLong(),
            jsonUser.getString("pp_rank").toLong(),
            jsonUser.getString("pp_country_rank").toLong()
        )
    }

    // limit - amount of results (range between 1 and 100 - defaults to 10).
    suspend fun getUserTopPlays(name: String): List<OsuRankedScoreResult>? {
        val result = httpClient.get<String>("$OSU_URL/get_user_best?k=$apiKey&u=$name&type=string&limit=25")
        if (result.isEmpty()) return null

        val data = DataArray.fromJson(result)
        if (data.isEmpty) return null

        val list = mutableListOf<OsuRankedScoreResult>()

        for (i in 0 until data.length()) {
            val entry = data.getObject(i)
            list.add(OsuRankedScoreResult(
                entry.getString("beatmap_id").toLong(),
                entry.getString("score_id").toLong(),
                entry.getString("score").toLong(),
                entry.getString("maxcombo").toLong(),
                entry.getString("count50").toLong(),
                entry.getString("count100").toLong(),
                entry.getString("count300").toLong(),
                entry.getString("countmiss").toLong(),
                entry.getString("countkatu").toLong(),
                entry.getString("countgeki").toLong(),
                entry.getString("perfect").toBoolean(),
                entry.getString("enabled_mods").toInt(),
                entry.getString("user_id").toLong(),
                entry.getString("date"),
                entry.getString("rank"),
                entry.getString("pp").toFloat(),
                entry.getString("replay_available").toBoolean()
            ))
        }

        return list
    }

    suspend fun getBeatMap(beatmapId: Long): OsuBeatMap? {
        val result = httpClient.get<String>("$OSU_URL/get_beatmaps?k=$apiKey&b=$beatmapId&limit=1")
        if (result.isEmpty()) return null

        val data = DataArray.fromJson(result)
        if (data.isEmpty) return null

        val beatMapJson = data.getObject(0)

        return OsuBeatMap(
            beatMapJson.getString("approved").toInt(), // 4 = loved, 3 = qualified, 2 = approved, 1 = ranked, 0 = pending, -1 = WIP, -2 = graveyard
            beatMapJson.getString("submit_date"),
            beatMapJson.getString("approved_date"),
            beatMapJson.getString("last_update"),
            beatMapJson.getString("artist"), // Creater of music/song
            beatMapJson.getString("beatmap_id").toLong(),
            beatMapJson.getString("beatmapset_id").toLong(),
            beatMapJson.getString("bpm").toFloat().toInt(),
            beatMapJson.getString("creator"), // User who uploaded the map
            beatMapJson.getString("creator_id").toLong(),
            beatMapJson.getString("difficultyrating").toFloat(), // amount of stars
            beatMapJson.getString("diff_aim").toFloat(), // aim diff
            beatMapJson.getString("diff_speed").toFloat(), // speed diff
            beatMapJson.getString("diff_size").toFloat(), // size diff
            beatMapJson.getString("diff_approach").toFloat(), // approach diff
            beatMapJson.getString("diff_drain").toFloat(), // drain diff
            beatMapJson.getString("hit_length").toLong(), // time of gameplay, breaks excluded
            beatMapJson.getString("genre_id").toInt(), // 0 = any, 1 = unspecified, 2 = video game, 3 = anime, 4 = rock, 5 = pop, 6 = other, 7 = novelty, 9 = hip hop, 10 = electronic, 13 = folk (note that there's no 8)
            beatMapJson.getString("language_id").toInt(), // 0 = any, 1 = other, 2 = english, 3 = japanese, 4 = chinese, 5 = instrumental, 6 = korean, 7 = french, 8 = german, 9 = swedish, 10 = spanish, 11 = italian
            beatMapJson.getString("title"),
            beatMapJson.getString("total_length").toLong(), // first until last note, including breaks
            beatMapJson.getString("version"), // difficulty name
            beatMapJson.getString("mode").toInt(),
            beatMapJson.getString("favourite_count").toInt(),
            beatMapJson.getString("rating").toFloat(), // x/10 rating by community
            beatMapJson.getString("playcount").toLong(),
            beatMapJson.getString("passcount").toLong(),
            beatMapJson.getString("count_normal").toLong(),
            beatMapJson.getString("count_slider").toLong(),
            beatMapJson.getString("count_spinner").toLong(),
            beatMapJson.getString("max_combo").toLong(),
            beatMapJson.getString("storyboard").toBoolean(),
            beatMapJson.getString("video").toBoolean(),
            beatMapJson.getString("download_unavailable").toBoolean(), // really old maps
            beatMapJson.getString("audio_unavailable").toBoolean() // dmca ect
        )

    }

    suspend fun getUserRecentPlays(user: String): List<OsuScoreResult>? {
        val result = httpClient.get<String>("$OSU_URL/get_user_recent?k=$apiKey&u=$user&type=string&limit=25")
        if (result.isEmpty()) return null

        val data = DataArray.fromJson(result)
        if (data.isEmpty) return null

        val list = mutableListOf<OsuScoreResult>()

        for (i in 0 until data.length()) {
            val entry = data.getObject(i)
            list.add(OsuScoreResult(
                entry.getString("beatmap_id").toLong(),
                entry.getString("score").toLong(),
                entry.getString("maxcombo").toLong(),
                entry.getString("count50").toLong(),
                entry.getString("count100").toLong(),
                entry.getString("count300").toLong(),
                entry.getString("countmiss").toLong(),
                entry.getString("countkatu").toLong(),
                entry.getString("countgeki").toLong(),
                entry.getString("perfect").toBoolean(),
                entry.getString("enabled_mods").toInt(),
                entry.getString("user_id").toLong(),
                entry.getString("date"),
                entry.getString("rank")
            ))
        }

        return list
    }
}

data class OsuBeatMap(
    val approved: Int,
    val submitDate: String,
    val approvedDate: String,
    val lastUpdateDate: String,
    val artist: String,
    val beatMapId: Long,
    val beatMapSetId: Long,
    val bpm: Int,
    val creator: String,
    val creatorId: Long,
    val difficulty: Float,
    val aimDifficulty: Float,
    val speedDifficulty: Float,
    val sizeDifficulty: Float,
    val approachDifficulty: Float,
    val drainDifficulty: Float,
    val playTime: Long,
    val genre: Int,
    val language: Int,
    val title: String,
    val length: Long,
    val version: String,
    val mode: Int,
    val favourites: Int,
    val rating: Float,
    val playCount: Long,
    val passCount: Long,
    val normalCount: Long,
    val sliderCount: Long,
    val spinnerCount: Long,
    val maxCombo: Long,
    val story: Boolean,
    val video: Boolean,
    val noDownload: Boolean,
    val noAudio: Boolean
)

data class OsuRankedScoreResult(
    val beatmapId: Long,
    val scoreId: Long,
    val score: Long,
    val maxCombo: Long,
    val count50: Long,
    val count100: Long,
    val count300: Long,
    val countMiss: Long,
    val countKatu: Long,
    val countGeki: Long,
    val perfect: Boolean,
    val mods: Int,
    val userId: Long,
    val date: String,
    val rank: String,
    val pp: Float,
    val replay: Boolean
)

data class OsuScoreResult(
    val beatmapId: Long,
    val score: Long,
    val maxCombo: Long,
    val count50: Long,
    val count100: Long,
    val count300: Long,
    val countMiss: Long,
    val countKatu: Long,
    val countGeki: Long,
    val perfect: Boolean,
    val mods: Int,
    val userId: Long,
    val date: String,
    val rank: String
)

data class OsuUser(
    val id: String,
    val username: String,
    val joinDate: String,
    val plays: Long,
    val level: Float,
    val acc: Float,
    val countSSH: Long,
    val countSS: Long,
    val countSH: Long,
    val countS: Long,
    val countA: Long,
    val country: String,
    val playtime: Long,
    val rank: Long,
    val localRank: Long
)