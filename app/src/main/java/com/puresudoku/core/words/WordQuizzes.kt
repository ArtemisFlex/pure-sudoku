package com.puresudoku.core.words

import com.puresudoku.core.Difficulty
import kotlin.random.Random

/** One multiple-choice round. [explain] is shown after answering. */
class QuizQuestion(
    val prompt: String,
    val subject: String,
    val options: List<String>,
    val correct: Int,
    val explain: String,
)

object QuizRules {
    data class Config(val rounds: Int, val options: Int, val minZipf: Int, val maxZipf: Int, val basePoints: Int, val parSeconds: Int)

    fun config(d: Difficulty) = when (d) {
        Difficulty.EASY -> Config(10, 3, 48, 99, 10, 120)
        Difficulty.MEDIUM -> Config(10, 4, 42, 99, 15, 150)
        Difficulty.HARD -> Config(12, 4, 36, 52, 25, 180)
        Difficulty.EXPERT -> Config(12, 5, 33, 46, 35, 210)
        Difficulty.MASTER -> Config(14, 5, 30, 42, 50, 240)
        Difficulty.EXTREME -> Config(15, 6, 26, 38, 75, 270)
    }

    private fun lex(w: String) = WordData.lexicon[w]

    private fun distractors(count: Int, posOf: String, avoid: Set<String>, band: IntRange, rng: Random, pred: (Lex) -> Boolean = { true }): List<String> {
        val pool = WordData.lexicon.values.filter { l ->
            l.zipf in band && l.word.length >= 3 && l.pos.any { it in posOf } && pred(l) &&
                l.word !in avoid && avoid.none { WordData.relatedForms(it, l.word) }
        }
        return pool.shuffled(rng).take(count).map { it.word }
    }

    // ------------------------------------------------------------ synonyms & antonyms

    fun synonymRound(d: Difficulty, round: Int, rng: Random, used: MutableSet<String>): QuizQuestion {
        val cfg = config(d)
        val antonym = rng.nextBoolean()
        val easy = d == Difficulty.EASY || d == Difficulty.MEDIUM
        repeat(400) {
            val (word, answer) = if (easy) {
                val p = WordData.pairs.filter { it.first == (if (antonym) 'A' else 'S') }.random(rng)
                if (rng.nextBoolean()) p.second to p.third else p.third to p.second
            } else {
                val e = WordData.thesaurus.entries.random(rng)
                val list = if (antonym) e.value.second else e.value.first
                if (list.isEmpty()) return@repeat
                e.key to list.random(rng)
            }
            if (word in used) return@repeat
            val lw = lex(word) ?: return@repeat
            val la = lex(answer) ?: return@repeat
            if (!easy && (lw.zipf !in cfg.minZipf..cfg.maxZipf || la.zipf < cfg.minZipf - 4)) return@repeat
            val (syns, ants) = WordData.thesaurus[word] ?: (emptyList<String>() to emptyList())
            val avoid = (syns + ants + listOf(word, answer)).toSet() +
                WordData.pairs.filter { it.second == word || it.third == word }.flatMap { listOf(it.second, it.third) }
            val band = (la.zipf - 8)..(la.zipf + 8)
            val wrong = distractors(cfg.options - 1, la.pos.ifEmpty { "nva" }, avoid, band, rng)
            if (wrong.size < cfg.options - 1) return@repeat
            used += word
            val opts = (wrong + answer).shuffled(rng)
            return QuizQuestion(
                if (antonym) "Opposite of" else "Same meaning as", word, opts, opts.indexOf(answer),
                if (antonym) "\"$answer\" is the opposite of \"$word\"." else "\"$answer\" means much the same as \"$word\".",
            )
        }
        error("no synonym round")
    }

    // ------------------------------------------------------------ rhymes

    fun rhymeRound(d: Difficulty, round: Int, rng: Random, used: MutableSet<String>): QuizQuestion {
        val cfg = config(d)
        val byRhyme = WordData.lexicon.values.filter { it.rhyme.isNotEmpty() && it.zipf >= cfg.minZipf - 6 }.groupBy { it.rhyme }
        val tricky = d.ordinal >= Difficulty.HARD.ordinal
        repeat(400) {
            val target = WordData.lexicon.values.random(rng)
            if (target.zipf !in cfg.minZipf..cfg.maxZipf || target.word in used || target.word.length < 3 || target.rhyme.isEmpty()) return@repeat
            val rhymes = byRhyme[target.rhyme].orEmpty().filter { it.word != target.word && !WordData.relatedForms(it.word, target.word) }
            if (rhymes.isEmpty()) return@repeat
            val answer = rhymes.random(rng)
            val avoidRhyme = target.rhyme
            // Tricky levels use "eye rhymes": same spelling ending, different sound.
            val end = target.word.takeLast(3)
            val eye = if (tricky) WordData.lexicon.values.filter {
                it.word.endsWith(end) && it.rhyme != avoidRhyme && it.zipf >= cfg.minZipf - 6 && it.word != target.word
            }.shuffled(rng).take(if (d.ordinal >= Difficulty.EXPERT.ordinal) 2 else 1).map { it.word } else emptyList()
            val band = (answer.zipf - 10)..(answer.zipf + 10)
            val rest = distractors(cfg.options - 1 - eye.size, "nvar", setOf(target.word, answer.word) + eye, band, rng) { it.rhyme != avoidRhyme }
            val wrong = eye + rest
            if (wrong.size < cfg.options - 1) return@repeat
            used += target.word
            val opts = (wrong + answer.word).shuffled(rng)
            val trap = eye.firstOrNull()?.let { " \"$it\" looks similar but sounds different." } ?: ""
            return QuizQuestion("Which word rhymes with", target.word, opts, opts.indexOf(answer.word),
                "\"${answer.word}\" rhymes with \"${target.word}\".$trap")
        }
        error("no rhyme round")
    }

