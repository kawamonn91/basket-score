package com.ttech.basketscore.ui.record

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ttech.basketscore.stats.Court
import kotlin.math.asin
import kotlin.math.sqrt

/** コートの図に出す印（画面の座標。0〜1） */
data class CourtMark(val x: Double, val y: Double, val color: Color, val made: Boolean, val pending: Boolean = false)

private val Floor = Color(0xFFF3E2C4)
private val ThreeZone = Color(0xFFE8CFA4)
private val Paint = Color(0xFFD9B98A)
private val Line = Color(0xFF5A4630)

/**
 * フルコートの図（F-401）。押した位置を 0〜1 で返す。3P の区域は色を変える。
 * [leftColor]・[rightColor] は、左・右のゴールを攻めるチームの色（エンドラインに帯で示す）
 */
@Composable
fun CourtView(
    marks: List<CourtMark>,
    leftColor: Color,
    rightColor: Color,
    onTap: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio((Court.LENGTH / Court.WIDTH).toFloat())
            .semantics { contentDescription = "コートの図（押すとシュートの位置）" }
            .pointerInput(Unit) {
                detectTapGestures { o -> onTap((o.x / size.width).toDouble().coerceIn(0.0, 1.0), (o.y / size.height).toDouble().coerceIn(0.0, 1.0)) }
            },
    ) {
        val m = size.width / Court.LENGTH.toFloat()
        drawRect(ThreeZone)
        // 2P の区域・制限区域・円
        listOf(false, true).forEach { right -> half(right, m) }
        val stroke = Stroke(width = 2f)
        drawRect(Line, style = stroke)
        drawLine(Line, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 2f)
        drawCircle(Line, radius = 1.8f * m, center = center, style = stroke)
        // 攻めるチームの色の帯
        drawRect(leftColor, topLeft = Offset(0f, 0f), size = Size(0.25f * m, size.height))
        drawRect(rightColor, topLeft = Offset(size.width - 0.25f * m, 0f), size = Size(0.25f * m, size.height))
        marks.forEach { mark ->
            val c = Offset((mark.x * size.width).toFloat(), (mark.y * size.height).toFloat())
            val r = 0.38f * m
            when {
                mark.pending -> {
                    drawCircle(Color.White, radius = r * 1.4f, center = c)
                    drawCircle(Color.Black, radius = r * 1.4f, center = c, style = Stroke(width = 4f))
                }
                mark.made -> drawCircle(mark.color, radius = r, center = c)
                else -> {
                    drawLine(mark.color, c + Offset(-r, -r), c + Offset(r, r), strokeWidth = 5f)
                    drawLine(mark.color, c + Offset(-r, r), c + Offset(r, -r), strokeWidth = 5f)
                }
            }
        }
    }
}

/** 片側の半面（2P の区域・制限区域・フリースローの円・ゴール） */
private fun DrawScope.half(right: Boolean, m: Float) {
    val w = Court.WIDTH.toFloat()
    val basketX = Court.BASKET_FROM_BASELINE.toFloat()
    val r = Court.THREE_RADIUS.toFloat()
    val corner = Court.THREE_CORNER.toFloat()
    // ゴールから、コーナーの直線が弧に交わるまでの深さ
    val depth = sqrt(r * r - corner * corner)
    fun px(x: Float) = (if (right) Court.LENGTH.toFloat() - x else x) * m
    fun py(y: Float) = y * m
    val cy = w / 2
    // 2P の区域（エンドライン → コーナーの直線 → 弧 → コーナーの直線 → エンドライン）
    val sweep = Math.toDegrees(2 * asin((corner / r).toDouble())).toFloat()
    val arcRect = Rect(Offset(px(basketX) - r * m, py(cy) - r * m), Size(2 * r * m, 2 * r * m))
    val path = Path().apply {
        moveTo(px(0f), py(cy - corner))
        lineTo(px(basketX + depth), py(cy - corner))
        if (right) {
            arcTo(arcRect, 180f + sweep / 2, -sweep, false)
        } else {
            arcTo(arcRect, -(sweep / 2), sweep, false)
        }
        lineTo(px(0f), py(cy + corner))
        close()
    }
    drawPath(path, Floor)
    drawPath(path, Line, style = Stroke(width = 2f))
    // 制限区域（幅 4.9 m・エンドラインから 5.8 m）
    val keyLeft = if (right) px(5.8f) else px(0f)
    drawRect(Paint, topLeft = Offset(keyLeft, py(cy - 2.45f)), size = Size(5.8f * m, 4.9f * m))
    drawRect(Line, topLeft = Offset(keyLeft, py(cy - 2.45f)), size = Size(5.8f * m, 4.9f * m), style = Stroke(width = 2f))
    drawCircle(Line, radius = 1.8f * m, center = Offset(px(5.8f), py(cy)), style = Stroke(width = 2f))
    // ノーチャージセミサークル（1.25 m）とゴール
    drawCircle(Line, radius = 1.25f * m, center = Offset(px(basketX), py(cy)), style = Stroke(width = 1.5f))
    drawCircle(Color(0xFFE65100), radius = 0.25f * m, center = Offset(px(basketX), py(cy)), style = Stroke(width = 3f))
    drawLine(Line, Offset(px(1.2f), py(cy - 0.9f)), Offset(px(1.2f), py(cy + 0.9f)), strokeWidth = 4f)
}
