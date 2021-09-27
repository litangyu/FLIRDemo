package me.kuma.flirdemo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import com.chillingvan.canvasgl.ICanvasGL
import com.chillingvan.canvasgl.androidCanvas.IAndroidCanvasHelper
import com.chillingvan.canvasgl.glview.texture.GLTextureView
import java.util.concurrent.LinkedBlockingQueue


/**
 * Description here.
 *
 * Created on: 2021/5/13 10:27 上午
 * @author lty <a href="mailto:lty81372860@gmail.com">Contact me.</a>
 */
class LiveView : GLTextureView {

  private val drawTextHelper = IAndroidCanvasHelper.Factory.createAndroidCanvasHelper(IAndroidCanvasHelper.MODE.MODE_ASYNC)

  private val queue = LinkedBlockingQueue<Bitmap>(30)

  private val textFPSMargin = QMUIDisplayHelper.dp2px(context, 16).toFloat()

  private val canvas: Canvas = Canvas()
  private val paint = Paint().apply {
    this.color = Color.GREEN
    this.textSize = QMUIDisplayHelper.sp2px(context, 12).toFloat()
  }

  private val drawPaint = Paint()

//  private val fpsHelper = FPSHelper()
  private var fpsFlag: Boolean = false

  constructor(context: Context) : this(context, null)
  constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  override fun onSurfaceChanged(width: Int, height: Int) {
    super.onSurfaceChanged(width, height)
    drawTextHelper.init(width, height)
  }

  override fun onGLDraw(canvasGL: ICanvasGL) {
    val bitmap = this.queue.poll()
    if (bitmap != null) {
      drawTextHelper.draw { androidCanvas, drawBitmap ->
        drawBitmap.eraseColor(Color.TRANSPARENT)
        androidCanvas.drawBitmap(bitmap, 0f, 0f, drawPaint)
        bitmap.recycle()
        if (fpsFlag) {
//          val fps = fpsHelper.fixedTime()
//          androidCanvas.drawText("FPS: $fps", textFPSMargin, textFPSMargin, paint)
        }
      }

      val outputBitmap = drawTextHelper.outputBitmap
      canvasGL.invalidateTextureContent(outputBitmap)
      canvasGL.drawBitmap(outputBitmap, 0, 0)
    }
  }

  fun displayFPS(flag: Boolean) {
    this.fpsFlag = flag
  }

  fun offNewFrame(bitmap: Bitmap) {
    this.queue.offer(bitmap)
    requestRenderAndWait()
  }

}