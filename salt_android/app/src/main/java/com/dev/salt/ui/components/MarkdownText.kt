package com.dev.salt.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

/**
 * Renders [markdown] text with a Compose-native Markdown renderer
 * (mikepenz/multiplatform-markdown-renderer), so question/info/option text can use
 * bold, italics, headings, and lists.
 *
 * Plain text (no markdown syntax) renders just like a normal [androidx.compose.material3.Text].
 *
 * Usage notes:
 * - Question / info text: pass [baseStyle] = `MaterialTheme.typography.headlineSmall` to keep
 *   the existing large heading look; markdown headings scale up from the Material typography.
 * - Option labels (inside a Button / Row): leave the defaults. [color] inherits the surrounding
 *   control's content color (so it still flips with the highlighted/selected button states), and
 *   [baseStyle] inherits the provided text style. Keep option formatting *inline* (bold/italic);
 *   block markdown (headings, lists) inside a button label looks wrong but degrades safely.
 *
 * @param baseStyle style for normal paragraph text; defaults to the inherited text style.
 * @param color body text color; defaults to the inherited [LocalContentColor].
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    baseStyle: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val resolvedColor = color.takeOrElse { LocalContentColor.current }
    Markdown(
        content = markdown,
        modifier = modifier,
        colors = markdownColor(text = resolvedColor),
        typography = markdownTypography(
            text = baseStyle,
            paragraph = baseStyle,
            ordered = baseStyle,
            bullet = baseStyle,
            list = baseStyle,
        ),
    )
}
