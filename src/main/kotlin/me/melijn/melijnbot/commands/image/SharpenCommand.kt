package me.melijn.melijnbot.commands.image

import me.melijn.melijnbot.commandutil.image.ImageCommandUtil
import me.melijn.melijnbot.objects.command.AbstractCommand
import me.melijn.melijnbot.objects.command.CommandCategory
import me.melijn.melijnbot.objects.command.CommandContext
import me.melijn.melijnbot.objects.utils.ImageUtils
import net.dv8tion.jda.api.Permission

class SharpenCommand : AbstractCommand("command.sharpen") {

    init {
        id = 136
        name = "sharpen"
        aliases = arrayOf("sharpenGif")
        discordChannelPermissions = arrayOf(Permission.MESSAGE_ATTACH_FILES)
        commandCategory = CommandCategory.IMAGE
    }

    override suspend fun execute(context: CommandContext) {
        if (context.commandParts[1].equals("sharpenGif", true)) {
            executeGif(context)
        } else {
            executeNormal(context)
        }
    }

    private suspend fun executeNormal(context: CommandContext) {
        ImageCommandUtil.executeNormalEffect(context, effect = { image, i ->
            ImageUtils.sharpen(image, i)

        }, hasOffset = true, defaultOffset = {
            1
        }, offsetRange = { img ->
            IntRange(1, Integer.max(img.height, img.width))

        })
    }

    private suspend fun executeGif(context: CommandContext) {
        ImageCommandUtil.executeGifEffect(context, effect = { image, i ->
            ImageUtils.sharpen(image, i)

        }, hasOffset = true, defaultOffset = { 1

        }, offsetRange = { img ->
            IntRange(1, Integer.max(img.height, img.width))

        })
    }
}