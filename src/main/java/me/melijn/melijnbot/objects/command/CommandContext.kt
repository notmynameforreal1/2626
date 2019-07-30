package me.melijn.melijnbot.objects.command

import me.melijn.melijnbot.Container
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.util.regex.Pattern

class CommandContext(
        private val messageReceivedEvent: MessageReceivedEvent,
        val commandParts: List<String>,
        private val container: Container,
        private val commandList: Set<AbstractCommand>
) : ICommandContext {

    override fun getEvent(): MessageReceivedEvent {
        return messageReceivedEvent
    }

    override fun getGuild(): Guild {
        return messageReceivedEvent.guild
    }


    val usedPrefix: String = commandParts[0]
    val offset: Int = retrieveOffset()
    val embedColor: Int = container.settings.embedColor
    val prefix: String = container.settings.prefix
    var commandOrder: List<AbstractCommand> = emptyList()
    var args: List<String> = emptyList()
    val botDevIds: LongArray = container.settings.developerIds
    val daoManager = container.daoManager
    val taskManager = container.taskManager
    val jda = messageReceivedEvent.jda
    val guildId = getGuild().idLong
    val authorId = getAuthor().idLong
    var rawArg: String = ""

    fun initArgs() {
        args = commandParts.drop(1 + commandOrder.size)

        val regex: Regex = ("${Pattern.compile(usedPrefix)}(\\s+)?" + (".*(\\s+)").repeat(commandOrder.size)).toRegex()
        rawArg = messageReceivedEvent.message.contentRaw.replaceFirst(regex, "")
    }

    fun getCommands() = commandList
    private fun retrieveOffset(): Int {
        var count = 0
        val pattern = Pattern.compile("<@!?(\\d+)>")
        val matcher = pattern.matcher(commandParts[0])

        while (matcher.find()) {
            if (jda.getUserById(matcher.group(1)) != null) count++
        }

        return count
    }
}