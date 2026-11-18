// Stand-in for the online lobby (see StubsOnline.kt): never shown on the Kreat test arena.
package com.puresudoku.ui.screens

import androidx.compose.runtime.Composable
import com.puresudoku.core.GameKind
import com.puresudoku.ui.AppViewModel

@Composable
fun OnlineScreen(@Suppress("UNUSED_PARAMETER") vm: AppViewModel, @Suppress("UNUSED_PARAMETER") kind: GameKind) {}
