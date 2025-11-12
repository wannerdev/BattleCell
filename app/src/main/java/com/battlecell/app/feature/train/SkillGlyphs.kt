package com.battlecell.app.feature.train

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.battlecell.app.domain.model.AttributeType

private object SkillGlyphPalette {
    val powerPrimary = Color(0xFFF59E0B)
    val powerSecondary = Color(0xFFB45309)
    val agilityPrimary = Color(0xFF34D399)
    val agilitySecondary = Color(0xFF0EA5E9)
    val endurancePrimary = Color(0xFF4C6EF5)
    val enduranceSecondary = Color(0xFF364FC7)
    val focusPrimary = Color(0xFF9F7AEA)
    val focusSecondary = Color(0xFF5B21B6)
}

@Composable
internal fun SkillGlyphIcon(
    attributeType: AttributeType,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawSkillGlyph(attributeType = attributeType, center = center, radius = radius)
    }
}

internal fun DrawScope.drawSkillGlyph(
    attributeType: AttributeType,
    center: Offset,
    radius: Float
) {
    when (attributeType) {
        AttributeType.POWER -> drawPowerGlyph(center, radius)
        AttributeType.AGILITY -> drawAgilityGlyph(center, radius)
        AttributeType.ENDURANCE -> drawEnduranceGlyph(center, radius)
        AttributeType.FOCUS -> drawFocusGlyph(center, radius)
    }
}

private fun DrawScope.drawPowerGlyph(center: Offset, radius: Float) {
    val palmWidth = radius * 1.4f
    val palmHeight = radius * 1.05f
    val palmTop = center.y - palmHeight / 2f + radius * 0.15f
    val knuckleHeight = radius * 0.42f
    val knuckleWidth = palmWidth * 0.18f
    val spacing = knuckleWidth * 0.12f

    drawRoundRect(
        color = SkillGlyphPalette.powerSecondary,
        topLeft = Offset(center.x - palmWidth / 2f, palmTop),
        size = androidx.compose.ui.geometry.Size(palmWidth, palmHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius * 0.18f, radius * 0.18f)
    )

    repeat(4) { index ->
        val left = center.x - palmWidth / 2f + spacing + index * (knuckleWidth + spacing)
        drawRoundRect(
            color = SkillGlyphPalette.powerPrimary,
            topLeft = Offset(left, palmTop - knuckleHeight / 2f),
            size = androidx.compose.ui.geometry.Size(knuckleWidth, knuckleHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius * 0.12f, radius * 0.12f)
        )
    }

    val thumbPath = Path().apply {
        moveTo(center.x + palmWidth / 2f - radius * 0.22f, palmTop + palmHeight * 0.2f)
        cubicTo(
            center.x + palmWidth / 2f + radius * 0.05f,
            palmTop + palmHeight * 0.08f,
            center.x + palmWidth / 2f + radius * 0.12f,
            palmTop + palmHeight * 0.46f,
            center.x + palmWidth / 2f - radius * 0.02f,
            palmTop + palmHeight * 0.62f
        )
        lineTo(center.x + palmWidth / 2f - radius * 0.25f, palmTop + palmHeight * 0.52f)
        close()
    }
    drawPath(thumbPath, color = SkillGlyphPalette.powerPrimary)

    drawCircle(
        color = Color.White.copy(alpha = 0.22f),
        radius = radius * 0.45f,
        center = Offset(center.x - palmWidth * 0.1f, palmTop + palmHeight * 0.55f)
    )
}

