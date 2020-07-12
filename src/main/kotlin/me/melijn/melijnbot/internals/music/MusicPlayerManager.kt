package me.melijn.melijnbot.internals.music

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withPermit
import me.melijn.melijnbot.database.DaoManager
import me.melijn.melijnbot.internals.music.lavaimpl.MelijnAudioPlayerManager
import me.melijn.melijnbot.internals.services.voice.VOICE_SAFE
import net.dv8tion.jda.api.entities.Guild
import org.slf4j.LoggerFactory


class MusicPlayerManager(
    private val daoManager: DaoManager,
    private val lavaManager: LavaManager
) {

    private val logger = LoggerFactory.getLogger(MusicPlayerManager::class.java)
    val audioPlayerManager: MelijnAudioPlayerManager = MelijnAudioPlayerManager()
    val audioLoader = AudioLoader(this)

    companion object {
        val guildMusicPlayers: HashMap<Long, GuildMusicPlayer> = HashMap()
    }


    init {
        audioPlayerManager.configuration.isFilterHotSwapEnabled = true
        audioPlayerManager.frameBufferDuration = 1000
        AudioSourceManagers.registerRemoteSources(audioPlayerManager)
        AudioSourceManagers.registerLocalSource(audioPlayerManager)

    }

    fun getLPPlayer(): AudioPlayer {
        return audioPlayerManager.createPlayer()
    }

    @Synchronized
    fun getGuildMusicPlayer(guild: Guild): GuildMusicPlayer {
        val cachedMusicPlayer = guildMusicPlayers[guild.idLong]
        if (cachedMusicPlayer == null) {
            val newMusicPlayer = GuildMusicPlayer(daoManager, lavaManager, guild.idLong, "normal")
            runBlocking {
                VOICE_SAFE.withPermit {
                    guildMusicPlayers[guild.idLong] = newMusicPlayer
                    logger.debug("new player for ${guild.id}")
                }
            }

            if (!lavaManager.lavalinkEnabled) {
                guild.audioManager.sendingHandler = newMusicPlayer.getSendHandler()
            }

            return newMusicPlayer
        }
        return cachedMusicPlayer
    }

    fun getPlayers() = guildMusicPlayers
}