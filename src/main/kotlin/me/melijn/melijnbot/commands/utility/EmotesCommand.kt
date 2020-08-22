package me.melijn.melijnbot.commands.utility

import me.melijn.melijnbot.internals.command.AbstractCommand
import me.melijn.melijnbot.internals.command.CommandCategory
import me.melijn.melijnbot.internals.command.CommandContext
import me.melijn.melijnbot.internals.command.RunCondition
import me.melijn.melijnbot.internals.utils.StringUtils
import me.melijn.melijnbot.internals.utils.await
import me.melijn.melijnbot.internals.utils.message.sendPaginationMsg
import me.melijn.melijnbot.internals.utils.message.sendRsp

class EmotesCommand : AbstractCommand("command.emotes") {

    init {
        id = 207
        name = "emotes"
        aliases = arrayOf("emojis", "emotejis")
        runConditions = arrayOf(RunCondition.GUILD)
        commandCategory = CommandCategory.UTILITY
    }

    override suspend fun execute(context: CommandContext) {
        val emotes = context.guild.retrieveEmotes().await()
        if (emotes.isEmpty()) {
            val msg = context.getTranslation("$root.noemotes")
            sendRsp(context, msg)
            return
        }
        if (context.args.isNotEmpty() && context.args[0] == "full") {
            val sb = StringBuilder("List of emotes:\n")
            for (emote in emotes) {
                sb.append(emote.asMention).append(" | ").append(emote.id).append(" - `:").append(emote.name).appendLine(":`")
            }
            sb.append(emotes.size).append("/").append(context.guild.maxEmotes)
            sendPaginationMsg(context, StringUtils.splitMessage(sb.toString()), 0)
            return
        }
        if (context.args.isNotEmpty() && context.args[0] == "compact") {
            sendRsp(context, emotes.joinToString("") { it.asMention })
            return
        }
        if (context.args.isNotEmpty() && context.args[0] == "raw") {
            sendRsp(context, "List of raw emotes:```" + emotes.joinToString("\n") { it.asMention } + "```")
            return
        }

        sendRsp(context, emotes.joinToString(" ") { it.asMention })
    }
}