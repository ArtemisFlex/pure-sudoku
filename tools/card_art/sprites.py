from canvas import Canvas
import math

SPRITES = {}

def sprite(sid, name, tags, notes, size=24):
    def deco(fn):
        SPRITES[sid] = (name, tags, notes, size, fn)
        return fn
    return deco

def lit(cx, cy):
    """distance helper for shading: far from the top-left light"""
    return lambda x, y: (x - cx) + (y - cy)

# ------------------------------------------------------------------ monsters

@sprite("slime", "Slime", ["monster", "card-art"], "Small monster (low values).")
def slime(c):
    c.ellipse(11.5, 16.5, 9.6, 6.6, "G")
    c.ellipse(11.5, 11, 4.2, 4.6, "G")
    c.stage()
    c.shade(lambda x, y, k: k == "G" and (x - 6) * 0.7 + (y - 10) > 12, "g")
    c.ellipse(7.5, 12.5, 2.2, 1.6, "L", only="G")
    c.ellipse(10.5, 8.5, 1.2, 1.4, "L", only="G")
    c.stage()
    c.rows(7, 15, ["xx    xx", "xT    xT", "xx    xx"])
    c.rows(9, 19, ["g    g", " gggg"])
    c.stage()
    c.outline()
    c.stage()

@sprite("goblin", "Goblin", ["monster", "card-art"], "Mid monster.")
def goblin(c):
    # ears + head
    c.poly([(2, 7), (8, 9), (8, 13)], "G")
    c.poly([(22, 7), (16, 9), (16, 13)], "G")
    c.ellipse(11.5, 10.5, 5.6, 5.2, "G")
    # body
    c.poly([(6, 23), (8, 15), (16, 15), (18, 23)], "n")
    c.rect(9, 15, 6, 2, "G")
    c.stage()
    c.shade(lambda x, y, k: k == "G" and (x - 6) + (y - 6) * 1.2 > 13, "g")
    c.shade(lambda x, y, k: k == "n" and x > 13, "w")
    c.rect(6, 19, 12, 1, "y")
    c.stage()
    c.rows(8, 9, ["YY  YY", "Yx  xY"])
    c.rows(9, 13, ["x xx x", " TTTT"])
    c.rows(7, 7, ["L"]); c.rows(6, 8, ["L"]); c.rows(3, 8, ["LL"])
    c.stage()
    # dagger in hand
    c.rows(18, 13, ["  T", " TS", "TS ", "y  "])
    c.rows(17, 17, ["y"])
    c.stage()
    c.outline()
    c.stage()

@sprite("skeleton", "Skeleton", ["monster", "card-art"], "Strong monster.")
def skeleton(c):
    c.ellipse(11.5, 7.5, 5.4, 5.2, "e")
    c.rect(8, 11, 8, 3, "e")
    c.rect(11, 14, 2, 9, "e")  # spine
    for i, y in enumerate([15, 17, 19]):
        w = 7 - i
        c.rect(12 - w, y, w * 2, 1, "e")
    c.rect(7, 22, 10, 1, "e")
    c.stage()
    c.shade(lambda x, y, k: k == "e" and (x - 7) + (y - 3) > 14, "E")
    c.stage()
    c.rows(8, 7, ["xxx  xxx", "xRx  xRx", "xxx  xxx"])
    c.rows(11, 10, ["xx"])
    c.rows(8, 12, ["x x x x"])
    c.stage()
    # arms
    c.line(5, 15, 3, 21, "e"); c.line(18, 15, 20, 21, "E")
    c.line(5, 15, 7, 15, "e"); c.line(16, 15, 18, 15, "E")
    c.stage()
    c.outline()
    c.stage()

