package com.widelight

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt

@Composable
fun PrintColorControls(library: Library) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.print_colors)) }
    if (expanded) {
        val color = library.printColor
        Text(stringResource(R.string.print_color_help), style = MaterialTheme.typography.bodySmall)
        ColorSlider(stringResource(R.string.skin_tint), color.skinTint, -30..30, !library.busy) { library.updatePrintColor(color.copy(skinTint = it)) }
        ColorSlider(stringResource(R.string.skin_depth), color.skinDepth, 0..30, !library.busy) { library.updatePrintColor(color.copy(skinDepth = it)) }
        TextButton(enabled = !library.busy, onClick = { library.updatePrintColor(PrintColor()) }) { Text(stringResource(R.string.color_defaults)) }
        TextButton(enabled = !library.busy, onClick = { library.updatePrintColor(PrintColor.NEUTRAL) }) { Text(stringResource(R.string.color_unadjusted)) }
    }
}

@Composable
private fun ColorSlider(label: String, value: Int, range: IntRange, enabled: Boolean, change: (Int) -> Unit) {
    Text("$label: $value", style = MaterialTheme.typography.bodyMedium)
    Slider(value.toFloat(), { change(it.roundToInt()) }, enabled = enabled,
        valueRange = range.first.toFloat()..range.last.toFloat(), steps = range.last-range.first-1)
}
