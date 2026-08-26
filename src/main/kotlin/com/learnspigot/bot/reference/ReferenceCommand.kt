package com.learnspigot.bot.reference

import com.learnspigot.bot.Registry
import com.learnspigot.bot.Server
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData

// TODO Lamp suggestion providers don't support name-value pairs, yet.
class ReferenceCommand {

    init {
        ReferenceAutocompleteListener.command = this
    }

    fun register(){
        Server.GUILD.upsertCommand(Commands.slash("reference","Reference a class")
            .addSubcommands(
                SubcommandData("class","Reference a class from an API")
                    .addOption(OptionType.STRING,"entry","The reference entry.",true,true),
                SubcommandData("nms","Reference a class from the NMS mappings")
                    .addOption(OptionType.STRING,"version","The mapping version.",true,true)
                    .addOption(OptionType.STRING,"entry","The reference entry.",true,true)
            )
        ).queue()
    }

    fun onReference(event: SlashCommandInteractionEvent, entry:String){
        if(Registry.INDEX.entries.size <= entry.toLong()){
            event.reply("Could not find that entry.").setEphemeral(true).queue()
            return
        }
        val entry = Registry.INDEX.entries[entry.toInt()]

        event.replyFormat("# ${entry.kind.emoji.formatted}[`${entry.simpleName}`](<${entry.url}>)")
            .setEphemeral(event.channelType!= ChannelType.GUILD_PUBLIC_THREAD||
            event.guildChannel.asThreadChannel().parentChannel.id != Server.CHANNEL_HELP.id)
            .queue()
    }


    fun onReferenceNms(event: SlashCommandInteractionEvent, version: String, entry:String){
        val mapping = Registry.INDEX.mappings.find { it.version == version }
        if(mapping==null){
            event.reply("Could not find that mapping. Did you put in the right version?")
                .setEphemeral(true)
                .queue()
            return
        }

        if(mapping.entries.size <= entry.toLong()){
            event.reply("Could not find that entry in $version mappings.")
                .setEphemeral(true)
                .queue()
            return
        }

        val indexEntry = mapping.entries[entry.toInt()]

        event.replyFormat("# ${indexEntry.kind.emoji.formatted}[`${indexEntry.simpleName}`](<${indexEntry.url}>)")
            .setEphemeral(event.channelType!= ChannelType.GUILD_PUBLIC_THREAD||
                    event.guildChannel.asThreadChannel().parentChannel.id != Server.CHANNEL_HELP.id)
            .queue()

    }

}