@sprite("troll", "Cave Troll", ["monster", "card-art"], "Elite monster (Jack/Queen).")
def troll(c):
    c.ellipse(11.5, 17, 9.5, 7, "M")       # body
    c.ellipse(11.5, 8, 5, 4.6, "M")        # head
    c.poly([(5, 6), (7, 1), (9, 5)], "e")  # horns
    c.poly([(18, 6), (16, 1), (14, 5)], "e")
    c.stage()
    c.shade(lambda x, y, k: k == "M" and (x - 4) + (y - 6) > 20, "m")
    c.ellipse(7, 14, 2.5, 2, "Q", only="M")
    c.ellipse(9, 6, 1.5, 1, "Q", only="M")
    c.stage()
    c.rows(8, 8, ["R    R"])
    c.rows(9, 10, ["T TT T", "kkkkkk"])
    c.rect(7, 19, 10, 2, "n"); c.rect(7, 19, 10, 1, "N")
    c.stage()
    # club
    c.line(21, 22, 21, 10, "w")
    c.ellipse(21, 9, 2, 3.5, "n")
    c.px(20, 7, "N"); c.px(22, 11, "w")
    c.stage()
    c.outline()
    c.stage()

@sprite("dragon", "Dragon", ["boss", "card-art"], "Boss monster (King/Ace).", 32)
def dragon(c):
    # wings
    c.poly([(14, 12), (2, 2), (4, 10), (1, 14), (6, 16), (3, 21), (12, 20)], "r")
    c.poly([(18, 12), (30, 2), (28, 10), (31, 14), (26, 16), (29, 21), (20, 20)], "r")
    # body + neck + head
    c.ellipse(15.5, 22, 7.5, 6.5, "R")
    c.poly([(12, 18), (13, 9), (18, 9), (19, 18)], "R")
    c.ellipse(15.5, 8, 5, 3.6, "R")
    c.poly([(11, 7), (8, 1), (13, 5)], "e"); c.poly([(20, 7), (23, 1), (18, 5)], "e")
    # tail
    c.poly([(21, 27), (28, 29), (31, 26), (29, 30), (20, 30)], "R")
    c.stage()
    c.shade(lambda x, y, k: k == "R" and (x - 9) + (y - 5) > 26, "r")
    c.shade(lambda x, y, k: k == "r" and (x < 4 or x > 27 or y < 4), "d")
    c.ellipse(15.5, 24, 4, 4.5, "Y", only="Rr")
    c.shade(lambda x, y, k: k == "Y" and y % 2 == 0, "y")
    c.stage()
    c.rows(12, 7, ["FF     FF"[:4] + "  FF"])
    c.rows(13, 10, ["x    x"])
    c.ellipse(11, 12, 1.4, 0.8, "P", only="R")
    c.stage()
    # flame breath
    c.rows(14, 12, [" fF", "fFFf", " fF"])
    c.stage()
    c.outline()
    c.stage()

# ------------------------------------------------------------------ items

@sprite("dagger", "Dagger", ["weapon", "card-art"], "Weak weapon (2-4).")
def dagger(c):
    c.poly([(15, 6), (17, 7), (13, 12), (11, 11)], "S")
    c.stage()
    c.line(15, 7, 12, 11, "T")
    c.line(17, 8, 14, 12, "s")
    c.stage()
    c.line(9, 10, 14, 15, "y")
    c.line(10, 10, 14, 14, "Y")
    c.line(11, 14, 8, 17, "n"); c.line(12, 14, 9, 17, "w")
    c.rect(6, 17, 3, 2, "Y")
    c.stage()
    c.outline()
    c.stage()

@sprite("sword", "Sword", ["weapon", "card-art"], "Medium weapon (5-7).")
def sword(c):
    c.poly([(19, 1), (22, 2), (9, 16), (7, 14)], "S")
    c.stage()
    c.line(20, 2, 8, 14, "T")
    c.line(22, 3, 10, 16, "s")
    c.stage()
    c.line(4, 13, 11, 20, "y"); c.line(5, 13, 11, 19, "Y")
    c.line(7, 17, 3, 21, "n"); c.line(8, 17, 4, 21, "w")
    c.rect(1, 21, 3, 2, "Y")
    c.px(4, 13, "R"); c.px(11, 20, "R")
    c.stage()
    c.outline()
    c.stage()

@sprite("battleaxe", "Battle Axe", ["weapon", "card-art"], "Strong weapon (8-10).")
def battleaxe(c):
    c.line(4, 22, 16, 4, "n"); c.line(5, 22, 17, 4, "w")
    c.stage()
    c.poly([(14, 2), (22, 3), (23, 11), (19, 13), (16, 9)], "S")
    c.poly([(13, 7), (9, 6), (10, 10)], "S")
    c.stage()
    c.shade(lambda x, y, k: k == "S" and x + y > 27, "s")
    c.line(15, 3, 21, 4, "T"); c.line(21, 4, 22, 9, "T")
    c.px(16, 6, "R")
    c.stage()
    c.outline()
    c.stage()

