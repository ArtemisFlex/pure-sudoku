#!/usr/bin/env python3
"""
Builds the offline word data for the Words & Literature games.

Sources (all downloaded at build time, nothing is fetched by the app):
  - WordNet 3.1 (Princeton; WordNet license)        definitions, synonyms, antonyms, categories
  - CMU Pronouncing Dictionary (BSD)                syllables, first sounds, rhymes
  - wordfreq (Robyn Speer; data CC BY-SA 4.0)      word frequency, to keep words familiar
  - better_profanity word list (MIT)                to filter offensive words

Usage: pip install nltk wordfreq cmudict better_profanity
       (put WordNet in ~/nltk_data/corpora/wordnet)
       python3 tools/build_words.py app/src/main/assets/words
"""
import os
import re
import sys
from collections import defaultdict

import cmudict
from better_profanity import profanity
from nltk.corpus import wordnet as wn
from wordfreq import zipf_frequency

OUT = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/words"
os.makedirs(OUT, exist_ok=True)

profanity.load_censor_words()
BAD = {str(w).lower() for w in profanity.CENSOR_WORDSET}
BAD |= {"nazi", "slave", "rape", "kill", "murder", "suicide", "drug", "cocaine", "heroin", "porn", "sex", "sexy", "gay",
        "lesbian", "negro", "colored", "retard", "midget", "gypsy", "jap", "chink", "spic", "kike", "wop", "dago",
        "breast", "breasts", "penis", "vagina", "nude", "naked", "bra", "condom", "erotic", "orgasm", "hooker",
        "whore", "pimp", "booze", "drunk", "bomb", "gun", "rifle", "pistol", "corpse", "torture", "abortion",
        "lynch", "lynching", "lynched", "massacre", "massacred", "genocide", "vasectomy", "hysterectomy", "panzer",
        "holocaust", "terrorist", "terrorism", "rapist", "molest", "incest", "pedophile", "bondage", "fetish", "crotch",
        "groin", "thong", "lingerie", "bikini", "panties", "slaughter", "executioner", "gunman", "hitler", "swastika"}
BAD_GLOSS = re.compile(r"offensive|vulgar|obscene|derogatory|disparaging|slur|slang for|sexual|genital|"
                       r"intercourse|ethnic|racial|homosexual|prostitut|feces|urin|excrement|semen|menstru|"
                       r"penis|vagina|anus|buttocks|breast|nipple|erotic|pornograph|masturbat|testicle", re.I)

CMU = cmudict.dict()
VOWEL = re.compile(r"[AEIOU]")


def zipf(w):
    return zipf_frequency(w, "en")


def syllables(phones):
    return sum(1 for p in phones if p[-1].isdigit())


def rhyme_key(phones):
    # from the last stressed vowel to the end
    idx = None
    for i, p in enumerate(phones):
        if p[-1] in "12":
            idx = i
    if idx is None:
        for i, p in enumerate(phones):
            if p[-1].isdigit():
                idx = i
    if idx is None:
        return ""
    return "-".join(re.sub(r"\d", "", p) for p in phones[idx:])


def first_sound(phones):
    return re.sub(r"\d", "", phones[0])


def clean(w):
    return w.isalpha() and w.islower() and w.isascii() and any(v in w for v in "aeiouy")


def ok_word(w):
    return clean(w) and w not in BAD and not any(b in w for b in ("fuck", "shit", "cunt", "nigg", "fag", "whor", "slut"))


def offensive_word(w):
    for s in wn.synsets(w):
        if BAD_GLOSS.search(s.definition()):
            # only exclude if that's the word's main sense
            if s == wn.synsets(w)[0]:
                return True
    return False


# ---------------------------------------------------------------- lexicon
FUNCTION = set("""a an the and or but nor so yet for of in on at by to from with without into onto upon over under above below
between among through across along around about against before after during since until till within beneath beside
behind beyond near off out up down i me my mine we us our ours you your yours he him his she her hers it its they them
their theirs this that these those who whom whose which what where when why how all any both each every either neither
few many much more most other some such no not only own same than too very can could may might must shall should will
would is am are was were be been being have has had having do does did doing done here there then now also just even
still again ever never always often once twice as if because while although though unless whether whatever whenever
wherever whoever let lets oh ah yes okay""".split())


def common_word(w):
    # a lowercase dictionary headword (so not a name like "Texas"), or a function word
    if w in FUNCTION:
        return True
    if any(l.name() == w for s in wn.synsets(w) for l in s.lemmas()):
        return True
    # inflections of real words ("wakes", "falls", "brighter")
    for pos in ("n", "v", "a"):
        base = wn.morphy(w, pos)
        if base and base != w and any(l.name() == base for s in wn.synsets(base) for l in s.lemmas()):
            return True
    return False
