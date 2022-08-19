package com.markvtls.cameraApp

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.mlkit.vision.face.Face

class FaceOverlay constructor(overlay: GraphicOverlay, private val face: Face) : GraphicOverlay.Graphic(overlay) {
    private val facePositionPaint: Paint


    init {
        val selectedColor = Color.WHITE
        facePositionPaint = Paint()
        facePositionPaint.color = selectedColor
    }

    companion object {
        private const val FACE_POSITION_RADIUS = 5.0f
    }

    override fun draw(canvas: Canvas?) {

        if (canvas != null) {
            val x = translateX(face.boundingBox.centerX().toFloat())
            val y = translateY(face.boundingBox.centerY().toFloat())
            canvas.drawCircle(y, x, FACE_POSITION_RADIUS, facePositionPaint)




            for (contour in face.allContours) {
                for (point in contour.points) {
                    canvas.drawCircle(
                        translateX(point.x),
                        translateY(point.y),
                        FACE_POSITION_RADIUS,
                        facePositionPaint
                    )
                }
            }

        }

    }
}