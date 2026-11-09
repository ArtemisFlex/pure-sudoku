package com.puresudoku.core.words

import java.io.BufferedReader
import java.io.Reader

/** One dictionary word with what the word games need to know about it. */
class Lex(
    val word: String,
    /** Frequency on the Zipf scale ×10 (60 = very common, 30 = uncommon). */
    val zipf: Int,
    val syllables: Int,
    /** First sound (CMU phoneme), e.g. "F" for both "fish" and "phone". */
    val first: String,
    /** Sounds from the last stressed vowel on; equal keys rhyme. */
    val rhyme: String,
    /** Parts of speech: n, v, a (adjective), r (adverb). */
    val pos: String,
)

/**
 * The bundled word data (built by tools/build_words.py from WordNet, CMUdict
 * and wordfreq). Loaded lazily from the app's assets on first use.
 */
object WordData {
    @Volatile private var opener: ((String) -> Reader)? = null

    /** Call once at startup with a way to open files from assets/words/. */
    fun init(open: (String) -> Reader) { if (opener == null) opener = open }

    private fun lines(name: String): Sequence<String> {
        val r = (opener ?: error("WordData.init not called"))(name)
        return BufferedReader(r).readLines().asSequence().filter { it.isNotBlank() }
    }

    val lexicon: Map<String, Lex> by lazy {
        lines("lexicon.tsv").map { it.split('\t') }.associate { p ->
            p[0] to Lex(p[0], p[1].toInt(), p[2].toInt(), p[3], p[4], p[5])
        }
    }

    val byLength: Map<Int, List<Lex>> by lazy { lexicon.values.groupBy { it.word.length } }

    val clues: Map<String, String> by lazy { lines("clues.tsv").map { it.split('\t') }.associate { it[0] to it[1] } }

    /** word -> (synonyms, antonyms) */
    val thesaurus: Map<String, Pair<List<String>, List<String>>> by lazy {
        lines("thesaurus.tsv").map { it.split('\t') }.associate { p ->
            p[0] to (p.getOrElse(1) { "" }.split(',').filter { it.isNotEmpty() } to p.getOrElse(2) { "" }.split(',').filter { it.isNotEmpty() })
        }
    }

    /** Hand-written pairs: ('S' synonym | 'A' antonym, word, answer). */
    val pairs: List<Triple<Char, String, String>> by lazy {
        lines("pairs.tsv").map { it.split('\t') }.map { Triple(it[0][0], it[1], it[2]) }.toList()
    }

    val themes: Map<String, List<String>> by lazy { lines("themes.tsv").map { it.split('\t') }.associate { it[0] to it[1].split(',') } }

    /** Season -> season words; "_filler" -> small connecting words. */
    val haiku: Map<String, List<String>> by lazy { lines("haiku.tsv").map { it.split('\t') }.associate { it[0] to it[1].split(',') } }

    fun syllables(w: String): Int = lexicon[w.lowercase()]?.syllables ?: estimateSyllables(w)

    /** Fallback for words not in the lexicon (vowel groups). */
    fun estimateSyllables(w: String): Int {
        val s = w.lowercase().replace(Regex("[^a-z]"), "")
        if (s.isEmpty()) return 0
        var n = Regex("[aeiouy]+").findAll(s).count()
        if (s.endsWith("e") && !s.endsWith("le") && n > 1) n--
        return n.coerceAtLeast(1)
    }

    fun relatedForms(a: String, b: String) = a.take(4) == b.take(4) || a in b || b in a
}
