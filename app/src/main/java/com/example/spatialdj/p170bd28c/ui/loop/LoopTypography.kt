package com.example.spatialdj.p170bd28c.ui.loop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.spatialdj.p170bd28c.BuildConfig
import com.example.spatialdj.p170bd28c.R
import com.pico.spatial.ui.design.Text

internal val LoopFontFamily = FontFamily(
    Font(R.font.loop_sans_sc_regular, FontWeight.Normal),
    Font(R.font.loop_sans_sc_semibold, FontWeight.SemiBold),
)

/** One family and explicit line metrics for Chinese, Latin note names and numbers. */
@Composable
internal fun LoopText(
    id: String,
    text: String,
    input: PanelInput,
    modifier: Modifier = Modifier,
    color: Color = Color(ClubPalette.Ink),
    fontSize: TextUnit = 18.sp,
    strong: Boolean = false,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    if (BuildConfig.DEBUG) {
        DisposableEffect(id, input) { onDispose { input.forgetText(id) } }
    }
    Text(
        text = text,
        modifier = if (BuildConfig.DEBUG) modifier.onGloballyPositioned { input.textPosition(id, it) }
            else modifier,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        style = TextStyle(
            fontFamily = LoopFontFamily,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
            fontSynthesis = FontSynthesis.None,
            fontSize = fontSize,
            lineHeight = (fontSize.value + 6).sp,
            letterSpacing = 0.sp,
            textAlign = textAlign,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        ),
        onTextLayout = if (BuildConfig.DEBUG) ({ input.textLayout(id, it) }) else null,
    )
}
