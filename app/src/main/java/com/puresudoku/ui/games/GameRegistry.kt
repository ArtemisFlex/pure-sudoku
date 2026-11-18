package com.puresudoku.ui.games

import com.puresudoku.core.GameKind

/** Every game in the app, in menu order. (The Kreat test site swaps this file for one with Kreat alone: see kreat-web.) */
object Games {
    val all: List<GameDef> by lazy {
        listOf(
            com.puresudoku.ui.games.sudoku.SudokuDef,
            com.puresudoku.ui.games.city.StreetSmartsDef,
        ) + com.puresudoku.ui.games.learn.LearnGames.all + listOf(
            com.puresudoku.ui.games.cagemath.CageMathDef,
            com.puresudoku.ui.games.kakuro.KakuroDef,
            com.puresudoku.ui.games.picture.PictureDef,
            com.puresudoku.ui.games.hashi.HashiDef,
            com.puresudoku.ui.games.recall.RecallDef,
            com.puresudoku.ui.games.code.CodeDef,
            com.puresudoku.ui.games.lumen.LumenDef,
            com.puresudoku.ui.games.sliding.SlidingDef,
            com.puresudoku.ui.games.crossword.CrosswordDef,
            com.puresudoku.ui.games.wordsearch.WordSearchDef,
            com.puresudoku.ui.games.haiku.HaikuDef,
            com.puresudoku.ui.games.quiz.AlliterationDef,
            com.puresudoku.ui.games.quiz.SynonymsDef,
            com.puresudoku.ui.games.quiz.RhymeDef,
            com.puresudoku.ui.games.wordplay.LadderDef,
            com.puresudoku.ui.games.wordplay.WheelDef,
            com.puresudoku.ui.games.cards.SolitaireDef,
            com.puresudoku.ui.games.cards.PyramidDef,
            com.puresudoku.ui.games.cards.BlackjackDef,
            com.puresudoku.ui.games.cards.GoFishDef,
            com.puresudoku.ui.games.cards.CrazyEightsDef,
            com.puresudoku.ui.games.cards.ScoundrelDef,
            com.puresudoku.ui.games.cards.CuttleDef,
            com.puresudoku.ui.games.cards.RegicideDef,
            com.puresudoku.ui.games.cards.DungeonDef,
            com.puresudoku.ui.games.cards.JailbreakDef,
            com.puresudoku.ui.games.board.ChessDef,
            com.puresudoku.ui.games.board.CheckersDef,
            com.puresudoku.ui.games.board.GoDef,
            com.puresudoku.ui.games.board.ShogiDef,
            com.puresudoku.ui.games.board.XiangqiDef,
            com.puresudoku.ui.games.board.CyvasseDef,
            com.puresudoku.ui.games.board.BackgammonDef,
            com.puresudoku.ui.games.board.MancalaDef,
            com.puresudoku.ui.games.board.MahjongDef,
            com.puresudoku.ui.games.board.GomokuDef,
            com.puresudoku.ui.games.board.MorrisDef,
            com.puresudoku.ui.games.board.TicTacToeDef,
            com.puresudoku.ui.games.board.SeaBattleDef,
            com.puresudoku.ui.games.board.CratebargeDef,
            com.puresudoku.ui.games.realm.RealmforgeDef,
            com.puresudoku.ui.games.realm.RealmDragonDef,
            com.puresudoku.ui.games.realm.RealmNightDef,
            com.puresudoku.ui.games.realm.RealmAshDef,
            com.puresudoku.ui.games.realm.WisteriaDef,
            com.puresudoku.ui.games.realm.AshfallDef,
            com.puresudoku.ui.games.realm.KreashunDef,
            com.puresudoku.ui.games.realm.VeilguardDef,
            com.puresudoku.ui.games.realm.DeedsDef,
            com.puresudoku.ui.games.realm.AegwaspellDef,
            com.puresudoku.ui.games.realm.AegConquestDef,
        )
    }

    fun of(kind: GameKind): GameDef = all.first { it.kind == kind }
}
