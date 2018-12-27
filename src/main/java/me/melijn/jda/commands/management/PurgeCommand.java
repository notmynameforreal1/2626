package me.melijn.jda.commands.management;

import me.melijn.jda.Helpers;
import me.melijn.jda.blub.Category;
import me.melijn.jda.blub.Command;
import me.melijn.jda.blub.CommandEvent;
import me.melijn.jda.blub.Need;
import me.melijn.jda.utils.MessageHelper;
import net.dv8tion.jda.core.Permission;

import java.util.concurrent.TimeUnit;

import static me.melijn.jda.Melijn.PREFIX;

public class PurgeCommand extends Command {

    public PurgeCommand() {
        this.commandName = "purge";
        this.description = "Deletes messages";
        this.usage = PREFIX + commandName + " [1 - 1000]";
        this.category = Category.MANAGEMENT;
        this.permissions = new Permission[]{
                Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY
        };
        this.needs = new Need[]{Need.GUILD};
        this.id = 55;
    }

    @Override
    protected void execute(CommandEvent event) {
        if (Helpers.hasPerm(event.getMember(), commandName, 1)) {
            String[] args = event.getArgs().split("\\s+");
            if (args.length == 1 && args[0].matches("^([1-9][0-9]{0,2}|1000)$")) {
                int toPurgeAmount = Integer.parseInt(args[0]);
                event.getTextChannel().getIterableHistory().takeAsync(toPurgeAmount + 1).thenAccept(messages -> {
                    messages.forEach(message -> MessageHelper.purgedMessageDeleter.put(message.getIdLong(), event.getAuthorId()));
                    event.getTextChannel().purgeMessages(messages);
                    event.getTextChannel().sendMessage("**Done**").queue(m -> {
                        m.delete().queueAfter(3, TimeUnit.SECONDS);
                        MessageHelper.botDeletedMessages.add(m.getIdLong());
                    });
                });
            } else {
                MessageHelper.sendUsage(this, event);
            }
        } else {
            event.reply("You need the permission `" + commandName + "` to execute this command.");
        }
    }
}