@sprite("potion", "Healing Potion", ["potion", "card-art"], "Hearts in Scoundrel.")
def potion(c):
    c.ellipse(11.5, 15.5, 7, 6.5, "R")
    c.rect(9, 5, 6, 5, "C")
    c.rect(8, 3, 8, 3, "n")
    c.stage()
    c.ellipse(11.5, 15.5, 7, 6.5, "C", only=".")  # glass rim
    c.ellipse(11.5, 16.5, 6, 5, "R")
    c.shade(lambda x, y, k: k == "R" and (x - 6) + (y - 11) > 13, "r")
    c.rect(8, 3, 8, 1, "N")
    c.stage()
    c.rows(7, 12, ["TT", "T", "", "", "  P"])
    c.ellipse(11.5, 12.5, 6.2, 0.6, "P", only="R")
    c.stage()
    c.outline()
    c.stage()

@sprite("chest", "Treasure Chest", ["treasure", "card-art"], "Tomb hoards / treasure.")
def chest(c):
    c.rect(3, 12, 18, 9, "n")
    c.ellipse(11.5, 12, 9, 5, "n")
    c.rect(3, 12, 18, 1, "w")
    c.stage()
    c.shade(lambda x, y, k: k == "n" and y < 12 and (x + y) < 15, "N")
    c.shade(lambda x, y, k: k == "n" and y > 17, "w")
    c.rect(3, 12, 18, 2, "Y"); c.rect(3, 13, 18, 1, "y")
    c.rect(5, 7, 2, 14, "Y"); c.rect(17, 7, 2, 14, "y")
    c.stage()
    c.rect(10, 12, 4, 4, "Y"); c.rect(11, 14, 2, 1, "x")
    c.rows(9, 5, ["W", " ", "   W"])
    c.stage()
    c.outline()
    c.stage()
    c.rows(1, 3, ["W"]); c.rows(21, 6, ["W"]); c.rows(20, 2, [" W", "WWW", " W"])
    c.stage()

@sprite("torch", "Torch", ["light", "card-art"], "Aces in Dungeon Solitaire.")
def torch(c):
    c.rect(11, 12, 3, 11, "n"); c.rect(13, 12, 1, 11, "w")
    c.rect(10, 11, 5, 2, "s")
    c.stage()
    c.ellipse(12, 6.5, 4, 5.5, "d")
    c.ellipse(12, 7.5, 3, 4, "f")
    c.ellipse(12, 8.5, 1.6, 2.6, "F")
    c.px(12, 0, "d"); c.px(9, 3, "f")
    c.stage()
    c.outline()
    c.stage()

@sprite("scroll", "Scroll of Light", ["magic", "card-art"], "The Joker in Dungeon Solitaire.")
def scroll(c):
    c.rect(5, 6, 14, 12, "e")
    c.rect(3, 4, 18, 3, "N"); c.rect(3, 17, 18, 3, "N")
    c.stage()
    c.rect(3, 6, 18, 1, "n"); c.rect(3, 19, 18, 1, "n")
    c.rect(17, 7, 2, 10, "E")
    c.stage()
    for y in (9, 11, 13, 15): c.rect(7, y, 8 - (y % 3), 1, "E")
    c.rows(14, 9, [" Y", "YWY", " Y"])
    c.stage()
    c.outline()
    c.stage()

@sprite("door", "Dungeon Door", ["door", "card-art"], "Clubs encounters in Dungeon Solitaire.")
def door(c):
    c.rect(2, 4, 20, 19, "M")
    c.ellipse(11.5, 6, 10, 5, "M")
    c.stage()
    c.rect(5, 7, 14, 16, "n")
    c.ellipse(11.5, 8, 7, 4, "n")
    c.stage()
    for x in (8, 12, 16): c.rect(x, 5, 1, 18, "w")
    c.rect(5, 10, 14, 1, "s"); c.rect(5, 18, 14, 1, "s")
    c.shade(lambda x, y, k: k == "M" and (x + y) % 5 == 0, "Q")
    c.shade(lambda x, y, k: k == "M" and (x * 3 + y) % 7 == 0, "m")
    c.rows(14, 14, ["YY", "yy"])
    c.stage()
    c.outline()
    c.stage()

