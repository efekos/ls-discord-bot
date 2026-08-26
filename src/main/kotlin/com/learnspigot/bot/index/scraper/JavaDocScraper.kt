package com.learnspigot.bot.index.scraper

import com.learnspigot.bot.Bot
import com.learnspigot.bot.index.DocumentClient
import com.learnspigot.bot.index.IndexEntry
import com.learnspigot.bot.index.IndexEntryKind
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import java.util.concurrent.CountDownLatch
import java.util.function.Consumer
import java.util.regex.Pattern

/**
 * Scrapes any JavaDoc
 */
class JavaDocScraper(val entrypoint: String) : Scraper {

    private val found: MutableList<IndexEntry> = mutableListOf()

    override fun scrape(afterFinish: Consumer<List<IndexEntry>>){
        Scraper.mainExecutor.submit {
            runCatching {
                if(canLog)println("[${Thread.currentThread().name}] starting to scrape $entrypoint")

                val pageClient = DocumentClient(entrypoint)
                val mainPage = pageClient.openPage()
                val homeUrl = entrypoint.substring(0..entrypoint.lastIndexOf('/'))

                if(canLog)println("[${Thread.currentThread().name}] opened $entrypoint")
                var classes: Elements? = mainPage.select("#all-classes-table\\.tabpanel > .summary-table > .col-first > a, .typeSummary > tbody > tr > .colFirst > a,.indexContainer > ul > li > a")
                val latch = CountDownLatch(classes!!.size)
                for (element in classes) {
                    val href = element.attr("href")
                    if(href.isNotBlank()) Scraper.poolExecutor.submit {
                        scrapeClassPage("$homeUrl$href",latch) { found.add(it) }
                    }
                }

                latch.await()
                classes = null // hand over to ggc
                pageClient.close()
                if(canLog)println("[${Thread.currentThread().name}] finished")
                afterFinish.accept(found)
            }.exceptionOrNull()?.printStackTrace()
        }
    }

    // group1: class type, group2: simple class name
    val classDeclarationRegex: Pattern = Pattern.compile("(enum|interface|class|record|annotation type):\\s+([A-Za-z_$][A-Za-z_$0-9]+)")

    fun scrapeClassPage(url: String, latch: CountDownLatch, adder:Consumer<IndexEntry>){
        runCatching {
            if(canLog)println("[${Thread.currentThread().name}] scraping class $url")
            val client = DocumentClient(url)
            val page = client.openPage()

            val classDeclaration:List<List<String>>? = page.selectFirst("meta[name=\"description\"]")
                ?.attr("content")?.let { str ->
                    val matcher = classDeclarationRegex.matcher(str)
                    val list = mutableListOf<List<String>>()
                    while (matcher.find())
                        list.add(listOf(matcher.group(),matcher.group(1),matcher.group(2)))
                    list
                } ?:
                page.selectFirst("div.description > ul.blockList > li.blockList > pre")
                    ?.let { element -> listOf(
                        listOf(
                            element.text(),
                            element.textNodes().first().nodeValue().replace(Regex("public|private|sealed|non-sealed"),""),
                            element.selectFirst("span.typeNameLabel")!!.text()
                            )
                    ) }

            val className = classDeclaration?.joinToString(".") { it[2] } ?: ""
            val classType = classDeclaration?.last()[1]?.trim() ?: ""

            if(className.isNotBlank()){
                val modifiers = page.selectFirst("#class-description > div > div.type-signature > span.modifiers, div.description > ul.blockList > li.blockList > pre")
                    ?.ownText()?.trim()

                val implements = page.select(".notes > dd > code > a")
                    .map { listOf(it.attr("href"),it.ownText().trim()) }

                val extends = page.select(".inheritance > a")
                    .map { listOf(it.attr("href"),it.ownText().trim()) }

                // for future reference if new entry kinds get added:
                // check if class extends a class: extends.any { it[1]=="full.class.Name" }
                // check if class implements an interface: implements.any { it[0].endsWidth("full/interface/Name.html") && it[1]=="Name" }

                val kind =
                    if(modifiers?.contains("abstract class") == true) IndexEntryKind.ABSTRACT_CLASS
                    else if(classType=="interface" || modifiers?.contains("interface") == true) IndexEntryKind.INTERFACE
                    else if(extends.any {it[1]=="org.bukkit.event.Event"}) IndexEntryKind.EVENT
                    else if(classType=="class" || modifiers?.contains("class") == true) IndexEntryKind.CLASS
                    else if(classType=="enum" || modifiers?.contains("enum") == true) IndexEntryKind.ENUM
                    else if(classType=="record" || modifiers?.contains("record") == true) IndexEntryKind.RECORD
                    else if(classType=="annotation type") IndexEntryKind.ANNOTATION
                    else IndexEntryKind.UNKNOWN

                adder.accept(
                    IndexEntry(
                        className, "${page.packageName}.$className",
                        url, kind, entrypoint
                    )
                )
                if(canLog)println("[${Thread.currentThread().name}] added $className")
            }

            client.close()
            latch.countDown()
        }.exceptionOrNull()?.printStackTrace()
    }

    val packageRegex: Pattern = Pattern.compile("package: ([A-Za-z_0-9$]+(?:\\.[A-Za-z_0-9$]+)+)")

    val Document.packageName get() =
        selectFirst("meta[name=\"description\"]")?.attr("content")
            ?.apply {
                val matcher = packageRegex.matcher(this)
                if(matcher.find()) return matcher.group(1)
            }
            ?: selectFirst("main > .header > .subTitle > a")?.ownText()?.trim()
            ?: selectFirst("div.contentContainer > ul.inheritance")
                ?.text()?.split(" ")?.last()?.substringBeforeLast('.')

}