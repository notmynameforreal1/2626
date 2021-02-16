package me.melijn.melijnbot.internals.command

import io.ktor.client.*
import me.melijn.melijnbot.Container
import me.melijn.melijnbot.database.DaoManager
import me.melijn.melijnbot.database.command.CustomCommand
import me.melijn.melijnbot.database.message.ModularMessage
import me.melijn.melijnbot.database.scripts.Script
import me.melijn.melijnbot.enums.ChannelCommandState
import me.melijn.melijnbot.internals.events.SuspendListener
import me.melijn.melijnbot.internals.jagtag.CCJagTagParser
import me.melijn.melijnbot.internals.jagtag.CCJagTagParserArgs
import me.melijn.melijnbot.internals.models.TriState
import me.melijn.melijnbot.internals.translation.getLanguage
import me.melijn.melijnbot.internals.translation.i18n
import me.melijn.melijnbot.internals.utils.*
import me.melijn.melijnbot.internals.utils.message.*
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import kotlin.random.Random

val SPACE_REGEX = "\\s+".toRegex()

class CommandClient(private val commandList: Set<AbstractCommand>, private val container: Container) :
    SuspendListener() {

    private val guildPrefixWrapper = container.daoManager.guildPrefixWrapper
    private val userPrefixWrapper = container.daoManager.userPrefixWrapper
    private val melijnMentions = arrayOf("<@${container.settings.botInfo.id}>", "<@!${container.settings.botInfo.id}>")

    private val commandMap: HashMap<String, AbstractCommand> = HashMap()

    init {
        commandList.forEach { command ->
            commandMap[command.name.toLowerCase()] = command
            for (alias in command.aliases) {
                commandMap[alias.toLowerCase()] = command
            }
        }
        container.commandMap = commandList.map { it.id to it }.toMap()
    }


    override suspend fun onEvent(event: GenericEvent) {
        if (event is MessageReceivedEvent) {
            if (event.author.isBot) return

            try {
                commandFinder(event)
            } catch (t: Throwable) {
                t.printStackTrace()
                t.sendInGuild(event.guild, shouldSend = false)
            }
        }
    }

    private suspend fun commandFinder(event: MessageReceivedEvent) {
        val message = event.message
        if (message.contentRaw.isBlank()) return

        if (event.channelType == ChannelType.TEXT && !event.guild.selfMember.hasPermission(
                event.textChannel,
                Permission.MESSAGE_WRITE
            )
        ) return

        val prefixes = getPrefixes(event)


        val ccsWithPrefix = mutableListOf<CustomCommand>()
        val ccsWithoutPrefix = mutableListOf<CustomCommand>()
        val guildId = event.guild.idLong
        val fromGuild = event.isFromGuild
        val daoManager = container.daoManager
        if (fromGuild) {
            val ccWrapper = daoManager.customCommandWrapper
            val ccs = ccWrapper.getList(guildId)

            for (cc in ccs) {
                if (cc.prefix) {
                    ccsWithPrefix
                } else {
                    ccsWithoutPrefix
                }.add(cc)
            }
        }

        val ccsWithPrefixMatches = mutableListOf<CustomCommand>()
        val ccsWithoutPrefixMatches = mutableListOf<CustomCommand>()
        var commandPartsGlobal: List<String> = emptyList()
        val spaceMap = mutableMapOf<String, Int>()


        for (prefix in prefixes) {
            if (!message.contentRaw.startsWith(prefix, true)) continue

            val commandParts: ArrayList<String> = ArrayList(
                message.contentRaw
                    .removeFirst(prefix, ignoreCase = true)
                    .trimEnd()
                    .split(SPACE_PATTERN)
            )

            // Validate command parts (Checks if we have enough info to actually call any command)
            if (commandParts[0].isEmpty()) {
                // Check if a spaced prefix is allowed or is suitable (mention), otherwise return
                if (!melijnMentions.contains(prefix) && !isSpacedPrefixAllowed(event)) {
                    return
                }
                commandParts[0] = prefix
            } else {
                commandParts.add(0, prefix)
            }
            if (commandParts.size < 2) return // if only a prefix is found -> abort
            commandPartsGlobal = commandParts


            // CC Finder
            for (cc in ccsWithPrefix) {
                val aliases = cc.aliases
                if (cc.name.equals(commandParts[1], true)) {

                    ccsWithPrefixMatches.add(cc)
                } else if (aliases != null) {
                    for (alias in aliases) {
                        val aliasParts = alias.split(SPACE_PATTERN)
                        if (alias.count() < commandParts.size) {
                            val matches = aliasParts.withIndex().all { commandParts[it.index + 1] == it.value }
                            if (!matches) continue
                            val spaceCount = aliasParts.size - 1

                            spaceMap["cc.${cc.id}"] = spaceCount
                            ccsWithPrefixMatches.add(cc)
                        } else continue
                    }
                }
            }

            var command = commandMap[commandParts[1].toLowerCase()]
            val aliasesMap = mutableMapOf<String, List<String>>()
            var searchedAliases = false
            if (command == null) {
                val aliasCache = daoManager.aliasWrapper
                if (fromGuild) {
                    aliasesMap.putAll(aliasCache.getAliases(guildId))
                }

                for ((cmd, ls) in aliasCache.getAliases(event.author.idLong)) {
                    val currentList = (aliasesMap[cmd] ?: emptyList()).toMutableList()
                    for (alias in ls) {
                        currentList.addIfNotPresent(alias)
                    }

                    aliasesMap[cmd] = currentList
                }

                searchedAliases = true
                // ^ Above constructs the aliaxMap for guild and user

                // Find command by custom alias v
                for ((cmd, aliases) in aliasesMap) {
                    val id = cmd.toIntOrNull() ?: continue

                    for (alias in aliases) {
                        val aliasParts = alias.split(SPACE_REGEX)
                        if (aliasParts.size < commandParts.size) {
                            val matches = aliasParts.withIndex().all { commandParts[it.index + 1] == it.value }
                            if (!matches) continue

                            spaceMap["$id"] = aliasParts.size - 1
                            commandList.firstOrNull {
                                it.id == id
                            }?.let {
                                command = it
                            }
                        }
                    }
                }
            }

            if (command != null) {
                val finalCommand = command ?: return
                if (checksFailed(
                        container, finalCommand, finalCommand.id.toString(), message, false,
                        commandParts
                    )
                ) return
                finalCommand.run(
                    CommandContext(
                        message,
                        commandParts,
                        container,
                        commandList,
                        spaceMap,
                        aliasesMap,
                        searchedAliases
                    )
                )
                return
            } else if (fromGuild && isPremiumGuild(daoManager, guildId)) {
                // Search for scripts
//                val scripts = daoManager.scriptWrapper.getScripts(guildId)
//                for (script in scripts) {
//                    val triggerParts = script.trigger.split(SPACE_REGEX)
//                    if (!eventIsForScript(triggerParts, commandParts, prefix)) continue
//                    runScript(event, script, triggerParts, prefix)
//                    return
//                }

            }
        }

        if (ccsWithPrefixMatches.isNotEmpty()) {
            runCustomCommandByChance(
                message,
                container.webManager.proxiedHttpClient,
                commandPartsGlobal,
                ccsWithPrefixMatches,
                true
            )
            return

        } else {
            val prefixLessCommandParts: ArrayList<String> = ArrayList(
                message.contentRaw
                    .trim()
                    .split(SPACE_PATTERN)
            )

            for (cc in ccsWithoutPrefix) {
                val aliases = cc.aliases
                if (prefixLessCommandParts[0].equals(cc.name, true)) {
                    ccsWithoutPrefixMatches.add(cc)
                } else if (aliases != null) {
                    for (alias in aliases) {
                        if (alias.equals(prefixLessCommandParts[0], true)) {
                            ccsWithoutPrefixMatches.add(cc)
                        }
                    }
                }
            }

            if (ccsWithoutPrefixMatches.isNotEmpty()) {
                runCustomCommandByChance(
                    message,
                    container.webManager.proxiedHttpClient,
                    prefixLessCommandParts,
                    ccsWithoutPrefixMatches,
                    false
                )
                return
            }
        }
    }

    private fun runScript(event: MessageReceivedEvent, script: Script, triggerParts: List<String>, prefix: String?) {
        var argBuilder = event.message.contentRaw
        if (prefix != null) {
            argBuilder = argBuilder.removePrefix(prefix, true).trimStart()
        }
        triggerParts.forEach {
            argBuilder = argBuilder.removePrefix(it, true).trimStart()
        }
        val args = argBuilder
        val argLines = args.split("\n")

        for ((index, cmdInfo) in script.commands.entries.sortedBy { it.key }) {
            val invoke = cmdInfo.first
            println(invoke + argLines)
        }
    }

    private fun eventIsForScript(
        triggerParts: List<String>,
        commandParts: List<String>,
        prefix: String?
    ): Boolean {
        val offset = if (prefix == null) 0 else 1
        return triggerParts.withIndex().all { (i, part) ->
            commandParts.getOrNull(i + offset)?.let { it.equals(part, true) } ?: false
        }
    }

    private suspend fun isSpacedPrefixAllowed(
        event: MessageReceivedEvent
    ): Boolean {
        val wrapper = container.daoManager.allowSpacedPrefixWrapper
        return when (wrapper.getUserTriState(event.author.idLong)) {
            TriState.TRUE -> true
            TriState.FALSE -> false
            TriState.DEFAULT -> {
                if (event.isFromGuild) wrapper.getGuildState(event.guild.idLong)
                else false
            }
        }
    }

    private suspend fun runCustomCommandByChance(
        message: Message,
        httpClient: HttpClient,
        commandParts: List<String>,
        ccs: List<CustomCommand>,
        hasPrefix: Boolean
    ) {
        val cc: CustomCommand = if (ccs.size == 1) {
            ccs.first()
        } else {
            getCustomCommandByChance(ccs)
        }

        if (checksFailed(container.daoManager, cc, message)) return

        if (hasPermission(container, message, "cc.${cc.id}")) {
            val cParts = commandParts.toMutableList()
            executeCC(cc, httpClient, message, cParts, hasPrefix)
        } else {
            val language = getLanguage(container.daoManager, message.author.idLong, message.guild.idLong)
            sendMissingPermissionMessage(message.textChannel, container.daoManager, language, "cc.${cc.id}")
        }
    }

    private suspend fun executeCC(
        cc: CustomCommand,
        httpClient: HttpClient,
        message: Message,
        commandParts: List<String>,
        hasPrefix: Boolean
    ) {
        val member = message.member ?: return
        val channel = message.textChannel
        if (!channel.canTalk()) return

        //registering execution
        val pair1 = Pair(channel.idLong, member.idLong)
        val map1 = container.daoManager.commandChannelCoolDownWrapper.executions[pair1]?.toMutableMap()
            ?: mutableMapOf()
        map1["cc." + cc.id] = System.currentTimeMillis()
        container.daoManager.commandChannelCoolDownWrapper.executions[pair1] = map1

        val pair2 = Pair(member.guild.idLong, member.idLong)
        val map2 = container.daoManager.commandChannelCoolDownWrapper.executions[pair2]?.toMutableMap()
            ?: mutableMapOf()
        map2["cc." + cc.id] = System.currentTimeMillis()
        container.daoManager.commandChannelCoolDownWrapper.executions[pair2] = map2

        val rawArg = message.contentRaw
            .removeFirst(commandParts[0])
            .trim()
            .removeFirst(if (hasPrefix) commandParts[1] else "")
            .trim()
        val modularMessage = replaceVariablesInCCMessage(member, rawArg, cc)

        val message: Message? = modularMessage.toMessage()
        when {
            message == null -> sendAttachmentsAwaitN(channel, httpClient, modularMessage.attachments)
            modularMessage.attachments.isNotEmpty() -> sendMsgWithAttachmentsAwaitN(
                channel,
                httpClient,
                message,
                modularMessage.attachments
            )
            else -> sendMsgAwaitN(channel, message)
        }
    }


    private suspend fun replaceVariablesInCCMessage(member: Member, rawArg: String, cc: CustomCommand): ModularMessage {
        val modularMessage = cc.content
        val ccArgs = CCJagTagParserArgs(member, rawArg, cc)

        return modularMessage.mapAllStringFields {
            if (it != null) {
                CCJagTagParser.parseJagTag(ccArgs, it)
            } else {
                null
            }
        }
    }

    private fun getCustomCommandByChance(ccs: List<CustomCommand>): CustomCommand {
        var range = 0
        for (cc in ccs) {
            range += cc.chance
        }
        val winner = Random.nextInt(range)
        range = 0
        for (cc in ccs) {
            val bool1 = (range <= winner)
            range += cc.chance
            val bool2 = (range <= winner)
            if (bool1 && !bool2) {
                return cc
            }
        }
        throw IllegalArgumentException("random int ($winner) out of range of ccs")
    }


    private suspend fun getPrefixes(event: MessageReceivedEvent): List<String> {
        val prefixes = if (event.isFromGuild) {
            guildPrefixWrapper.getPrefixes(event.guild.idLong).toMutableList()
        } else {
            mutableListOf()
        }

        // add default prefix if none are set
        if (prefixes.isEmpty()) {
            prefixes.add(container.settings.botInfo.prefix)
        }

        //registering private prefixes
        prefixes.addAll(userPrefixWrapper.getPrefixes(event.author.idLong))


        //mentioning the bot will always work
        val tags = melijnMentions
        for (tag in tags) {
            prefixes.add(tag)
        }

        val jdaMention = if (event.isFromGuild) {
            event.guild.selfMember.asMention
        } else {
            event.jda.selfUser.asMention
        }

        if (!prefixes.contains(jdaMention)) {
            prefixes.add(jdaMention)
        }

        return prefixes.toList()
    }


    companion object {

        /**
         * [@return] returns true if the check failed
         *
         * **/
        suspend fun checksFailed(
            container: Container,
            command: AbstractCommand,
            cmdId: String,
            message: Message,
            isSubCommand: Boolean,
            commandParts: List<String>
        ): Boolean {
            if (message.isFromGuild && commandIsDisabled(container.daoManager, cmdId, message)) {
                return true
            }

            val conditions = mutableListOf<RunCondition>()
            if (!isSubCommand) addConditions(conditions, command.commandCategory.runCondition)
            addConditions(conditions, command.runConditions)

            conditions.forEach {
                if (!RunConditionUtil.runConditionCheckPassed(
                        container,
                        it,
                        message,
                        command,
                        commandParts
                    )
                ) return true
            }

            if (message.isFromGuild && !isSubCommand) {
                val botMember = message.guild.selfMember

                // Channel perms
                val missingChannelPermissions = command.discordChannelPermissions.filter { permission ->
                    !botMember.hasPermission(message.textChannel, permission)
                }

                if (missingChannelPermissions.isNotEmpty()) {
                    val language = getLanguage(container.daoManager, message.author.idLong, message.guild.idLong)
                    sendMelijnMissingChannelPermissionMessage(
                        message.textChannel,
                        language,
                        container.daoManager,
                        missingChannelPermissions
                    )
                    return true
                }

                // Global perms
                val missingPermissions = command.discordPermissions.filter { permission ->
                    !botMember.hasPermission(permission)
                }

                if (missingPermissions.isNotEmpty()) {
                    val language = getLanguage(container.daoManager, message.author.idLong, message.guild.idLong)
                    sendMelijnMissingPermissionMessage(
                        message.textChannel,
                        language,
                        container.daoManager,
                        missingPermissions
                    )
                    return true
                }

                if (commandIsOnCooldown(container.daoManager, cmdId, command.cooldown, message)) {
                    return true
                }
            }

            return false
        }

        private fun addConditions(conditionList: MutableList<RunCondition>, list: Array<RunCondition>) {
            for (runCondition in list) {
                addConditions(conditionList, runCondition.preRequired)
                if (!conditionList.contains(runCondition)) {
                    conditionList.add(runCondition)
                }
            }
        }

        /**
         * [@return] returns true if the check failed
         *
         * **/
        private suspend fun checksFailed(
            daoManager: DaoManager,
            command: CustomCommand,
            message: Message
        ): Boolean {
            val cmdId = "cc.${command.id}"
            if (commandIsDisabled(daoManager, cmdId, message)) {
                return true
            }

            if (commandIsOnCooldown(daoManager, cmdId, 0, message)) {
                return true
            }

            return false
        }


        private suspend fun commandIsOnCooldown(
            daoManager: DaoManager,
            id: String,
            globalCooldown: Long,
            message: Message
        ): Boolean {
            val guildId = message.guild.idLong
            val userId = message.author.idLong
            val channelId = message.channel.idLong

            val commandCooldownWrapper = daoManager.commandCooldownWrapper
            val commandChannelCoolDownWrapper = daoManager.commandChannelCoolDownWrapper


            var lastExecution = 0L // millis for last guild execution
            var lastExecutionChannel = 0L // millis for last channel execution
            var bool = false
            var cooldownResult = 0L

            if (!daoManager.commandChannelCoolDownWrapper.executions.contains(Pair(guildId, userId))) {
                val commandChannelCooldowns = commandChannelCoolDownWrapper.getMap(channelId)
                if (commandChannelCooldowns.containsKey(id)) {

                    //init lastExecutionChannel
                    daoManager.commandChannelCoolDownWrapper.executions[Pair(channelId, userId)]
                        ?.filter { entry -> entry.key == id }
                        ?.forEach { entry ->
                            if (entry.value > lastExecutionChannel) lastExecutionChannel = entry.value
                        }

                    val cooldown = commandChannelCooldowns[id] ?: 0L

                    if (System.currentTimeMillis() - cooldown < lastExecutionChannel) {
                        cooldownResult = cooldown
                        bool = true
                    }
                }
                val commandCooldowns = commandCooldownWrapper.getMap(guildId)
                if (commandCooldowns.containsKey(id)) {

                    //init lastExecution
                    daoManager.commandChannelCoolDownWrapper.executions[Pair(guildId, userId)]
                        ?.filter { entry -> entry.key == id }
                        ?.forEach { entry ->
                            if (entry.value > lastExecution) lastExecution = entry.value
                        }

                    val cooldown = commandCooldowns[id] ?: 0L

                    if (System.currentTimeMillis() - cooldown < lastExecution) {
                        if (cooldownResult < cooldown) cooldownResult = cooldown
                        bool = true
                    }
                }
            }

            val lastGlobalExecuted = daoManager.globalCooldownWrapper.getLastExecuted(userId, id)
            if (globalCooldown != 0L && (System.currentTimeMillis() - globalCooldown) < lastGlobalExecuted) {
                if (cooldownResult < globalCooldown) cooldownResult = globalCooldown
                bool = true
            }

            val lastExecutionBiggest = maxOf(lastExecution, lastExecutionChannel, lastGlobalExecuted)

            if (bool && cooldownResult != 0L) {
                val language = getLanguage(daoManager, userId, guildId)
                val unReplacedCooldown = i18n.getTranslation(language, "message.cooldown")
                val msg = unReplacedCooldown
                    .withVariable(
                        "cooldown",
                        ((cooldownResult - (System.currentTimeMillis() - lastExecutionBiggest)) / 1000.0).toString()
                    )

                sendRspOrMsg(message.textChannel, daoManager, msg)
            }
            return bool
        }


        private suspend fun commandIsDisabled(
            daoManager: DaoManager,
            id: String,
            message: Message
        ): Boolean {
            val disabledCommandCache = daoManager.disabledCommandWrapper
            val channelCommandStateCache = daoManager.channelCommandStateWrapper

            val disabledChannelCommands = channelCommandStateCache.getMap(message.channel.idLong)
            if (disabledChannelCommands.contains(id)) {
                if (disabledChannelCommands[id] == ChannelCommandState.ENABLED) {
                    return false
                } else if (disabledChannelCommands[id] == ChannelCommandState.DISABLED) {
                    return true
                }
            }

            val disabledCommands = disabledCommandCache.getSet(message.guild.idLong)
            if (disabledCommands.contains(id)) return true

            return false
        }
    }
}