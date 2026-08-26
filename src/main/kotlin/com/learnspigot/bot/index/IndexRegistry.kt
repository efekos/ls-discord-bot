@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.learnspigot.bot.index

import com.learnspigot.bot.Bot
import com.learnspigot.bot.index.IndexEntry.Companion.toIndexEntry
import okio.ByteString.Companion.decodeBase64
import org.bson.*
import org.bson.codecs.BsonDocumentCodec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.io.BasicOutputBuffer
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*

class IndexRegistry {
    val entries: MutableList<IndexEntry> = mutableListOf()
    val mappings: MutableList<Mapping> = mutableListOf()
    val base = Bot.fromEnv("INDEX_CACHE_DIRECTORY")

    private fun String.encodeBase64() = String(Base64.getEncoder().encode(toByteArray()))

    init {
        loadAllCache()
    }

    private fun loadAllCache() {
        val path = Path(Bot.fromEnv("INDEX_CACHE_DIRECTORY"))
        if (!path.exists() || !path.isDirectory()) {
            System.err.println("[INDEX] Cache directory could not be found, not loading any cache")
            return
        }

        for (file in path.listDirectoryEntries("*.bson")) {
            loadCacheEntries(file, entries)
        }

        val mappingsPath = Path(path.toString(), "mapping")
        if (mappingsPath.exists() && mappingsPath.isDirectory()) {
            for (file in mappingsPath.listDirectoryEntries("*.bson")) {
                val fileNameExtension = file.fileName.toString()
                val version = fileNameExtension.substringBeforeLast('.')
                val entries = mutableListOf<IndexEntry>()
                loadCacheEntries(file, entries)
                mappings.add(Mapping(version, entries))
            }
        }
    }

    private fun AbstractBsonReader.resetState() {
        AbstractBsonReader::class.java
            .getDeclaredField("state")
            .also { it.isAccessible = true }
            .set(this, AbstractBsonReader.State.INITIAL)
    }

    private fun loadCacheEntries(
        file: Path,
        entries: MutableList<IndexEntry>,
    ) {
        BsonBinaryReader(ByteBuffer.wrap(file.readBytes())).use { reader ->
            val codec = BsonDocumentCodec()
            val entrypoint =
                file.name
                    .substringBeforeLast('.')
                    .decodeBase64()
                    .toString()
            val s = codec.decode(reader, DecoderContext.builder().build()).getInt32("size")!!.value
            reader.resetState()
            for (n in 0 until s) {
                entries.add(codec.decode(reader, DecoderContext.builder().build()).toIndexEntry(entrypoint))
                reader.resetState()
            }
        }
    }

    fun saveCache(
        entrypoint: String,
        entries: List<IndexEntry>,
    ) {
        val codec = BsonDocumentCodec()

        Path(base, "${entrypoint.encodeBase64()}.bson")
            .also { if (!it.exists()) it.createFile() }
            .outputStream()
            .use { out ->
                val put = BasicOutputBuffer()
                BsonBinaryWriter(put).use {
                    codec.encode(
                        it,
                        BsonDocument("size", BsonInt32(entries.size)),
                        EncoderContext.builder().build(),
                    )
                    for (entry in entries) {
                        codec.encode(it, entry.toBson(), EncoderContext.builder().build())
                    }
                }
                out.write(put.toByteArray())
                out.flush()
            }
    }

    fun removeCache(entrypoint: String) {
        Path(base, "${entrypoint.encodeBase64()}.bson").deleteIfExists()
        entries.removeIf { it.entrypoint == entrypoint }
    }

    fun removeMappingCache(version: String) {
        Path(base, "mapping", "$version.bson").deleteIfExists()
        mappings.removeIf { it.version == version }
    }

    fun saveMapping(mapping: Mapping) {
        val codec = BsonDocumentCodec()

        Path(base, "mapping", "${mapping.version}.bson")
            .also {
                if (!it.exists()) {
                    it.parent.createDirectories()
                    it.createFile()
                }
            }.outputStream()
            .use { out ->
                val put = BasicOutputBuffer()
                BsonBinaryWriter(put).use {
                    codec.encode(
                        it,
                        BsonDocument("size", BsonInt32(mapping.entries.size)),
                        EncoderContext.builder().build(),
                    )
                    for (entry in mapping.entries) {
                        codec.encode(it, entry.toBson(), EncoderContext.builder().build())
                    }
                }
                out.write(put.toByteArray())
                out.flush()
            }
    }
}
