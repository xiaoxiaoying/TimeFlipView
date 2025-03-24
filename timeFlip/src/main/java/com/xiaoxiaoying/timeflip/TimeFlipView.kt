package com.xiaoxiaoying.timeflip

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.CountDownTimer
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.animation.doOnEnd
import androidx.core.content.withStyledAttributes
import com.xiaoxiaoying.timeflip.SizeUtils.getTextSize
import java.util.Locale
import kotlin.math.min

class TimeFlipView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : View(context, attrs, defStyleAttr, defStyleRes) {
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val colonPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    var timeBackgroundColor: Int = Color.BLACK
        set(value) {
            if (field == value) {
                return
            }
            field = value
            boxPaint.setColor(value)
            colonPaint.setColor(value)
            postInvalidate()
        }

    private var timePaddingStart = 0F
    private var timePaddingEnd = 0F
    private var timePaddingTop = 0F
    private var timePaddingBottom = 0F
    private val digits = arrayOf("0", "2", ":", "2", "0", ":", "2", "0")
    private var newDigits = arrayOf("0", "2", ":", "2", "0", ":", "2", "0")
    private val boxes = digits.map { RectF() }
    private var flipProgress = 0f // 翻页动画进度
    private var animator: ValueAnimator? = null
    private var countDownTimer: CountDownTimer? = null
    private val camera = Camera() // 用于 3D 变换

