package com.puresudoku.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.puresudoku.core.words.WordData
import com.puresudoku.ui.theme.loadPoppins
import com.puresudoku.web.res.Res
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.w3c.fetch.Response

private val WORD_FILES = listOf("lexicon.tsv", "clues.tsv", "thesaurus.tsv", "themes.tsv", "pairs.tsv", "haiku.tsv")

private suspend fun fetchText(url: String): String {
    val r = window.fetch(url).await<Response>()
    if (!r.ok) error("Could not load $url (${r.status})")
    return r.text().await<JsString>().toString()
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalResourceApi::class)
fun main() {
    MainScope().launch {
        // Fonts and word lists load before the first frame: the shared code reads them synchronously.
        val words = WORD_FILES.map { name -> async { name to runCatching { fetchText("words/$name") }.getOrDefault("") } }
        val medium = async { Res.readBytes("font/poppins_medium.ttf") }
        val bold = async { Res.readBytes("font/poppins_bold.ttf") }
        runCatching { loadPoppins(medium.await(), bold.await()) }.onFailure { println("Fonts: $it") }
        val text = words.associate { it.await() }
        WordData.init { name -> text[name] ?: error("Missing word file $name") }
        document.getElementById("loading")?.remove()
        ComposeViewport(document.body!!) { WebApp() }
    }
}
