package com.learnspigot.bot.index

import com.learnspigot.bot.Bot
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji

data class IndexEntry(
    val simpleName: String,
    val name: String,
    val url: String,
    val kind: IndexEntryKind,
    val entrypoint: String
)

enum class IndexEntryKind(val nativeEmoji: UnicodeEmoji) {
        EVENT(Emoji.fromUnicode("⚡")),
        CLASS(Emoji.fromUnicode("\uD83D\uDCD5")),
        ABSTRACT_CLASS(Emoji.fromUnicode("\uD83D\uDCC4")),
        INTERFACE(Emoji.fromUnicode("\uD83D\uDCD8")),
        ENUM(Emoji.fromUnicode("\uD83E\uDE9F")),
        RECORD(Emoji.fromUnicode("\uD83D\uDCBD")),
        ANNOTATION(Emoji.fromUnicode("\uD83C\uDFF7\uFE0F")),
        UNKNOWN(Emoji.fromUnicode("❔"));

        val emoji: Emoji get() = Bot.fromEnvUnsafe("${this.name}_EMOJI_ID")?.let {
            Emoji.fromCustom(this.name.lowercase(),it.toLong(),false)
        }?: nativeEmoji

}

data class Mapping(val version: String, val entries: List<IndexEntry>)

data class GitHubContentsResponse(
    val name: String,
    val path: String,
    val url: String,
    val download_url: String?,
    val type: String
)