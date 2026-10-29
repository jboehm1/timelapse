package eu.example.constructionlapse

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/**
 * Lightweight independent AF/AE target visualization.
 * The app never considers a target "hardware applied" only because this view drew a marker.
 * Positions are normalized to the preview's currently visible area; hardware accepts regions
 * independently, through PreviewView.meteringPointFactory in MainActivity.
 */
class MeteringOverlay(context: Context) : View(context) {
    enum class Target { FOCUS, EXPOSURE, BOTH }
    data class Marker(val x: Float, val y: Float, val size: Float, val feedback: String = "PENDING") {
        init { require(x in 0f..1f && y in 0f..1f && size in 0.02f..0.50f) }
    }
    var focus: Marker? = null
        private set
    var exposure: Marker? = null
        private set
    var target: Target = Target.FOCUS
    var showGrid: Boolean = true
        set(value) { field = value; invalidate() }
    var onSelect: ((Target, Marker) -> Unit)? = null
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textSize = 13f * resources.displayMetrics.scaledDensity
        isFakeBoldText = true
    }
    private fun drawMarker(canvas: Canvas, marker: Marker, name: String, color: Int, square: Boolean) {
        val cx = marker.x * width
        val cy = marker.y * height
        val radius = (marker.size * minOf(width, height) / 2f).coerceAtLeast(15f)
        pen.color = Color.BLACK
        pen.strokeWidth = 6f * resources.displayMetrics.density
        if (square) canvas.drawRect(cx-radius,cy-radius,cx+radius,cy+radius,pen)
        else canvas.drawCircle(cx,cy,radius,pen)
        pen.color = color
        pen.strokeWidth = 2.5f * resources.displayMetrics.density
        if (square) canvas.drawRect(cx-radius,cy-radius,cx+radius,cy+radius,pen)
        else canvas.drawCircle(cx,cy,radius,pen)
        canvas.drawLine(cx-9f,cy,cx+9f,cy,pen)
        canvas.drawLine(cx,cy-9f,cx,cy+9f,pen)
        label.color = Color.BLACK
        val sx = cx + radius + 5f
        val sy = (cy-radius).coerceAtLeast(label.textSize + 2f)
        canvas.drawText("$name: ${marker.feedback}",sx+1,sy+1,label)
        label.color = color
        canvas.drawText("$name: ${marker.feedback}",sx,sy,label)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (showGrid && width>0 && height>0) {
            pen.color = Color.argb(110, 245, 248, 255)
            pen.strokeWidth = resources.displayMetrics.density
            for (i in 1..2) {
                val x = width*i/3f; val y = height*i/3f
                canvas.drawLine(x,0f,x,height.toFloat(),pen)
                canvas.drawLine(0f,y,width.toFloat(),y,pen)
            }
        }
        focus?.let { drawMarker(canvas,it,"AF",Color.GREEN,true) }
        exposure?.let { drawMarker(canvas,it,"AE",Color.YELLOW,false) }
    }
    fun setMarker(target: Target, marker: Marker) {
        if(target == Target.FOCUS || target == Target.BOTH) focus = marker
        if(target == Target.EXPOSURE || target == Target.BOTH) exposure = marker
        invalidate()
    }
    fun setFeedback(target: Target, message: String) {
        if (target == Target.FOCUS || target == Target.BOTH)
            focus = focus?.copy(feedback=message)
        if (target == Target.EXPOSURE || target == Target.BOTH)
            exposure = exposure?.copy(feedback=message)
        invalidate()
    }
    fun restore(focusMarker: Marker?, exposureMarker: Marker?) {
        focus = focusMarker
        exposure = exposureMarker
        invalidate()
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(event.action == MotionEvent.ACTION_UP && width>0 && height>0) {
            val marker = Marker((event.x/width).coerceIn(0f,1f),
                (event.y/height).coerceIn(0f,1f),0.12f)
            setMarker(target,marker)
            onSelect?.invoke(target,marker)
            performClick()
            return true
        }
        return true
    }
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