@sprite("trap", "Spike Trap", ["trap", "card-art"], "Diamond encounters in Dungeon Solitaire.")
def trap(c):
    c.rect(1, 18, 22, 5, "M")
    c.stage()
    for i, x in enumerate(range(2, 22, 4)):
        top = 6 if i % 2 == 0 else 9
        c.poly([(x, 18), (x + 2, top), (x + 4, 18)], "S")
    c.stage()
    c.shade(lambda x, y, k: k == "S" and (x % 4) >= 2, "s")
    c.shade(lambda x, y, k: k == "M" and y >= 21, "m")
    c.px(4, 8, "T"); c.px(12, 8, "T"); c.px(20, 8, "T")
    c.rows(8, 13, ["R", "r"]); c.rows(16, 15, ["R"])
    c.stage()
    c.outline()
    c.stage()

@sprite("key", "Skeleton Key", ["skill", "card-art"], "Pick Lock skill.")
def key(c):
    c.ellipse(7, 7, 4.6, 4.6, "Y")
    c.ellipse(7, 7, 2, 2, ".")
    c.line(10, 10, 19, 19, "Y"); c.line(11, 10, 20, 19, "Y")
    c.rows(15, 18, ["Y", "YY", " YY"]); c.rows(18, 15, ["Y", "YY"])
    c.stage()
    c.shade(lambda x, y, k: k == "Y" and (x + y) > 16 and (x - y) > -1, "y")
    c.rows(4, 4, ["W", "W"])
    c.stage()
    c.outline()
    c.stage()

@sprite("boot", "Swift Boot", ["skill", "card-art"], "Dodge Blow skill.")
def boot(c):
    c.poly([(8, 3), (15, 3), (15, 15), (21, 17), (21, 21), (6, 21), (7, 12)], "n")
    c.stage()
    c.shade(lambda x, y, k: k == "n" and x > 13 and y < 15, "w")
    c.rect(6, 20, 16, 2, "w")
    c.rect(7, 3, 9, 2, "N")
    c.rows(2, 8, ["C C", " C", "C C"])
    c.rows(1, 12, [" C", "CC"])
    c.stage()
    c.outline()
    c.stage()

@sprite("blessing", "Divine Favor", ["skill", "card-art"], "Queens in Dungeon Solitaire.")
def blessing(c):
    c.poly([(12, 0), (14, 9), (23, 12), (14, 15), (12, 23), (10, 15), (1, 12), (10, 9)], "Y")
    c.stage()
    c.ellipse(11.5, 11.5, 3.5, 3.5, "W")
    c.shade(lambda x, y, k: k == "Y" and (x + y) > 24, "y")
    c.stage()
    c.rows(3, 3, ["W"]); c.rows(19, 4, ["W", "WW"]); c.rows(4, 19, ["W"]); c.rows(19, 19, ["W"])
    c.stage()

@sprite("coins", "Gold Coins", ["treasure", "card-art"], "Diamond treasure.")
def coins(c):
    for i, (x, y) in enumerate([(4, 17), (10, 18), (16, 17), (7, 13), (13, 14), (10, 9)]):
        c.ellipse(x + 2, y + 1.5, 3.4, 2, "Y")
        c.ellipse(x + 2, y + 2.5, 3.4, 1.4, "y", only=".Y" if False else "Y")
        c.ellipse(x + 2, y + 1, 3.2, 1.6, "Y")
        c.px(x + 1, y + 1, "W")
        c.stage()
    c.rows(17, 5, [" C", "CTC", " C"])
    c.outline()
    c.stage()

# ------------------------------------------------------------------ royals (Regicide)

