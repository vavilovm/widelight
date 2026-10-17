package com.widelight
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
@Composable
fun Photo.localizedTitle(): String = when {
    title == "Photo" -> stringResource(R.string.photo)
    title == "Designed print" -> stringResource(R.string.designed_print)
    title.startsWith("Collage · ") -> title.substringAfter("Collage · ").substringBefore(" ").toIntOrNull()?.let { pluralStringResource(R.plurals.collage_title, it, it) } ?: title
    else -> title
}
interface LocalizedIssue { val textRes: Int; val arguments: Array<out Any> }
class UserStateFailure(override val textRes: Int, vararg args: Any) : IllegalStateException(), LocalizedIssue { override val arguments = args }
class UserInputFailure(override val textRes: Int) : IllegalArgumentException(), LocalizedIssue { override val arguments = emptyArray<Any>() }
fun ensureState(condition: Boolean, textRes: Int) { if (!condition) throw UserStateFailure(textRes) }
fun ensureInput(condition: Boolean, textRes: Int) { if (!condition) throw UserInputFailure(textRes) }
fun android.content.Context.failureText(error: Throwable, fallback: Int): String = if (error is LocalizedIssue) getString(error.textRes, *error.arguments) else error.message ?: getString(fallback)
