// Stand-in for the strategy games' rulebooks (see Stubs.kt): Kreat uses the ordinary manual.
package com.puresudoku.ui.games.realm

import androidx.compose.runtime.Composable
import com.puresudoku.core.GameKind

object Rulebooks { fun has(@Suppress("UNUSED_PARAMETER") kind: GameKind) = false }

@Composable
fun RulebookScreen(@Suppress("UNUSED_PARAMETER") kind: GameKind, @Suppress("UNUSED_PARAMETER") onClose: () -> Unit) {}