lex = {}
for w, prons in CMU.items():
    if not ok_word(w) or not (2 <= len(w) <= 12):
        continue
    z = zipf(w)
    if z < 2.6:
        continue
    if not common_word(w):
        continue
    if offensive_word(w):
        continue
    ph = prons[0]
    pos = ""
    syn = wn.synsets(w)
    for tag, letter in (("n", "n"), ("v", "v"), ("a", "a"), ("s", "a"), ("r", "r")):
        if any(s.pos() == tag for s in syn) and letter not in pos:
            pos += letter
    lex[w] = (int(round(z * 10)), syllables(ph), first_sound(ph), rhyme_key(ph), pos or "-")

with open(f"{OUT}/lexicon.tsv", "w") as f:
    for w in sorted(lex):
        z, sy, fs, rk, pos = lex[w]
        f.write(f"{w}\t{z}\t{sy}\t{fs}\t{rk}\t{pos}\n")
print("lexicon", len(lex))

# ---------------------------------------------------------------- crossword clues
STOP = {"a", "an", "the", "of", "or", "and", "to", "in", "for", "by", "with", "as", "on", "that", "is", "be", "at", "from", "something", "someone"}


def gloss_ok(answer, gloss):
    g = gloss.lower()
    stem = answer[:4] if len(answer) > 4 else answer
    if re.search(r"\b" + re.escape(stem), g):
        return False
    if BAD_GLOSS.search(g):
        return False
    return 8 <= len(gloss) <= 72


clues = {}
for w, (z, sy, fs, rk, pos) in lex.items():
    if not (3 <= len(w) <= 11) or z < 30:
        continue
    # only senses where the answer itself is the dictionary headword (not "crashed" -> crash)
    syns = [s for s in wn.synsets(w) if any(l.name().lower() == w for l in s.lemmas())]
    if not syns or syns[0].pos() not in "nvas":
        continue
    # skip words whose main sense is a proper noun
    if any(l.name()[0].isupper() for l in syns[0].lemmas() if l.name().lower() == w):
        continue
    picked = []
    for s in syns[:4]:
        gloss = s.definition().split(";")[0].strip()
        if gloss_ok(w, gloss):
            gloss = gloss[0].upper() + gloss[1:]
            picked.append(gloss)
    if picked:
        clues[w] = picked[0]

with open(f"{OUT}/clues.tsv", "w") as f:
    for w in sorted(clues):
        f.write(f"{w}\t{clues[w]}\n")
print("clues", len(clues))

# ---------------------------------------------------------------- thesaurus
def related_forms(a, b):
    return a[:4] == b[:4] or a in b or b in a


CURATED_ANTONYMS = """hot cold, big small, happy sad, fast slow, old young, old new, light dark, light heavy, up down, in out,
open closed, early late, rich poor, strong weak, hard soft, hard easy, high low, long short, tall short, wide narrow,
thick thin, full empty, wet dry, clean dirty, loud quiet, loud soft, good bad, right wrong, right left, true false,
win lose, buy sell, give take, push pull, start finish, begin end, first last, near far, inside outside, above below,
before after, always never, often seldom, many few, more less, most least, all none, love hate, friend enemy,
brave cowardly, kind cruel, polite rude, calm angry, safe dangerous, sweet sour, sharp dull, smooth rough, deep shallow,
sunny cloudy, day night, summer winter, north south, east west, top bottom, front back, arrive depart, accept refuse,
add subtract, agree disagree, alive dead, asleep awake, absent present, ancient modern, beautiful ugly, bitter sweet,
bold timid, cheap expensive, clever stupid, common rare, complex simple, cool warm, crooked straight, dangerous safe,
difficult easy, entrance exit, even odd, excited bored, fail succeed, false true, fancy plain, fat thin, fierce gentle,
float sink, foolish wise, forget remember, fresh stale, frequent rare, generous selfish, giant tiny, guilty innocent,
healthy sick, help hinder, hero villain, honest dishonest, humble proud, include exclude, increase decrease,
inferior superior, junior senior, laugh cry, lazy busy, lend borrow, lose find, major minor, maximum minimum,
melt freeze, messy neat, minor major, mild severe, noisy silent, obey disobey, optimist pessimist, partial complete,
peace war, permanent temporary, plural singular, positive negative, private public, question answer, raise lower,
rapid gradual, real fake, reward punishment, rigid flexible, rise fall, rural urban, scatter gather, shrink grow,
sloppy careful, sober drunk, solid liquid, spend save, stand sit, sturdy fragile, success failure, teacher student,
tight loose, timid confident, tough tender, tragic comic, transparent opaque, unite divide, vacant occupied,
vertical horizontal, victory defeat, visible invisible, voluntary compulsory, wealth poverty, whisper shout,
wild tame, wrong right, sunrise sunset, attack defend, ally enemy, export import, freedom slavery, joy sorrow,
cheerful gloomy, ascend descend, exterior interior, maximum minimum, multiply divide, natural artificial,
seldom often, shallow deep, sour sweet, swift slow, vague clear, victory loss, wax wane, ebb flow, dawn dusk"""
curated = defaultdict(set)
for pair in CURATED_ANTONYMS.replace("\n", " ").split(","):
    pr = pair.split()
    if len(pr) == 2 and pr[0] in lex and pr[1] in lex:
        curated[pr[0]].add(pr[1]); curated[pr[1]].add(pr[0])


