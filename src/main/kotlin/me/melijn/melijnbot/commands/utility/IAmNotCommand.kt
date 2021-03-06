package me.melijn.melijnbot.commands.utility

import me.melijn.melijnbot.database.role.SelfRoleGroup
import me.melijn.melijnbot.internals.command.AbstractCommand
import me.melijn.melijnbot.internals.command.CommandCategory
import me.melijn.melijnbot.internals.command.ICommandContext
import me.melijn.melijnbot.internals.command.RunCondition
import me.melijn.melijnbot.internals.translation.MESSAGE_SELFINTERACT_MEMBER_HIARCHYEXCEPTION
import me.melijn.melijnbot.internals.translation.PLACEHOLDER_USER
import me.melijn.melijnbot.internals.utils.asTag
import me.melijn.melijnbot.internals.utils.await
import me.melijn.melijnbot.internals.utils.getRoleByArgsNMessage
import me.melijn.melijnbot.internals.utils.message.sendRsp
import me.melijn.melijnbot.internals.utils.message.sendSyntax
import me.melijn.melijnbot.internals.utils.withVariable
import net.dv8tion.jda.api.entities.Role

class IAmNotCommand : AbstractCommand("command.iamnot") {

    init {
        id = 210
        name = "iamNot"
        runConditions = arrayOf(RunCondition.GUILD)
        commandCategory = CommandCategory.UTILITY
    }

    override suspend fun execute(context: ICommandContext) {
        doSelfRoleSelect(context, false)
    }

    companion object {
        suspend fun doSelfRoleSelect(context: ICommandContext, add: Boolean) {
            val root = context.commandOrder.first().root
            if (context.args.isEmpty()) {
                sendSyntax(context, "$root.syntax")
                return
            }
            val role = getRoleByArgsNMessage(context, 0, sameGuildAsContext = true, canInteract = true) ?: return
            val selfRolesGrouped = context.daoManager.selfRoleWrapper.getMap(context.guildId)
            val selfRoleGroups = context.daoManager.selfRoleGroupWrapper.getMap(context.guildId)
            var noMatchReason = ""
            var foundMatch = false
            var group: SelfRoleGroup? = null
            for ((groupName, selfRoles) in selfRolesGrouped) {
                val selfRoleGroup = selfRoleGroups.firstOrNull { it.groupName == groupName }
                if (selfRoleGroup?.isSelfRoleable != true) {
                    noMatchReason = "notselfroleable"
                    continue
                }

                for (selfRoleIndex in 0 until selfRoles.length()) {
                    val selfRole = selfRoles.getArray(selfRoleIndex)
                    val rolesArray = selfRole.getArray(2)
                    if (rolesArray.length() > 1) {
                        noMatchReason = "nocomplexroles"
                        continue
                    }
                    for (i in 0 until rolesArray.length()) {
                        val chanceRole = rolesArray.getArray(i)
                        val roleId = chanceRole.getLong(1)
                        if (roleId == role.idLong) {
                            foundMatch = true
                            group = selfRoleGroup
                            break
                        }
                    }
                    noMatchReason = "notfound"
                    continue
                }
            }
            if (selfRolesGrouped.isEmpty()) {
                noMatchReason = "notfound"
            }

            if (foundMatch && group != null) {
                if (add && group.limitToOneRole) {
                    val data = selfRolesGrouped[group.groupName] ?: return
                    for (entryIndex in 0 until data.length()) {
                        val roleData = data.getArray(entryIndex).getArray(2)
                        var differentRole: Role? = null
                        for (i in 0 until roleData.length()) {
                            val roleId = roleData.getArray(i).getLong(1)
                            val hasRole = context.member.roles.any { it.idLong == roleId }
                            if (hasRole && roleId != role.idLong) {
                                differentRole = context.guild.getRoleById(roleId)
                                break
                            }
                        }
                        if (differentRole != null) {
                            val msg = context.getTranslation("$root.alreadyhasrolefromgroup")
                                .withVariable("group", group.groupName)
                                .withVariable("role", role.asMention)
                                .withVariable("differentRole", differentRole.asMention)
                            sendRsp(context, msg)
                            return
                        }
                    }
                }

                if (!context.selfMember.canInteract(context.member)) {
                    val msg = context.getTranslation(MESSAGE_SELFINTERACT_MEMBER_HIARCHYEXCEPTION)
                        .withVariable(PLACEHOLDER_USER, context.member.asTag)
                    sendRsp(context, msg)
                    return
                }
                val msg = try {
                    if (add) {
                        context.guild.addRoleToMember(context.member, role)
                    } else {
                        context.guild.removeRoleFromMember(context.member, role)
                    }.reason("${context.commandOrder.first().name} cmd").await()

                    context.getTranslation("$root.success")
                        .withVariable("role", role.asMention)
                } catch (t: Throwable) {
                    context.getTranslation("$root.failed")
                        .withVariable("cause", t.message ?: "unknown")
                        .withVariable("role", role.asMention)
                }
                sendRsp(context, msg)
            } else {
                val msg = context.getTranslation("$root.$noMatchReason")
                    .withVariable("prefix", context.usedPrefix)
                    .withVariable("role", "@" + role.name)
                sendRsp(context, msg)
            }
        }
    }
}