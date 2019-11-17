package me.melijn.melijnbot.objects.utils.checks

import kotlinx.coroutines.future.await
import me.melijn.melijnbot.database.DaoManager
import me.melijn.melijnbot.database.logchannel.LogChannelWrapper
import me.melijn.melijnbot.database.role.RoleWrapper
import me.melijn.melijnbot.enums.ChannelType
import me.melijn.melijnbot.enums.LogChannelType
import me.melijn.melijnbot.enums.RoleType
import me.melijn.melijnbot.objects.translation.getLanguage
import me.melijn.melijnbot.objects.utils.LogUtils
import me.melijn.melijnbot.objects.utils.toUpperWordCase
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.VoiceChannel

private const val UNKNOWN_ID_CAUSE = "unknownid"
private const val NO_PERM_CAUSE = "nopermission"

suspend fun Guild.getAndVerifyLogChannelByType(type: LogChannelType, logChannelWrapper: LogChannelWrapper): TextChannel? {
    val channelId = logChannelWrapper.logChannelCache.get(Pair(idLong, type)).await()
    val textChannel = getTextChannelById(channelId)
    var shouldRemove = false
    if (channelId != -1L && textChannel == null) {
        shouldRemove = true
    }
    if (textChannel == null) return null
    if (!textChannel.canTalk()) {
        shouldRemove = true
    }

    if (shouldRemove) {
        logChannelWrapper.removeChannel(this.idLong, type)
    }
    return textChannel
}

suspend fun Guild.getAndVerifyChannelByType(
    type: ChannelType,
    daoManager: DaoManager,
    vararg requiredPerms: Permission
): TextChannel? {
    val channelWrapper = daoManager.channelWrapper
    val logChannelWrapper = daoManager.logChannelWrapper
    val channelId = channelWrapper.channelCache.get(Pair(idLong, type)).await()
    val textChannel = getTextChannelById(channelId)
    val selfMember = textChannel?.guild?.selfMember
    val logChannel = textChannel?.guild?.getAndVerifyLogChannelByType(LogChannelType.BOT, logChannelWrapper)

    val language = getLanguage(daoManager, -1, this.idLong)

    var shouldRemove = false
    if (channelId != -1L && textChannel == null) {
        LogUtils.sendRemovedChannelLog(language, type, logChannel, UNKNOWN_ID_CAUSE, channelId.toString())
        shouldRemove = true
    }

    for (perm in requiredPerms) {
        if (shouldRemove || selfMember == null) break
        if (!selfMember.hasPermission(textChannel, perm)) {
            shouldRemove = true
            LogUtils.sendRemovedChannelLog(language, type, logChannel, NO_PERM_CAUSE, perm.toString().toUpperWordCase())
        }
    }

    if (shouldRemove) {
        channelWrapper.removeChannel(this.idLong, type)
        return null
    }

    return textChannel
}

suspend fun Guild.getAndVerifyMusicChannel(
    daoManager: DaoManager,
    vararg requiredPerms: Permission
): VoiceChannel? {
    val channelWrapper = daoManager.musicChannelWrapper
    val logChannelWrapper = daoManager.logChannelWrapper
    val channelId = channelWrapper.musicChannelCache.get(idLong).await()
    val voiceChannel = getVoiceChannelById(channelId)
    val selfMember = voiceChannel?.guild?.selfMember
    val logChannel = voiceChannel?.guild?.getAndVerifyLogChannelByType(LogChannelType.BOT, logChannelWrapper)

    val language = getLanguage(daoManager, -1, this.idLong)

    var shouldRemove = false
    if (channelId != -1L && voiceChannel == null) {
        LogUtils.sendRemovedMusicChannelLog(language, logChannel, UNKNOWN_ID_CAUSE, channelId.toString())
        shouldRemove = true
    }

    for (perm in requiredPerms) {
        if (shouldRemove || selfMember == null) break
        if (!selfMember.hasPermission(voiceChannel, perm)) {
            shouldRemove = true
            LogUtils.sendRemovedMusicChannelLog(language, logChannel, NO_PERM_CAUSE, perm.toString().toUpperWordCase())
        }
    }

    if (shouldRemove) {
        channelWrapper.removeChannel(this.idLong)
    }

    return voiceChannel
}

suspend fun Guild.getAndVerifyRoleByType(type: RoleType, roleWrapper: RoleWrapper, shouldBeInteractable: Boolean = false): Role? {
    val channelId = roleWrapper.roleCache.get(Pair(idLong, type)).await()
    if (channelId == -1L) return null

    val role = getRoleById(channelId)
    var shouldRemove = false
    if (role == null) shouldRemove = true
    else if (shouldBeInteractable && !selfMember.canInteract(role)) shouldRemove = true

    if (shouldRemove) {
        roleWrapper.removeRole(this.idLong, type)
    }

    return role
}