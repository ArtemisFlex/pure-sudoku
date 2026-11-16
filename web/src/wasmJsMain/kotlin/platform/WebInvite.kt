package com.puresudoku.platform

import androidx.compose.runtime.Composable

@JsFun("(url) => { if (navigator.share) { navigator.share({title:'Join my PureGames game',url}).catch(() => {}); } else if (navigator.clipboard) { navigator.clipboard.writeText(url).catch(() => {}); } }")
private external fun shareInvite(url: String)

@Composable
fun rememberInviteShare(): (String) -> Unit = { shareInvite(it) }
