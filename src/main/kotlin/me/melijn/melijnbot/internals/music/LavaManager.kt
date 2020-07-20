package me.melijn.melijnbot.internals.music


import me.melijn.llklient.io.jda.JDALavalink
import me.melijn.llklient.player.IPlayer
import me.melijn.llklient.player.LavaplayerPlayerWrapper
import me.melijn.melijnbot.MelijnBot
import me.melijn.melijnbot.database.DaoManager
import me.melijn.melijnbot.internals.command.CommandContext
import me.melijn.melijnbot.internals.utils.notEnoughPermissionsAndMessage
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.VoiceChannel


class LavaManager(
    val lavalinkEnabled: Boolean,
    val daoManager: DaoManager,
    val jdaLavaLink: JDALavalink?
) {

    val musicPlayerManager: MusicPlayerManager = MusicPlayerManager(daoManager, this)

    fun getIPlayer(guildId: Long, groupId: String): IPlayer {
        return if (lavalinkEnabled && jdaLavaLink != null) {
            jdaLavaLink.getLink(guildId, groupId).player
        } else {
            LavaplayerPlayerWrapper(musicPlayerManager.getLPPlayer())
        }
    }

    suspend fun openConnection(channel: VoiceChannel, groupId: String) {
        if (jdaLavaLink == null) {
            val selfMember = channel.guild.selfMember
            if (selfMember.hasPermission(channel, Permission.VOICE_CONNECT)) {
                channel.guild.audioManager.sendingHandler = AudioPlayerSendHandler(getIPlayer(channel.guild.idLong, groupId))
                channel.guild.audioManager.openAudioConnection(channel)
            }
        } else {
            jdaLavaLink.getLink(channel.guild.idLong, groupId).connect(channel)
        }

        musicPlayerManager.getGuildMusicPlayer(channel.guild)
    }

    /**
     * @param context            This will be used to send replies
     * @param guild              This will be used to check permissions
     * @param channel This is the voice channel you want to join
     * @return returns true on success and false when failed
     */
    suspend fun tryToConnectToVCNMessage(context: CommandContext, channel: VoiceChannel, groupId: String): Boolean {
        if (notEnoughPermissionsAndMessage(context, channel, Permission.VOICE_CONNECT)) return false
        return if (channel.userLimit == 0 || channel.userLimit > channel.members.size || notEnoughPermissionsAndMessage(context, channel, Permission.VOICE_MOVE_OTHERS)) {
            openConnection(channel, groupId)
            true
        } else {
            false
        }
    }

    suspend fun tryToConnectToVCSilent(voiceChannel: VoiceChannel, groupId: String): Boolean {
        val guild: Guild = voiceChannel.guild
        if (!guild.selfMember.hasPermission(voiceChannel, Permission.VOICE_CONNECT)) {
            return false
        }

        return if (voiceChannel.userLimit == 0 || voiceChannel.userLimit > voiceChannel.members.size || guild.selfMember.hasPermission(voiceChannel, Permission.VOICE_MOVE_OTHERS)) {
            openConnection(voiceChannel, groupId)
            true
        } else {
            false
        }
    }

    // run with VOICE_SAFE pls
    suspend fun closeConnection(guildId: Long) {
        closeConnectionLite(guildId)

        if (MusicPlayerManager.guildMusicPlayers.containsKey(guildId)) {
            MusicPlayerManager.guildMusicPlayers[guildId]?.removeTrackManagerListener()
            MusicPlayerManager.guildMusicPlayers.remove(guildId)
            //logger.info("removed guildmusicplayer for $guildId")
        }
    }


    suspend fun closeConnectionLite(guildId: Long) {
        val guild = MelijnBot.shardManager.getGuildById(guildId)

        if (jdaLavaLink == null) {
            guild?.audioManager?.closeAudioConnection()

        } else {
            jdaLavaLink.getExistingLink(guildId)?.destroy()
        }
    }


    fun getConnectedChannel(guild: Guild): VoiceChannel? = guild.selfMember.voiceState?.channel

    suspend fun changeGroup(guildId: Long, groupId: String) {
        val link = jdaLavaLink?.getLink(guildId, groupId) ?: throw IllegalArgumentException("wtf")
        link.changeGroup(groupId)
    }
}