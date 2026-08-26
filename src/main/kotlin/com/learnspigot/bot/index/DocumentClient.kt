package com.learnspigot.bot.index

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.lang.AutoCloseable

class DocumentClient(val url: String) : AutoCloseable {

    private var doc: Document? = Jsoup.connect(url).get()

    fun openPage():Document{
        return doc?:throw IllegalStateException("Document accessed after close")
    }

    override fun close() {
        if(doc==null) throw IllegalStateException("Document already closed")
        // hand the document over to gc
        doc!!.children().forEach { it.remove() }
        doc!!.attributes().forEach { doc!!.removeAttr(it.key) }
        doc!!.remove()
        doc = null
    }

}