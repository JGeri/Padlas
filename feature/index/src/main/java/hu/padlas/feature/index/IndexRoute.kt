package hu.padlas.feature.index

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.padlas.core.design.motion.Motion
import kotlinx.coroutines.delay

/**
 * The first run. Nothing here blocks: the worker keeps writing batches and the numbers
 * keep climbing, so the screen is a readout of work already done rather than a wait.
 */
@Composable
fun IndexRoute(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: IndexViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) {
            // Long enough to read the final count, short enough that it is not a wait.
            delay(Motion.MaxBlockingMs.toLong())
            onFinished()
        }
    }

    IndexScreen(state = state, onRetry = viewModel::retry, modifier = modifier)
}
