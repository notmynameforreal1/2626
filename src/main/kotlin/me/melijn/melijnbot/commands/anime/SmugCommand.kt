package me.melijn.melijnbot.commands.anime

import me.melijn.melijnbot.commandutil.anime.AnimeCommandUtil
import me.melijn.melijnbot.internals.command.AbstractCommand
import me.melijn.melijnbot.internals.command.CommandCategory
import me.melijn.melijnbot.internals.command.ICommandContext

class SmugCommand : AbstractCommand("command.smug") {

    init {
        id = 106
        name = "smug"
        commandCategory = CommandCategory.ANIME
    }

    suspend fun execute(context: ICommandContext) {
        AnimeCommandUtil.executeShow(context, "smug")
    }
}