private fun DrawScope.drawAgilityGlyph(center: Offset, radius: Float) {
    val wingSpan = radius * 1.9f
    val wingHeight = radius * 0.9f
    val bodyHeight = radius * 1.2f

    fun wing(isLeft: Boolean): Path = Path().apply {
        val direction = if (isLeft) -1 else 1
        moveTo(center.x + direction * wingSpan / 2f, center.y)
        quadraticBezierTo(
            center.x + direction * wingSpan / 3f,
            center.y - wingHeight,
            center.x,
            center.y - wingHeight * 0.25f
        )
        quadraticBezierTo(
            center.x + direction * wingSpan / 3f,
            center.y + wingHeight * 0.25f,
            center.x + direction * wingSpan / 2f,
            center.y + wingHeight * 0.4f
        )
        close()
    }

    val body = Path().apply {
        moveTo(center.x, center.y - bodyHeight / 2f)
        quadraticBezierTo(center.x - radius * 0.32f, center.y, center.x, center.y + bodyHeight / 2f)
        quadraticBezierTo(center.x + radius * 0.32f, center.y, center.x, center.y - bodyHeight / 2f)
        close()
    }

    drawPath(
        wing(true),
        brush = Brush.verticalGradient(
            colors = listOf(SkillGlyphPalette.agilitySecondary, SkillGlyphPalette.agilityPrimary)
        )
    )
    drawPath(
        wing(false),
        brush = Brush.verticalGradient(
            colors = listOf(SkillGlyphPalette.agilitySecondary, SkillGlyphPalette.agilityPrimary)
        )
    )
    drawPath(
        body,
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.85f), SkillGlyphPalette.agilityPrimary)
        )
    )

    val tail = Path().apply {
        moveTo(center.x, center.y + bodyHeight / 2f)
        lineTo(center.x - radius * 0.25f, center.y + bodyHeight * 0.9f)
        lineTo(center.x + radius * 0.25f, center.y + bodyHeight * 0.9f)
        close()
    }
    drawPath(tail, color = SkillGlyphPalette.agilitySecondary)

    drawCircle(
        color = SkillGlyphPalette.agilitySecondary,
        radius = radius * 0.12f,
        center = Offset(center.x + radius * 0.25f, center.y - bodyHeight * 0.2f)
    )
    drawCircle(
        color = Color.White,
        radius = radius * 0.05f,
        center = Offset(center.x + radius * 0.29f, center.y - bodyHeight * 0.23f)
    )
}

private fun DrawScope.drawEnduranceGlyph(center: Offset, radius: Float) {
    val shieldWidth = radius * 1.6f
    val shieldHeight = radius * 2.0f

    fun shieldPath(width: Float, height: Float): Path = Path().apply {
        moveTo(center.x, center.y - height / 2f)
        quadraticBezierTo(center.x + width / 2f, center.y - height / 4f, center.x + width / 3.4f, center.y + height / 3f)
        lineTo(center.x, center.y + height / 2f)
        lineTo(center.x - width / 3.4f, center.y + height / 3f)
        quadraticBezierTo(center.x - width / 2f, center.y - height / 4f, center.x, center.y - height / 2f)
        close()
    }

    drawPath(
        shieldPath(shieldWidth, shieldHeight),
        brush = Brush.linearGradient(
            colors = listOf(SkillGlyphPalette.enduranceSecondary, SkillGlyphPalette.endurancePrimary),
            start = Offset(center.x, center.y - shieldHeight / 2f),
            end = Offset(center.x, center.y + shieldHeight / 2f)
        )
    )

    drawPath(
        shieldPath(shieldWidth * 0.72f, shieldHeight * 0.72f),
        color = Color.White.copy(alpha = 0.18f)
    )

    val crest = Path().apply {
        moveTo(center.x, center.y - radius * 0.35f)
        lineTo(center.x + radius * 0.3f, center.y)
        lineTo(center.x, center.y + radius * 0.45f)
        lineTo(center.x - radius * 0.3f, center.y)
        close()
    }
    drawPath(crest, color = Color(0xFFEDF2FF))
}

private fun DrawScope.drawFocusGlyph(center: Offset, radius: Float) {
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(SkillGlyphPalette.focusPrimary, SkillGlyphPalette.focusSecondary),
            center = center,
            radius = radius * 1.6f
        ),
        topLeft = Offset(center.x - radius * 1.6f, center.y - radius),
        size = androidx.compose.ui.geometry.Size(radius * 3.2f, radius * 2f)
    )

    val irisRadius = radius * 0.56f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF211A47), SkillGlyphPalette.focusSecondary),
            center = center,
            radius = irisRadius * 1.5f
        ),
        radius = irisRadius,
        center = center
    )

    drawCircle(
        color = Color.White.copy(alpha = 0.72f),
        radius = irisRadius * 0.28f,
        center = Offset(center.x + irisRadius * 0.35f, center.y - irisRadius * 0.25f)
    )

    val sigil = Path().apply {
        moveTo(center.x, center.y - irisRadius * 0.75f)
        lineTo(center.x + irisRadius * 0.22f, center.y)
        lineTo(center.x, center.y + irisRadius * 0.75f)
        lineTo(center.x - irisRadius * 0.22f, center.y)
        close()
    }
    drawPath(sigil, color = Color.White.copy(alpha = 0.28f))
}
