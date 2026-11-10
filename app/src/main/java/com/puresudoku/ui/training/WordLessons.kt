package com.puresudoku.ui.training

import com.puresudoku.core.GameKind

object WordLessons {
    val all: Map<GameKind, Course> = mapOf(
        GameKind.CROSSWORD to Course(
            listOf(
                "Each numbered clue fits the run of white squares that starts at its number, across or down.",
                "Tap a square to pick a word; tap again to switch direction. Type letters with the keyboard.",
                "Crossing words share letters, so every answer helps the ones around it.",
            ),
            listOf(
                listOf("Start with the clues you're sure of, however few.", "Short words fill fast and give crossing letters.", "Plurals often end in S: check the clue's wording."),
                listOf("Use crossing letters: a pattern like _A_E narrows things a lot.", "Match tense: a past-tense clue wants a past-tense answer.", "Skip and come back; fresh crossings make clues easier."),
                listOf("Clue and answer share part of speech: a verb clue has a verb answer.", "Common endings: -ING, -ED, -ER, -LY.", "Think of synonyms, not just the first word that comes to mind."),
                listOf("Fill vowels from crossings, then guess the consonants around them.", "Watch for abbreviations in clues: they hint at short answers.", "Long answers often split into two words."),
                listOf("Read clues literally and figuratively; both meanings can matter.", "Use letter frequency: E, A, R, I, O, T are most common.", "Don't marry an answer: if crossings fight it, rethink it."),
                listOf("Work in sections and finish them to build momentum.", "Try alternative senses of each clue word.", "Every letter must work both across and down; trust the grid."),
            ),
        ),
        GameKind.WORD_SEARCH to Course(
            listOf(
                "Find every listed word hidden in the letter grid.",
                "Words run in straight lines: across, down, diagonal, and on harder levels backwards too.",
                "Drag from the first letter to the last to mark a word.",
            ),
            listOf(
                listOf("Look for each word's rarest letter first: Q, Z, X, J, K.", "Scan row by row for the first letter, then check around it.", "Long words are easier to spot than short ones."),
                listOf("Look for double letters (LL, EE): they stand out.", "Check all eight directions around a starting letter.", "Cross words off as you find them."),
                listOf("Scan for letter pairs instead of single letters.", "Diagonals hide words well: sweep them deliberately.", "Leave the hardest words for last when the grid is clearer."),
                listOf("Backwards words: search for the last letter first.", "Use the outline of found words to narrow empty areas.", "Keep your scanning pattern consistent."),
                listOf("Overlapping words share letters: a found word may hold the start of another.", "Rotate your attention: rows, then columns, then diagonals.", "Short words hide inside longer letter runs."),
                listOf("Speed: glance for distinctive shapes of letter pairs.", "Use the leftover letters as a final check.", "Stay systematic rather than darting around."),
            ),
        ),
        GameKind.HAIKU to Course(
            listOf(
                "A haiku has three lines of 5, 7 and 5 syllables.",
                "Pick words from the bank to build each line; the syllable counter keeps track.",
                "Traditional haiku capture a moment, often in nature, with a turn between images.",
            ),
            listOf(
                listOf("Clap the syllables of each word: 'morn-ing' is two.", "Start with one strong image: a frog, a leaf, the moon.", "Short words help you hit the count exactly."),
                listOf("Use the seasons: spring blossom, summer heat, autumn wind, winter snow.", "Let the middle line be the longest image.", "Read it aloud to check the rhythm."),
                listOf("Show, don't tell: 'cold rain on the gate' rather than 'I feel sad'.", "Add a cutting moment: two images side by side.", "Swap words for synonyms to fix counts."),
                listOf("Use concrete nouns and active verbs.", "Avoid filler words like 'very' or 'the' when you can.", "End with a surprise or shift."),
                listOf("Contrast: big and small, still and moving, old and new.", "Sound matters: soft sounds for calm, hard sounds for energy.", "Every word should earn its syllables."),
                listOf("Aim for a single moment of insight.", "Let the reader fill the gap between images.", "Revise: the second draft is usually better."),
            ),
        ),
        GameKind.ALLITERATION to Course(
            listOf(
                "Alliteration is the repeat of the same starting sound in nearby words: 'big bold bears'.",
                "Each round shows words; tap the one that breaks the sound pattern.",
                "It's about sound, not spelling: 'cat' and 'kite' share a K sound.",
            ),
            listOf(
                listOf("Say each word aloud and listen to the very first sound.", "Watch for silent letters: 'knife' starts with N.", "Compare words in pairs."),
                listOf("Same letter can mean different sounds: 'giant' (J) vs 'goat' (G).", "PH sounds like F: 'phone' alliterates with 'fun'.", "Take your time on the tricky ones."),
                listOf("Soft C (S sound) vs hard C (K sound): 'city' vs 'cat'.", "CH can be 'ch', 'k' or 'sh': 'chair', 'choir', 'chef'.", "Trust your ear over your eyes."),
                listOf("Consonant blends: 'sp' and 's' both start with S, but some sets want the blend.", "Vowel sounds can alliterate too: 'apple' and 'ant'.", "Look for the odd one out quickly, then double-check."),
                listOf("Silent openings: 'gnome', 'wrap', 'psalm'.", "Borrowed words keep foreign sounds: 'chic' starts with SH.", "Mixed sets hide one false friend."),
                listOf("Speed and accuracy: decide fast, verify once.", "Know your tricky letters: C, G, CH, PH, silent K/W/G/P.", "Keep a calm rhythm through the rounds."),
            ),
        ),
        GameKind.SYNONYMS to Course(
            listOf(
                "Synonyms mean the same (big, large). Antonyms mean the opposite (hot, cold).",
                "Each round asks for one or the other: read the question carefully.",
                "Pick the best match among the choices.",
            ),
            listOf(
                listOf("Check whether it asks for 'same' or 'opposite' first.", "Put the word in a sentence and swap each choice in.", "Rule out choices that are a different part of speech."),
                listOf("Some choices are related but not the same: 'hot' and 'warm' are close, not identical.", "Opposites must be true opposites, not just different.", "Go with the most common meaning."),
                listOf("Use prefixes: un-, dis-, in- often make antonyms.", "Roots help: 'bene' is good, 'mal' is bad.", "Consider shades of meaning and strength."),
                listOf("Words can have several meanings; choose the one the options fit.", "Watch for traps that sound similar but differ in meaning.", "Context sentences settle close calls."),
                listOf("Less common words: break them into roots and affixes.", "Formal vs casual synonyms still count as synonyms.", "Eliminate wrong answers to find the right one."),
                listOf("Precision: pick the closest meaning, not a loose relative.", "Build vocabulary by noticing words you missed.", "Keep a steady pace."),
            ),
        ),
        GameKind.RHYME_TIME to Course(
            listOf(
                "A true rhyme matches the ending sound from the last stressed vowel: 'cat' and 'hat'.",
                "Each round, find the word that truly rhymes with the target.",
                "Look-alikes don't count: 'cough' doesn't rhyme with 'though'.",
            ),
            listOf(
                listOf("Say the target and each choice out loud.", "Listen to the vowel and everything after it.", "Spelling can fool you; sound can't."),
                listOf("Eye rhymes look alike but sound different: 'love' / 'move'.", "Different spellings can rhyme: 'blue' / 'shoe'.", "Ignore the first letter."),
                listOf("Multi-syllable words rhyme from the stressed syllable: 'nation' / 'station'.", "Near rhymes (slant rhymes) aren't true rhymes.", "Check the stress pattern."),
                listOf("Watch for homophones and tricky vowels.", "Count syllables after the stress; they must match.", "Trust your ear when spelling disagrees."),
                listOf("Accents vary; the app uses standard pronunciations.", "Unusual words: break them into syllables.", "Rule out choices that only share a letter pattern."),
                listOf("Quick and accurate: say it, compare, decide.", "Learn common traps: -ough, -ear, -ove.", "Stay consistent and relaxed."),
            ),
        ),
        GameKind.WORD_LADDER to Course(
            listOf(
                "Turn the start word into the goal word one letter at a time.",
                "Every step must be a real word. Tap a letter, then the new letter.",
                "Fewer steps score more.",
            ),
            listOf(
                listOf("Compare start and goal: which letters already match?", "Change a letter toward the goal whenever the result is a real word.", "If stuck, change a different letter first."),
                listOf("Vowels are flexible: swapping A, E, I, O, U often finds a word.", "Keep matching letters fixed.", "Work backwards from the goal too."),
                listOf("Sometimes you must step away from the goal to get around a dead end.", "Common words make the best stepping stones.", "Plan two steps ahead."),
                listOf("Find a bridge word that shares letters with both ends.", "Try every vowel in a slot before giving up on it.", "Count the minimum changes: each differing letter needs at least one."),
                listOf("Map out likely middle words before playing.", "Watch for letter positions that rarely change easily (Q, X).", "Efficient ladders often change consonants early."),
                listOf("Shortest path: aim for exactly as many steps as differing letters.", "When blocked, detour by one step and come back.", "Experience builds a mental dictionary of bridges."),
            ),
        ),
        GameKind.WORD_WHEEL to Course(
            listOf(
                "Make words of 4 or more letters from the seven letters on the wheel.",
                "Every word must use the centre letter. Letters can be reused.",
                "Find as many as you can; longer words score more.",
            ),
            listOf(
                listOf("Start with simple 4-letter words using the centre.", "Add -S, -ED or -ING when the letters allow.", "Try the centre letter in different positions."),
                listOf("Look for common pairs: TH, ST, ER, IN.", "Build families: PLAY, PLAYS, PLAYED.", "Shuffle the letters in your head."),
                listOf("Hunt for the pangram: a word that uses all seven letters.", "Prefixes and suffixes: RE-, UN-, -ER, -LY.", "Swap vowels in found words for new ones."),
                listOf("Reuse letters: LOOP, NOON, LEVEL.", "Search systematically by first letter.", "Compound ideas: two short words joined."),
                listOf("Less common words count: think of hobbies, nature, food.", "The longest words carry the most points.", "Revisit near-misses with a different ending."),
                listOf("Cycle through every letter as a starting letter.", "Pangrams first, then fill in the rest.", "Keep going: there are always more words."),
            ),
        ),
    )
}