    // ------------------------------------------------------------ alliteration

    private val SOUNDS = listOf("B", "K", "D", "F", "G", "HH", "JH", "L", "M", "N", "P", "R", "S", "T", "W", "SH", "CH", "TH", "V", "Y")
    private val SOUND_NAME = mapOf("K" to "k", "HH" to "h", "JH" to "j", "SH" to "sh", "CH" to "ch", "TH" to "th", "Y" to "y")
    fun soundName(s: String) = if (s[0] in "AEIOU") "a vowel" else "\"" + (SOUND_NAME[s] ?: s.lowercase()) + "\""

    fun alliterationRound(d: Difficulty, round: Int, rng: Random, used: MutableSet<String>): QuizQuestion {
        val cfg = config(d)
        val size = cfg.options + 1 // phrase length
        val tricky = d.ordinal >= Difficulty.HARD.ordinal
        val pool = WordData.lexicon.values.filter { it.zipf in cfg.minZipf..cfg.maxZipf && it.word.length >= 3 && it.pos.any { p -> p in "nav" } && it.word !in used }
        repeat(400) {
            val sound = SOUNDS.random(rng)
            val same = pool.filter { it.first == sound }.shuffled(rng)
            if (same.size < size) return@repeat
            val group = ArrayList<Lex>()
            val lead = same[0].word[0]
            if (!tricky) {
                // easy levels: same sound AND same first letter
                for (l in same) { if (group.size >= size - 1) break; if (l.word[0] == lead && group.none { WordData.relatedForms(it.word, l.word) }) group += l }
                if (group.size < size - 1) return@repeat
            }
            if (tricky) {
                // include spellings that hide the sound: ph/f, c/k, kn/n, wr/r, wh/w, g/j
                same.firstOrNull { it.word[0] != same[0].word[0] }?.let { group += it }
            }
            for (l in same) { if (group.size >= size - 1) break; if (l !in group && group.none { WordData.relatedForms(it.word, l.word) }) group += l }
            if (group.size < size - 1) return@repeat
            val letter = group.groupingBy { it.word[0] }.eachCount().maxBy { it.value }.key
            val odd = if (tricky) {
                // the odd word starts with the same LETTER but a different sound (e.g. city vs cat)
                pool.filter { it.word[0] == letter && it.first != sound }.randomOrNull(rng)
                    ?: pool.filter { it.first != sound }.random(rng)
            } else pool.filter { it.first != sound && it.word[0] != letter }.random(rng)
            val words = (group.map { it.word } + odd.word).shuffled(rng)
            used += words
            return QuizQuestion(
                "Tap the word that breaks the alliteration", "", words, words.indexOf(odd.word),
                "The others start with a ${soundName(sound)} sound; \"${odd.word}\" starts with ${soundName(odd.first)}.",
            )
        }
        error("no alliteration round")
    }
}

// =====================================================================
// Haiku
// =====================================================================

class HaikuPrompt(val season: String, val tiles: List<String>, val seasonWords: Set<String>, val required: List<String>)

object HaikuRules {
    data class Config(val poems: Int, val tiles: Int, val needSeason: Boolean, val noRepeats: Boolean, val required: Int, val basePoints: Int, val parSeconds: Int)
    val LINES = intArrayOf(5, 7, 5)

    fun config(d: Difficulty) = when (d) {
        Difficulty.EASY -> Config(2, 26, false, false, 0, 10, 240)
        Difficulty.MEDIUM -> Config(2, 24, true, false, 0, 15, 300)
        Difficulty.HARD -> Config(3, 22, true, true, 0, 25, 420)
        Difficulty.EXPERT -> Config(3, 18, true, true, 1, 35, 540)
        Difficulty.MASTER -> Config(3, 16, true, true, 2, 50, 600)
        Difficulty.EXTREME -> Config(3, 14, true, true, 3, 75, 660)
    }

    fun prompt(d: Difficulty, rng: Random, exclude: Set<String> = emptySet()): HaikuPrompt {
        val cfg = config(d)
        val seasons = WordData.haiku.keys.filter { !it.startsWith("_") }
        val season = seasons.filter { it !in exclude }.ifEmpty { seasons }.random(rng)
        while (true) {
            val sw = WordData.haiku.getValue(season).filter { WordData.lexicon.containsKey(it) }.shuffled(rng)
            val fill = WordData.haiku.getValue("_filler").shuffled(rng)
            val extra = WordData.lexicon.values.filter { it.zipf >= 45 && it.syllables in 1..3 && it.pos.any { p -> p in "nva" } && it.word.length >= 3 }
                .shuffled(rng).take(4).map { it.word }
            val nSeason = cfg.tiles * 4 / 10
            val nFill = cfg.tiles * 4 / 10
            val tiles = (sw.take(nSeason) + fill.take(nFill) + extra).distinct().take(cfg.tiles)
            // the tray must be able to make 5-7-5 even without repeating words
            val syl = tiles.map { WordData.syllables(it) }
            if (syl.sum() < 22 || syl.count { it == 1 } < 6) continue
            val seasonWords = tiles.filter { it in sw }.toSet()
            val required = seasonWords.shuffled(rng).take(cfg.required)
            return HaikuPrompt(season, tiles.shuffled(rng), seasonWords, required)
        }
    }

    fun lineSyllables(words: List<String>) = words.sumOf { WordData.syllables(it) }
}
