package me.melijn.melijnbot.internals

import io.github.cdimascio.dotenv.dotenv
import me.melijn.melijnbot.enums.Environment
import me.melijn.melijnbot.internals.utils.splitIETEL
import net.dv8tion.jda.api.utils.data.DataArray


data class Settings(
    val botInfo: BotInfo,
    val restServer: RestServer,
    val api: Api,
    val environment: Environment,
    val lavalink: Lavalink,
    val tokens: Token,
    val database: Database,
    val redis: Redis,
    val unLoggedThreads: Array<String>
) {

    data class BotInfo(
        val prefix: String,
        val id: Long,
        val shardCount: Int,
        val embedColor: Int,
        val exceptionChannel: Long,
        val developerIds: LongArray
    )

    data class RestServer(
        val port: Int,
        val token: String
    )

    data class Api(
        val melijnCDN: MelijnCDN,
        val jikan: Jikan,
        val spotify: Spotify,
        val imgHoard: ImgHoard
    ) {
        data class Spotify(
            var clientId: String,
            var password: String
        )

        data class Jikan(
            var ssl: Boolean,
            var host: String,
            var port: Int
        )

        data class MelijnCDN(
            var token: String
        )

        data class ImgHoard(
            var token: String
        )
    }


    data class Lavalink(
        var http_nodes: Array<LLNode>,
        var verified_nodes: Array<LLNode>,
        var enabled: Boolean
    ) {
        data class LLNode(val groupId: String, val host: String, val pass: String)
    }

    data class Token(
        var discord: String,
        var topDotGG: String,
        var weebSh: String,
        var botsOnDiscordXYZ: String,
        var botlistSpace: String,
        var discordBotListCom: String,
        var discordBotsGG: String,
        var botsForDiscordCom: String,
        var discordBoats: String,
        var randomCatApi: String,
        var kSoftApi: String,
        var osu: String
    )

    data class Database(
        var database: String,
        var password: String,
        var user: String,
        var host: String,
        var port: Int
    )


    data class Redis(
        val host: String,
        val port: Int,
        val enabled: Boolean
    )

    companion object {
        private val dotenv = dotenv {
            this.filename = System.getenv("ENV_FILE") ?: ".env"
            this.ignoreIfMissing = true
        }

        fun get(path: String): String = dotenv[path.toUpperCase().replace(".", "_")]
            ?: throw IllegalStateException("missing env value: $path")

        fun getLong(path: String): Long = get(path).toLong()
        fun getInt(path: String): Int = get(path).toInt()
        fun getBoolean(path: String): Boolean = get(path).toBoolean()

        fun initSettings(): Settings {

            val llNodes = mutableListOf<Lavalink.LLNode>()
            val groupList = DataArray.fromJson(get("lavalink.nodes"))
            for (i in 0 until groupList.length()) {
                val groupEntry = groupList.getObject(i)
                val group = groupEntry.getString("group")
                val nodes = groupEntry.getArray("nodes")
                for (j in 0 until nodes.length()) {
                    val node = nodes.getObject(j)
                    llNodes.add(Lavalink.LLNode(group, node.getString("host"), node.getString("pass")))
                }
            }

            return Settings(
                BotInfo(
                    get("botinfo.prefix"),
                    getLong("botinfo.id"),
                    getInt("botinfo.shardCount"),
                    getInt("botinfo.embedColor"),
                    getLong("botinfo.exceptionsChannelId"),
                    get("botinfo.developerIds").split(",").map { it.toLong() }.toLongArray()
                ),
                RestServer(
                    getInt("restserver.port"),
                    get("restserver.token")
                ),
                Api(
                    Api.MelijnCDN(
                        get("api.melijncdn.token")
                    ),
                    Api.Jikan(
                        getBoolean("api.jikan.ssl"),
                        get("api.jikan.host"),
                        getInt("api.jikan.port")
                    ),
                    Api.Spotify(
                        get("api.spotify.clientId"),
                        get("api.spotify.password")
                    ),
                    Api.ImgHoard(
                        get("api.imghoard.token")
                    )
                ),
                Environment.valueOf(get("environment")),
                Lavalink(
                    llNodes.filter { it.groupId == "http" }.toTypedArray(),
                    llNodes.filter { it.groupId == "normal" }.toTypedArray(),
                    getBoolean("lavalink.enabled")
                ),
                Token(
                    get("token.discord"),
                    get("token.topDotGG"),
                    get("token.weebSh"),
                    get("token.botsOnDiscordXYZ"),
                    get("token.botListSpace"),
                    get("token.discordBotListCom"),
                    get("token.discordBotsGG"),
                    get("token.botsForDiscordCom"),
                    get("token.discordBoats"),
                    get("token.randomCatApi"),
                    get("token.kSoftApi"),
                    get("token.osuApi")
                ),
                Database(
                    get("database.database"),
                    get("database.password"),
                    get("database.user"),
                    get("database.host"),
                    getInt("database.port")
                ),
                Redis(
                    get("redis.host"),
                    getInt("redis.port"),
                    getBoolean("redis.enabled")
                ),
                get("unloggedThreads").splitIETEL(",").toTypedArray()
            )
        }

    }
}

