// Platform layer (browser): Android's resource calls, answered from Compose Multiplatform resources.
// The app refers to art as R.drawable.x ids; R.kt (generated at build time) numbers every image, and
// these functions look each number up in the generated table.
package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import com.puresudoku.web.drawableFor

@Composable
fun painterResource(id: Int): Painter = org.jetbrains.compose.resources.painterResource(drawableFor(id))

@Composable
fun ImageBitmap.Companion.imageResource(id: Int): ImageBitmap = org.jetbrains.compose.resources.imageResource(drawableFor(id))
