package com.learnspigot.bot.reference

import com.learnspigot.bot.Registry
import net.dv8tion.jda.api.interactions.commands.build.OptionData

class MappingProvider {

    fun getSuggestions(version: String?): Collection<String> {
        return Registry.INDEX.mappings
            .map { it.version }
            .filter { it.startsWith(version?:"") }
            .stream().limit(OptionData.MAX_CHOICES.toLong()).toList()
    }

}