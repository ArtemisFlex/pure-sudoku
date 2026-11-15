// Platform layer (browser): small stand-ins for Android-only APIs the shared screens call.
package androidx.activity.compose

import androidx.compose.runtime.Composable

/** Android's system Back. The browser has no such button inside the page; screens still close via their own controls. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {}
