package com.pixelatedsource.jda.commands;

import com.jagrosh.jdautilities.commandclient.Command;
import com.jagrosh.jdautilities.commandclient.CommandEvent;
import com.pixelatedsource.jda.Helpers;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.User;

import java.text.SimpleDateFormat;
import java.util.Date;

public class PlayerinfoCommand extends Command {

    public PlayerinfoCommand() {
        this.name = "playerinfo";
        this.help = "Gives you info about a specific player.";
        this.aliases = new String[] { "profile", "userinfo", "memberinfo", "playerprofile" };
    }

    @Override
    protected void execute(CommandEvent event) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm ss");
        String[] args = event.getArgs().split(" ");
        User user;
        User usr2;
        if (args[0].equalsIgnoreCase(""))  usr2 = event.getAuthor(); else if (event.getMessage().getMentionedUsers().size() == 0) usr2 = event.getJDA().getUserById(args[0]);
        else usr2 = event.getMessage().getMentionedUsers().get(0);
        user = usr2;
        if (user == null) user = event.getAuthor();
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(Helpers.EmbedColor);
        eb.setTitle(user.getName() + "'s profile");
        eb.setImage(user.getAvatarUrl());
        if (event.getGuild().getMember(user) == null) {
            eb.addField("Avatar:", "[Download](" + user.getAvatarUrl() + ")", true);
            eb.addField("ID:", user.getId(), true);
            eb.addField("Discord join date:", String.valueOf(user.getCreationTime().toLocalDate()), false);
            eb.addField("Bot:", String.valueOf(user.isBot()), false);
            eb.addField("Member of this guild:", "false", false);
        } else {
            Member member = event.getGuild().getMember(user);
            String nickname = member.getNickname();
            if (nickname == null) nickname = "No nickname";
            eb.addField("Avatar:", "[Download](" + user.getAvatarUrl() + ")", true);
            eb.addField("Nickname:", nickname, false);
            eb.addField("Status:", member.getOnlineStatus().toString(), false);
            eb.addField("Playing:", String.valueOf(member.getGame()), false);
            eb.addField("ID:", user.getId(), false);
            eb.addField("Discord join date:", String.valueOf(simpleDateFormat.format(Date.from(user.getCreationTime().toInstant()))) + "s", true);
            eb.addField("Guild join date:", String.valueOf(simpleDateFormat.format(Date.from(member.getJoinDate().toInstant()))) + "s", true);
            eb.addField("Bot:", String.valueOf(user.isBot()), false);
            eb.addField("Member of this guild:", "true", false);
        }
        eb.setFooter(Helpers.getFooterStamp(), Helpers.getFooterIcon());
        event.reply(eb.build());
    }
}