def related_forms(a, b):
    return a[:4] == b[:4] or a in b or b in a


def dedupe(words):
    out = []
    for x in sorted(words, key=lambda x: -lex[x][0]):
        if not any(related_forms(x, y) for y in out):
            out.append(x)
    return out


thes = {}
for w, (z, sy, fs, rk, pos) in lex.items():
    if z < 28 or len(w) < 3:
        continue
    senses = [s for s in wn.synsets(w) if any(l.name().lower() == w for l in s.lemmas())]
    syn_set, ant_set = set(), set(curated.get(w, ()))
    if senses:
        main = senses[0]
        for l in main.lemmas():
            n = l.name().lower()
            if n != w and n in lex and lex[n][0] >= 30 and not related_forms(n, w):
                syn_set.add(n)
        for sense in senses[:2]:
            for l in sense.lemmas():
                if l.name().lower() != w:
                    continue
                for a in l.antonyms():
                    ant_set.add(a.name().lower())
            # satellite adjectives: antonyms of the head adjective ("glad" ~ "happy" <-> "sad")
            if sense.pos() == "s":
                for head in sense.similar_tos():
                    for hl in head.lemmas():
                        for a in hl.antonyms():
                            ant_set.add(a.name().lower())
    ant_set = {a for a in ant_set if a in lex and lex[a][0] >= 28 and not related_forms(a, w) and a not in syn_set}
    syn_list, ant_list = dedupe(syn_set)[:5], dedupe(ant_set)[:4]
    if syn_list or ant_list:
        thes[w] = (syn_list, ant_list)

CURATED_SYNONYMS = """big large, small little, fast quick, happy glad, sad unhappy, begin start, end finish, smart clever,
angry mad, tired sleepy, shut close, look see, talk speak, yell shout, rich wealthy, easy simple, hard difficult,
quiet silent, loud noisy, pretty beautiful, ugly hideous, brave bold, scared afraid, gift present, house home,
road street, child kid, mother mom, father dad, kind nice, rude impolite, wet damp, cold chilly, hot warm,
choose pick, help aid, hurry rush, jump leap, laugh giggle, cry weep, keep hold, like enjoy, maybe perhaps,
under below, above over, near close, center middle, odd strange, sick ill, sure certain, total whole,
alike similar, gather collect, grab seize, error mistake, fix repair, idea notion, job task, mend repair,
journey trip, enormous huge, tiny miny, shiny bright, dull boring, famous wellknown, fake false, aware conscious,
brief short, calm peaceful, cheap inexpensive, correct right, dangerous risky, delicious tasty, dirty filthy,
fair just, false untrue, funny amusing, grin smile, honest truthful, ill sick, injure hurt, intelligent smart,
jolly merry, large huge, mistake error, neat tidy, polite courteous, quick rapid, rare scarce, rest relax,
rotten decayed, scream shriek, shy timid, sleepy drowsy, sly cunning, speak talk, stop halt, story tale,
strange odd, sturdy strong, terror fear, thin slim, trip journey, usual normal, vacant empty, vanish disappear,
wealthy rich, weary tired, wicked evil, wise smart, cautious careful, ancient old, purchase buy, answer reply,
precise exact, assist help, observe watch, crucial vital, fragile delicate, gleam shine, hazard danger,
imitate copy, keen eager, loyal faithful, mend fix, nimble agile, ponder think, quarrel argue, rapid fast,
serene calm, tremble shake, vivid bright, wander roam, yearn long, abundant plentiful, conceal hide, disclose reveal,
eminent famous, feeble weak, frank honest, grief sorrow, hostile unfriendly, meager scant, ominous threatening,
prudent wise, resolute determined, tranquil peaceful, ubiquitous everywhere, zeal enthusiasm"""
pairs = []
for pair in CURATED_SYNONYMS.replace("\n", " ").split(","):
    pr = pair.split()
    if len(pr) == 2 and pr[0] in lex and pr[1] in lex:
        pairs.append(("S", pr[0], pr[1]))
