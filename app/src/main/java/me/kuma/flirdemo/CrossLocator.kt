package me.kuma.flirdemo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RectF
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout


/**
 * Description here.
 *
 * Created on: 2021/6/18 3:05 下午
 * @author lty <a href="mailto:lty81372860@gmail.com">Contact me.</a>
 */
class CrossLocator : ConstraintLayout {

  private lateinit var tvTemperature: TextView

  private var listener: OnFocusPointCoordinateChangeListener? = null
  private var translationRangeRectF: RectF = RectF()

  private var toX: Float = 0.0F
  private var toY: Float = 0.0F

  constructor(context: Context) : this(context, null)
  constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
    initView(context, attrs, defStyleAttr)
  }

  private fun initView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) {
    LayoutInflater.from(context).inflate(R.layout.layout_cross_locator, this, true)
    tvTemperature = findViewById(R.id.tv_temperature)
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent): Boolean {
    when (event.action) {
      MotionEvent.ACTION_DOWN -> {
        toX = event.rawX
        toY = event.rawY
      }
      MotionEvent.ACTION_MOVE -> {
        val rawX = event.rawX
        val rawY = event.rawY
        val translationX = translationX
        val translationY = translationY
        val deltaX = rawX - toX + translationX
        val deltaY = rawY - toY + translationY
        setTranslation(deltaX, deltaY)
        toX = event.rawX
        toY = event.rawY
      }
      MotionEvent.ACTION_UP -> {
      }
    }
    return true
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)
    listener?.onCoordinateChange(left + (right - left) / 2 + translationX.toInt(), top + (bottom - top) / 2 + translationY.toInt())
  }

  fun setTranslation(deltaX: Float, deltaY: Float) {
    translationX = deltaX
    translationY = deltaY
  }

  fun setTranslationRange(rect: RectF) {
    translationRangeRectF = rect
  }

  fun setTemperature(temperatureValue: String) {
    tvTemperature.text = temperatureValue
  }

  fun setOnFocusPointCoordinateChangeListener(listener: OnFocusPointCoordinateChangeListener) {
    this.listener = listener
  }

  interface OnFocusPointCoordinateChangeListener {

    fun onCoordinateChange(x: Int, y: Int)
  }
}