@sprite("dark-knight", "Corrupted Knight", ["boss", "royal", "card-art"], "Regicide Jacks.", 32)
def dark_knight(c):
    c.poly([(5, 31), (8, 19), (24, 19), (27, 31)], "s")       # shoulders
    c.ellipse(16, 12, 8, 9, "s")                              # helm
    c.poly([(14, 3), (16, 0), (18, 3)], "V")                  # crest
    c.stage()
    c.shade(lambda x, y, k: k == "s" and (x - 8) + (y - 4) > 20, "k")
    c.ellipse(13, 8, 2.5, 3, "S", only="s")
    c.rect(9, 11, 14, 3, "x")                                 # visor slit
    c.stage()
    c.rows(11, 12, ["vV    Vv"])
    c.rect(16, 16, 1, 5, "k")
    for x in (10, 13, 19, 22): c.rect(x, 16, 1, 2, "x")
    c.stage()
    c.poly([(1, 31), (6, 20), (8, 31)], "p"); c.poly([(31, 31), (26, 20), (24, 31)], "p")  # cape
    c.poly([(13, 21), (19, 21), (16, 28)], "V")                # tabard sigil
    c.stage()
    c.outline()
    c.stage()

@sprite("dark-queen", "Shadow Queen", ["boss", "royal", "card-art"], "Regicide Queens.", 32)
def dark_queen(c):
    c.ellipse(16, 13, 9, 10, "p")             # hair
    c.poly([(4, 31), (9, 21), (23, 21), (28, 31)], "V")  # gown
    c.stage()
    c.ellipse(16, 13, 5.5, 6.5, "h")          # face
    c.shade(lambda x, y, k: k == "h" and x > 18, "H")
    c.shade(lambda x, y, k: k == "V" and x > 18, "p")
    c.stage()
    c.rows(10, 1, ["Y  Y  Y  Y  Y"[:12], "YY YYY YYY Y"[:12], "YYYYYYYYYYYY"])
    c.rows(15, 1, ["R"])
    c.stage()
    c.rows(12, 12, ["xv  vx"[:3] + " " + "vx"])
    c.rows(13, 13, ["p  p"])
    c.rows(14, 17, ["RR"])
    c.rows(11, 22, ["v Y v", " vYv"])
    c.stage()
    c.outline()
    c.stage()

@sprite("lich-king", "Lich King", ["boss", "royal", "card-art"], "Regicide Kings.", 32)
def lich_king(c):
    c.poly([(3, 31), (8, 18), (24, 18), (29, 31)], "p")       # robe
    c.ellipse(16, 13, 7, 7.5, "e")                            # skull
    c.rect(12, 18, 8, 4, "e")
    c.stage()
    c.shade(lambda x, y, k: k == "e" and (x - 10) + (y - 7) > 13, "E")
    c.shade(lambda x, y, k: k == "p" and x > 17, "x")
    c.stage()
    c.rows(9, 0, ["Y  Y  Y  Y"[:10] + "  ", "YY YY YY YY"[:11], "YYYYYYYYYYYYYY"[:14], "yyyyyyyyyyyyyy"])
    c.rows(15, 2, ["R"])
    c.stage()
    c.rows(11, 12, ["xxx   xxx"[:9] + "x", "xGx   xGx"[:9] + "x", "xxx   xxx"[:9] + "x"])
    c.rows(15, 16, ["xx"])
    c.rows(12, 19, ["x x x x"])
    c.ellipse(16, 25, 3, 3, "G", only="px")
    c.px(15, 24, "L")
    c.stage()
    c.outline()
    c.stage()

# ------------------------------------------------------------------ jester (Joker Jailbreak)

@sprite("jester", "The Joker", ["joker", "card-art"], "Joker Jailbreak's prisoner.")
def jester(c):
    c.poly([(3, 3), (11, 9), (6, 11)], "R"); c.poly([(21, 3), (13, 9), (18, 11)], "V")
    c.poly([(12, 0), (14, 8), (10, 8)], "Y")
    c.ellipse(11.5, 12.5, 5.5, 5, "h")
    c.poly([(5, 23), (8, 17), (16, 17), (19, 23)], "R")
    c.stage()
    c.rect(12, 17, 7, 6, "V")
    c.rect(6, 9, 12, 2, "Y")
    c.shade(lambda x, y, k: k == "h" and x > 14, "H")
    c.stage()
    c.rows(9, 11, ["x  x"]); c.rows(8, 14, ["R    R", " RRRR"])
    c.rows(2, 2, ["Y"]); c.rows(21, 2, ["Y"]); c.rows(11, 0, [" "])
    c.rows(9, 18, ["Y Y Y"[:5]])
    c.stage()
    c.outline()
    c.stage()
