package com.learnspigot.bot.reference

import com.learnspigot.bot.Registry
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import revxrsal.commands.jda.actor.SlashCommandActor
import revxrsal.commands.node.ExecutionContext
import java.util.regex.Pattern

class ReferenceEntryProvider {

    val suggestionCache: MutableMap<String, List<Command.Choice>> = mutableMapOf()

    fun terms(input: String): List<String>{
        val set = mutableSetOf<String>()
        for (str in input.split(Regex("\\s+"))) {
            if(str.lowercase()==str){
                set.add(str)
                continue
            }
            val matcher = Pattern.compile("[A-Z]?[a-z]+|[A-Z]+(?=[A-Z][a-z]|$)").matcher(str)
            while (matcher.find())
                set.add(matcher.group())
        }
        return set.sorted()
    }

    fun suggest(inp: String?,mappingVersion: String?):List<Command.Choice>{
        val input = inp ?: ""
        val inputTerms = terms(input)
        val cacheKey = "$mappingVersion:${inputTerms.joinToString(",")}"
        if(cacheKey in suggestionCache)
            return suggestionCache[cacheKey] ?: emptyList()
        val mapping = Registry.INDEX.mappings.find { it.version==mappingVersion }
        if(mappingVersion!=null&&mapping==null)return emptyList()

        val res = (mapping?.entries ?: Registry.INDEX.entries)
            .filter { entry -> inputTerms.all { entry.name.contains(it) } }
            .sortedByDescending { entry ->
                inputTerms.count { entry.name.contains(it) } +
                        (if(entry.simpleName==input) 1 else 0) +
                        (if(entry.simpleName.contains(input)) 1 else 0)
            }
            .map {
                Command.Choice("${it.kind.nativeEmoji.formatted} ${it.simpleName} (${it.name.tryShortenPackage()})".shortenTo100(),
                    (mapping?.entries ?: Registry.INDEX.entries).indexOf(it).toLong())
            }
            .stream().limit(OptionData.MAX_CHOICES.toLong()).toList()
        suggestionCache[cacheKey] = res
        return res

    }

    // most common and recognized packages only
    fun String.tryShortenPackage() = this
        .replace("net.minecraft.server","NMS")
        .replace("net.minecraft","MC")
        .replace("org.bukkit","BKT")
        .replace("com.destroystokyo.paper","PPR")
        .replace("io.papermc.paper","PPR")
        .replace("org.spigotmc","SPT")
    fun String.shortenTo100() = if(length <= 100) this else "${substring(0, 97)}..."

}