package me.melijn.jda.commands.music;

import me.melijn.jda.Helpers;
import me.melijn.jda.blub.Category;
import me.melijn.jda.blub.Command;
import me.melijn.jda.blub.CommandEvent;
import me.melijn.jda.blub.Need;
import me.melijn.jda.audio.Lava;

import static me.melijn.jda.Melijn.PREFIX;

public class SummonCommand extends Command {

    private Lava lava = Lava.lava;

    public SummonCommand() {
        this.commandName = "summon";
        this.description = "Summons the bot to your channel";
        this.aliases = new String[]{"join", "here"};
        this.usage = PREFIX + commandName;
        this.category = Category.MUSIC;
        this.needs = new Need[]{Need.GUILD, Need.SAME_VOICECHANNEL_OR_DISCONNECTED};
        this.id = 12;
    }


    @Override
    protected void execute(CommandEvent event) {
        if (Helpers.hasPerm(event.getMember(), commandName, 0)) {
            if (lava.tryToConnectToVC(event, event.getGuild(), event.getMember().getVoiceState().getChannel())) {
                event.reply("I have been summoned to your channel");
            }
        } else {
            event.reply(Helpers.noPerms);
        }
    }
}
