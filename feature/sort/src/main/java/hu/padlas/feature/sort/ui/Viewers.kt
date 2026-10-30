package hu.padlas.feature.sort.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import hu.padlas.core.design.component.PhotoViewer

/** The photo itself, full screen, through the design system's viewer. */
@Composable
internal fun ZoomViewer(uri: Uri, onDismiss: () -> Unit) {
    PhotoViewer(onDismiss = onDismiss) { modifier ->
        AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
    }
}
