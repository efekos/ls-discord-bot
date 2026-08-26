package com.learnspigot.bot.index

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.learnspigot.bot.Bot
import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.reader
import kotlin.io.path.writeText
import kotlin.io.path.writer

class IndexRegistry {

    companion object {
        val gson: Gson = GsonBuilder().disableHtmlEscaping()
            .setPrettyPrinting().create()
    }

    val entries: MutableList<IndexEntry> = mutableListOf()
    val mappings: MutableList<Mapping> = mutableListOf()
    val base = Bot.fromEnv("INDEX_CACHE_DIRECTORY")

    private fun String.encodeBase64() = String(Base64.getEncoder().encode(toByteArray()))

    init {
        loadAllCache()
    }

    private fun loadAllCache() {
        val path = Path(Bot.fromEnv("INDEX_CACHE_DIRECTORY"))
        if(!path.exists() || !path.isDirectory()){
            System.err.println("[INDEX] Cache directory could not be found, not loading any cache")
            return
        }

        for(file in path.listDirectoryEntries("*.json"))
            loadCacheEntries(file,entries)

        val mappingsPath = Path(path.toString(), "mapping")
        if(mappingsPath.exists() && mappingsPath.isDirectory())
            for (file in mappingsPath.listDirectoryEntries("*.json")) {
                val fileNameExtension = file.fileName.toString()
                val version = fileNameExtension.substringBeforeLast('.')
                val entries = mutableListOf<IndexEntry>()
                loadCacheEntries(file, entries)
                mappings.add(Mapping(version, entries))
            }
    }

    private fun loadCacheEntries(file: Path, entries: MutableList<IndexEntry>) {
        val json = gson.fromJson(file.reader(), JsonElement::class.java)
        if (json == null || !json.isJsonArray) {
            System.err.println("[INDEX] cache file '$file' is corrupted")
            return
        }
        for (element in json.asJsonArray) {
            if (!element.isJsonObject) {
                System.err.println("[INDEX] cache file '$file' has a corrupted element, skipping")
                continue
            }
            gson.fromJson(element.asJsonObject, IndexEntry::class.java)
                .apply { if(entries.none { it.name==this.name }) entries.add(this) }
        }
    }

    fun saveCache(entrypoint: String,entries: List<IndexEntry>) {
        Path(base,"${entrypoint.encodeBase64()}.json")
            .writeText(gson.toJson(entries))
    }

    fun removeCache(entrypoint: String) {
        Path(base,"${entrypoint.encodeBase64()}.json").deleteIfExists()
        entries.removeIf { it.entrypoint == entrypoint }
    }

    fun removeMappingCache(version: String) {
        Path(base,"mapping","$version.json").deleteIfExists()
        mappings.removeIf { it.version == version }
    }

    fun saveMapping(mapping: Mapping) {
        val mappingPath = Path(base, "mapping", "${mapping.version}.json")
        mappingPath.parent.createDirectories()
        mappingPath.writeText(gson.toJson(mapping.entries))
    }

}