    // 创建 Path 对象
    private val path = Path()
    private val shadowPaint = Paint().apply {
        color = Color.argb(100, 0, 0, 0) // 半透明黑色阴影
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    companion object {
        private const val SECONDS: Long = 1000
        private const val MINUTES = 60 * SECONDS
        private const val HOURS = 60 * MINUTES
        private const val DAY = 24 * HOURS
    }

    var isCountDownTimer: Boolean = true

    var time: Long = System.currentTimeMillis()
        set(value) {
            if (field == value)
                return
            field = value
        }

    var sunken: Float = 2F
        set(value) {
            if (field == value)
                return
            field = value
            postInvalidate()
        }
    var dashGap: Float = 10F
        set(value) {
            if (field == value) {
                return
            }
            field = value
            postInvalidate()
        }

    var hasAnim: Boolean = false
    var textSize: Float = context.getTextSize(12F)
        set(value) {
            field = value
            textPaint.textSize = value
            colonPaint.textSize = value * 1.5F
        }

    var textColor: Int = Color.WHITE
        set(value) {
            field = value
            textPaint.setColor(value)
        }

    var textStyle: Int = Typeface.BOLD
        set(value) {
            field = value
            textPaint.flags = if (Typeface.BOLD == value) {
                Paint.FAKE_BOLD_TEXT_FLAG
            } else {
                Paint.ANTI_ALIAS_FLAG
            }
            colonPaint.flags = textPaint.flags
        }

    var radius: Float = 8f
        set(value) {
            if (field == value)
                return
            field = value
            postInvalidate()
        }

    var onFinishCall: () -> Unit = {}

    private var itemWidth: Float = 0F
    private var itemHeight: Float = 0F

    init {
        context.withStyledAttributes(attrs, R.styleable.TimeFlipView, defStyleAttr, defStyleRes) {
            textSize = getDimension(R.styleable.TimeFlipView_android_textSize, textSize)
            textColor = getColor(R.styleable.TimeFlipView_android_textColor, textColor)
            textStyle = getInt(R.styleable.TimeFlipView_android_textStyle, textStyle)
            hasAnim = getBoolean(R.styleable.TimeFlipView_time_hasAnim, hasAnim)
            dashGap = getDimension(R.styleable.TimeFlipView_android_dashGap, dashGap)
            radius = getDimension(R.styleable.TimeFlipView_android_radius, radius)
            sunken = getDimension(R.styleable.TimeFlipView_time_sunken, sunken)
            timeBackgroundColor =
                getColor(R.styleable.TimeFlipView_backgroundColor, timeBackgroundColor)

            timePaddingStart =
                getDimension(R.styleable.TimeFlipView_time_paddingStart, timePaddingStart)
            timePaddingEnd = getDimension(R.styleable.TimeFlipView_time_paddingEnd, timePaddingEnd)
            timePaddingTop = getDimension(R.styleable.TimeFlipView_time_paddingTop, timePaddingTop)
            timePaddingBottom =
                getDimension(R.styleable.TimeFlipView_time_paddingBottom, timePaddingBottom)
            val timePadding = getDimension(R.styleable.TimeFlipView_time_padding, 0F)
            if (timePadding != 0F && timePaddingBottom == 0F && timePaddingTop == 0F && timePaddingStart == 0F && timePaddingEnd == 0F) {
                timePaddingStart = timePadding
                timePaddingEnd = timePadding
                timePaddingTop = timePadding
                timePaddingBottom = timePadding
            }
            val paddingVertical = getDimension(R.styleable.TimeFlipView_time_paddingVertical, 0F)
            if (paddingVertical != 0F && timePaddingTop == 0F && timePaddingBottom == 0F) {
                timePaddingTop = paddingVertical
                timePaddingBottom = paddingVertical
            }
            val paddingHorizontal =
                getDimension(R.styleable.TimeFlipView_time_paddingHorizontal, 0F)
            if (paddingHorizontal != 0F && timePaddingStart == 0F && timePaddingEnd == 0F) {
                timePaddingStart = paddingHorizontal
                timePaddingEnd = paddingHorizontal
            }

            itemWidth = getDimension(R.styleable.TimeFlipView_time_itemWidth, itemWidth)
            itemHeight = getDimension(R.styleable.TimeFlipView_time_itemHeight, itemHeight)

        }

        boxPaint.style = Paint.Style.FILL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val widthMode = MeasureSpec.getSize(widthMeasureSpec)
        // 获取屏幕宽度
        val screenWidth = resources.displayMetrics.widthPixels
        val winWith = getWinWidth()
//        val winWith = 0F
        // 确定宽度
        val width = if (winWith > 0F) {
            winWith.toInt()
        } else if (widthMode == MeasureSpec.EXACTLY) {
            widthSize // 如果宽度是精确值（match_parent 或具体值），使用指定宽度
        } else {
            screenWidth // 如果宽度是 wrap_content，使用屏幕宽度
        }
        val size = digits.size
        // 冒号的宽度，比其他的窄
        val colon = width / (size * 4F)
        val boxWidth = (width - (size - 1) * dashGap - colon * 2) / (size - 2).toFloat()

        // 处理高度
        val height = if (itemHeight > 0F) {
            itemHeight.toInt()
        } else if (heightMode == MeasureSpec.AT_MOST) {
            min(boxWidth.toDouble(), heightSize.toDouble()).toInt() // 不超过父布局的最大值
        } else {
            boxWidth.toInt() // 未指定时使用自定义高度
        }
        setMeasuredDimension(widthSize, height)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        startTime()
    }

    private fun getWinWidth(): Float {

        if (itemWidth <= 0F) {
            return 0F
        }

        return itemWidth * (digits.size - 2) + (digits.size - 1) * dashGap + 2 * itemWidth / 4F
    }


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = digits.size
        // 冒号的宽度，比其他的窄
        val colon = w / (size * 4F)
        val boxWidth = (w - (size - 1) * dashGap - colon * 2) / (size - 2).toFloat()
        val boxHeight = h.toFloat()
        (0 until size).forEach {

            when {
                it > 5 -> {
                    val left = (it - 2) * boxWidth + it * dashGap + 2 * colon
                    boxes[it].set(left, 0F, left + boxWidth, boxHeight)
                }

                it == 5 -> {
                    val left = (it - 1) * boxWidth + it * dashGap + colon
                    boxes[it].set(left, 0F, left + colon, boxHeight)
                }

                it == 2 -> {
                    val left = it * (boxWidth + dashGap)
                    boxes[it].set(left, 0F, left + colon, boxHeight)
                }

                it < 2 -> {
                    val left = it * (boxWidth + dashGap)
                    boxes[it].set(left, 0F, left + boxWidth, boxHeight)
                }

                else -> {
                    val left = (it - 1) * boxWidth + it * dashGap + colon
                    boxes[it].set(left, 0F, left + boxWidth, boxHeight)
                }
            }

        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制每个框和文字

        boxes.forEachIndexed { index, rectF ->
            // 绘制文字
            val textWidth = textPaint.measureText(digits[index])
            val x = rectF.centerX() - textWidth / 2F
            val y = rectF.centerY() + (textPaint.textSize / 3)
            // 绘制矩形框
            if (index != 2 && index != 5) {
                if (sunken > 0F) {
                    path.reset()
                    val left = rectF.left
                    val top = rectF.top
                    val right = rectF.right
                    val bottom = rectF.bottom
                    // 左上角圆角
                    path.moveTo(left + radius, top)
                    path.arcTo(left, top, left + 2 * radius, top + 2 * radius, 180f, 90f, false)

                    // 右上角圆角
                    path.lineTo(right - radius, top)
                    path.arcTo(right - 2 * radius, top, right, top + 2 * radius, 270f, 90f, false)
                    path.lineTo(right, top + radius)
                    path.lineTo(right - sunken, rectF.height() / 2F)
                    // 右下角圆角
                    path.lineTo(right, bottom - radius)
                    path.arcTo(
                        right - 2 * radius,
                        bottom - 2 * radius,
                        right,
                        bottom,
                        0f,
                        90f,
                        false
                    )

                    // 左下角圆角
                    path.lineTo(left + radius, bottom)
                    path.arcTo(
                        left,
                        bottom - 2 * radius,
                        left + 2 * radius,
                        bottom,
                        90f,
                        90f,
                        false
                    )
                    path.lineTo(left, bottom - radius)
                    path.lineTo(left + sunken, rectF.height() / 2F)
                    path.lineTo(left, top + radius)

                    // 闭合路径
                    path.close()
                    // 绘制 Path
                    canvas.drawPath(path, boxPaint)
                } else {
                    canvas.drawRoundRect(rectF, radius, radius, boxPaint)
                }

            }


            if (index == 2 || index == 5) {
                canvas.drawText(":", x, y, colonPaint)
            } else if (digits[index] != newDigits[index] && flipProgress > 0F) {
                // 绘制翻页动画
                draw3DFlipAnimation(
                    canvas,
                    rectF,
                    digits[index],
                    newDigits[index],
                    flipProgress,
                    x,
                    y
                )
            } else {
                // 正常绘制文字
                canvas.drawText(digits[index], x, y, textPaint)
            }

        }

    }

    private fun draw3DFlipAnimation(
        canvas: Canvas,
        rectF: RectF,
        currentDigit: String,
        nextDigit: String,
        progress: Float,
        x: Float,
        y: Float
    ) {
        // 保存 Canvas 状态
        canvas.save()

        // 裁剪到当前矩形区域
        val clipPath = Path()
        clipPath.addRect(rectF, Path.Direction.CW)
        canvas.clipPath(clipPath)

        // 计算旋转角度（0 到 90 度）
        val angle = 90f * progress

        // 1. 绘制上半部分（固定，显示当前数字）
        canvas.save()
        val upperClipPath = Path()
        upperClipPath.addRect(
            rectF.left,
            rectF.top,
            rectF.right,
            rectF.centerY(),
            Path.Direction.CW
        )
        canvas.clipPath(upperClipPath)
        canvas.drawText(currentDigit, x, y, textPaint)
        canvas.restore()

        // 2. 绘制下半部分（当前数字，向上翻转）
        if (progress < 1f) {
            canvas.save()
            val lowerClipPath = Path()
            lowerClipPath.addRect(
                rectF.left,
                rectF.centerY(),
                rectF.right,
                rectF.bottom,
                Path.Direction.CW
            )
            canvas.clipPath(lowerClipPath)

            // 使用 Camera 实现 3D 旋转
            camera.save()
            camera.rotateX(-angle) // 向上翻转（负角度）
            val matrix = Matrix()
            camera.getMatrix(matrix)
            camera.restore()

            // 设置透视效果
            matrix.preTranslate(-rectF.centerX(), -rectF.centerY())
            matrix.postTranslate(rectF.centerX(), rectF.centerY())
            canvas.concat(matrix)

            // 绘制阴影（随着翻转角度增加，阴影逐渐变淡）
            val shadowAlpha = (255 * (1 - progress)).toInt()
            shadowPaint.alpha = shadowAlpha
            canvas.drawRect(rectF, shadowPaint)

            // 绘制当前数字
            canvas.drawText(currentDigit, x, y, textPaint)
            canvas.restore()
        }

        // 3. 绘制下半部分（新数字，从背面翻转过来）
        canvas.save()
        val lowerClipPath = Path()
        lowerClipPath.addRect(
            rectF.left,
            rectF.centerY(),
            rectF.right,
            rectF.bottom,
            Path.Direction.CW
        )
        canvas.clipPath(lowerClipPath)

        // 使用 Camera 实现 3D 旋转
        camera.save()
        camera.rotateX(90f - angle) // 从背面翻转过来
        val matrix = Matrix()
        camera.getMatrix(matrix)
        camera.restore()

        // 设置透视效果
        matrix.preTranslate(-rectF.centerX(), -rectF.centerY())
        matrix.postTranslate(rectF.centerX(), rectF.centerY())
        canvas.concat(matrix)

        // 绘制阴影
        val shadowAlpha = (255 * progress).toInt()
        shadowPaint.alpha = shadowAlpha
        canvas.drawRect(rectF, shadowPaint)

        // 绘制新数字
        canvas.drawText(nextDigit, x, y, textPaint)
        canvas.restore()

        // 恢复 Canvas 状态
        canvas.restore()
    }

    private fun startTime() {
        startCountDownTimer()
    }


    override fun onAttachedToWindow() {
        if (countDownTimer != null) {
            cancel()
            countDownTimer = null
        }
        startTime()
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        cancel()
        super.onDetachedFromWindow()
    }

    fun cancel() {
        countDownTimer?.cancel()
    }

    private fun startCountDownTimer() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(Long.MAX_VALUE, SECONDS) {
            override fun onTick(millisUntilFinished: Long) {
                if (isCountDownTimer) {
                    time -= SECONDS
                    if (time <= 0L) {
                        onFinishCall()
                        countDownTimer?.cancel()
                    }
                } else {
                    time += SECONDS
                }

                val hour = (time % DAY) / HOURS
                val minute = (time % HOURS) / MINUTES
                val second = (time % MINUTES) / SECONDS

                setTime(String.format(Locale.getDefault(), "%02d:%02d:%02d", hour, minute, second))

            }

            override fun onFinish() {}
        }
        countDownTimer?.start()
    }

    private fun setTime(newTime: String) {
        Log.i("888", "new time = $newTime")
        newDigits = newTime.split("").filter { !TextUtils.isEmpty(it) }.toTypedArray()

        if (digits.contentEquals(newDigits)) {
            return
        }
        Log.i("888", "new time = ${newDigits.size}")
        if (!hasAnim) {
            newDigits.forEachIndexed { index, s ->
                digits[index] = s
            }
            postInvalidate()
            return
        }
        startFlipAnimation()

    }

    private fun startFlipAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0F, 1F)
        animator?.duration = SECONDS
        animator?.addUpdateListener {
            val value = it.animatedValue
            if (value is Float) {
                flipProgress = value
                postInvalidate()
            }
        }
        animator?.doOnEnd {
            newDigits.forEachIndexed { index, s ->
                digits[index] = s
            }
            postInvalidate()
        }
        animator?.start()
    }
}