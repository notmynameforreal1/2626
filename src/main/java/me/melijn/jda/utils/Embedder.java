package me.melijn.jda.utils;

import me.melijn.jda.Helpers;
import me.melijn.jda.commands.management.SetEmbedColorCommand;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Guild;

public class Embedder extends EmbedBuilder {

    public Embedder(Guild guild) {
        if (guild != null) {
            setColor(SetEmbedColorCommand.embedColorCache.getUnchecked(guild.getIdLong()));
        } else setColor(Helpers.embedColor);
    }

    public Embedder(long guildId) {
        setColor(SetEmbedColorCommand.embedColorCache.getUnchecked(guildId));
    }
}
