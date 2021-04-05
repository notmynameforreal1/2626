package me.melijn.melijnbot.commands.anime

import me.melijn.melijnbot.commandutil.anime.AnimeCommandUtil
import me.melijn.melijnbot.internals.command.AbstractCommand
import me.melijn.melijnbot.internals.command.CommandCategory
import me.melijn.melijnbot.internals.command.ICommandContext
import me.melijn.melijnbot.internals.web.weebsh.WeebApi

class NomCommand : AbstractCommand("command.nom") {

    init {
        id = 246
        name = "nom"
        commandCategory = CommandCategory.ANIME
    }

    suspend fun execute(context: ICommandContext) {
        AnimeCommandUtil.execute(context, "nom", arrayOf(WeebApi.Type.XIG))
    }
}