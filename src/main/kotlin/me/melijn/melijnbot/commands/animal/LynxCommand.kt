package me.melijn.melijnbot.commands.animal

import me.melijn.melijnbot.internals.command.AbstractCommand
import me.melijn.melijnbot.internals.command.CommandCategory
import me.melijn.melijnbot.internals.command.ICommandContext
import me.melijn.melijnbot.internals.embed.Embedder
import me.melijn.melijnbot.internals.translation.MISSING_IMAGE_URL
import me.melijn.melijnbot.internals.utils.message.sendEmbedRsp
import me.melijn.melijnbot.internals.web.WebManager
import me.melijn.melijnbot.internals.web.WebUtils

class LynxCommand : AbstractCommand("command.lynx") {

    init {
        name = "lynx"
        commandCategory = CommandCategory.ANIMAL
    }

    suspend fun execute(context: ICommandContext) {
        val title = context.getTranslation("$root.title")
        val web = context.webManager

        val eb = Embedder(context)
            .setTitle(title)
            .setImage(getRandomLynxUrl(web, context.container.settings.api.imgHoard.token))
        sendEmbedRsp(context, eb.build())
    }

    private suspend fun getRandomLynxUrl(webManager: WebManager, token: String): String {
        val reply = WebUtils.getJsonFromUrl(
            webManager.httpClient, "https://api.miki.bot/images/random?tags=lynx",
            headers = mapOf(Pair("Authorization", token))
        ) ?: return MISSING_IMAGE_URL
        return reply.getString("url")
    }
}