for pair in CURATED_ANTONYMS.replace("\n", " ").split(","):
    pr = pair.split()
    if len(pr) == 2 and pr[0] in lex and pr[1] in lex:
        pairs.append(("A", pr[0], pr[1]))
with open(f"{OUT}/pairs.tsv", "w") as f:
    for t, a, b in pairs:
        f.write(f"{t}\t{a}\t{b}\n")
print("curated pairs", len(pairs))

with open(f"{OUT}/thesaurus.tsv", "w") as f:
    for w in sorted(thes):
        s, a = thes[w]
        f.write(f"{w}\t{','.join(s)}\t{','.join(a)}\n")
print("thesaurus", len(thes), "with antonyms", sum(1 for v in thes.values() if v[1]))

# ---------------------------------------------------------------- word search themes
THEMES = {
    "Animals": ["animal.n.01"], "Birds": ["bird.n.01"], "Fish": ["fish.n.01"], "Fruit": ["edible_fruit.n.01"],
    "Vegetables": ["vegetable.n.01"], "Trees": ["tree.n.01"], "Flowers": ["flower.n.01"], "Tools": ["tool.n.01"],
    "Instruments": ["musical_instrument.n.01"], "Furniture": ["furniture.n.01"], "Clothing": ["clothing.n.01"],
    "Vehicles": ["vehicle.n.01"], "Weather": ["weather.n.01", "atmospheric_phenomenon.n.01"], "Colors": ["chromatic_color.n.01"],
    "Sports": ["sport.n.01"], "Gems": ["gem.n.02", "precious_stone.n.01"], "Insects": ["insect.n.01"],
    "Buildings": ["building.n.01"], "Food": ["dish.n.02", "baked_goods.n.01"],
    "Body": ["external_body_part.n.01", "organ.n.01"], "Kitchen": ["kitchen_utensil.n.01", "cooking_utensil.n.01"],
    "Dances": ["dance.n.01"], "Games": ["board_game.n.01", "card_game.n.01"],
}
themes = {}
for name, roots in THEMES.items():
    words = set()
    for r in roots:
        try:
            root = wn.synset(r)
        except Exception:
            continue
        inside = set(root.closure(lambda x: x.hyponyms())) | {root}
        for syn in inside:
            for l in syn.lemmas():
                n = l.name()
                if n in lex and 3 <= len(n) <= 10 and lex[n][0] >= 30:
                    main = [x for x in wn.synsets(n) if any(m.name() == n for m in x.lemmas())]
                    if main and main[0] in inside:
                        words.add(n)
    if len(words) >= 14:
        themes[name] = sorted(words)
with open(f"{OUT}/themes.tsv", "w") as f:
    for name in sorted(themes):
        f.write(f"{name}\t{','.join(themes[name])}\n")
print("themes", {k: len(v) for k, v in themes.items()})

# ---------------------------------------------------------------- haiku seasons (season words written for this app)
SEASONS = {
    "Spring": "blossom cherry petal sprout rain bud nest robin thaw meadow breeze lamb frog swallow tulip daisy green "
              "dawn mist puddle seed bloom sparrow kite clover dew willow brook bee orchard morning",
    "Summer": "sun heat cicada wave shore sand firefly thunder noon shade river swim peach melon dragonfly lily "
              "bright storm sweat cloud lake grass field honey cricket breeze warm golden",
    "Autumn": "harvest moon maple leaf crow scarecrow apple pumpkin wind smoke frost geese dusk acorn amber red "
              "rust chill fog orchard hay cider crisp twilight falling field gold",
    "Winter": "snow ice frost pine cold fire hearth owl moon silence breath stars night drift sleet bare "
              "winter candle quiet white frozen wolf chimney gray dark still pale",
}
FILLERS = ("the a an in on of my your our old new soft slow still deep far near over under through "
           "and but as all one each we you I it is was are falls fly sings sleeps drifts waits glows hums "
           "turns rests calls flows hides wakes long light gentle quiet alone again home away across beneath "
           "between morning evening silver small little open empty lonely")
with open(f"{OUT}/haiku.tsv", "w") as f:
    for season, words in SEASONS.items():
        ws = [w for w in dict.fromkeys(words.split()) if w in CMU]
        f.write(f"{season}\t{','.join(ws)}\n")
    fill = [w for w in dict.fromkeys(FILLERS.split()) if w.lower() in CMU]
    f.write(f"_filler\t{','.join(fill)}\n")
print("haiku ok")
