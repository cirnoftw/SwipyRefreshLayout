package com.orangegangsters.github.swipyrefreshlayout.library

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.*
import android.view.animation.Animation.AnimationListener
import android.view.animation.Interpolator
import androidx.annotation.IntDef
import java.util.*
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Fancy progress indicator for Material theme.
 *
 * @hide
 */
// Maps to ProgressBar default style
private const val CIRCLE_DIAMETER = 40
private const val CENTER_RADIUS = 8.75f //should add up to 10 when + stroke_width
private const val STROKE_WIDTH = 2.5f

// Maps to ProgressBar.Large style
private const val CIRCLE_DIAMETER_LARGE = 56
private const val CENTER_RADIUS_LARGE = 12.5f
private const val STROKE_WIDTH_LARGE = 3f

/** The duration of a single progress spin in milliseconds.  */
private const val ANIMATION_DURATION = 1000 * 80 / 60

/** The number of points in the progress "star".  */
private const val NUM_POINTS = 5f

/** Layout info for the arrowhead in dp  */
private const val ARROW_WIDTH = 10
private const val ARROW_HEIGHT = 5
private const val ARROW_OFFSET_ANGLE = 5f

/** Layout info for the arrowhead for the large spinner in dp  */
private const val ARROW_WIDTH_LARGE = 12
private const val ARROW_HEIGHT_LARGE = 6
private const val MAX_PROGRESS_ARC = .8f

internal class MaterialProgressDrawable(context: Context, private val mParent: View) : Drawable(), Animatable {
    private var stopped = false

    @Retention(AnnotationRetention.BINARY)
    @IntDef(LARGE, DEFAULT)
    annotation class ProgressDrawableSize

    private val COLORS = intArrayOf(Color.BLACK)

    /** The list of animators operating on this drawable.  */
    private val mAnimators = ArrayList<Animation>()

    private val mCallback: Callback = object : Callback {
        override fun invalidateDrawable(d: Drawable) {
            invalidateSelf()
        }

        override fun scheduleDrawable(d: Drawable, what: Runnable, `when`: Long) {
            scheduleSelf(what, `when`)
        }

        override fun unscheduleDrawable(d: Drawable, what: Runnable) {
            unscheduleSelf(what)
        }
    }

    /** The indicator ring, used to manage animation state.  */
    private val mRing: Ring = Ring(mCallback)

    /** Canvas rotation in degrees.  */
    private var mRotation = 0f
    private val mResources: Resources = context.resources
    private var mAnimation: Animation? = null
    private var mRotationCount = 0f
    private var mWidth = 0.0
    private var mHeight = 0.0
    private var mFinishAnimation: Animation? = null
    private fun setSizeParameters(
            progressCircleWidth: Double,
            progressCircleHeight: Double,
            centerRadius: Double,
            strokeWidth: Double,
            arrowWidth: Float,
            arrowHeight: Float
    ) {
        mRing.apply {
            val metrics = mResources.displayMetrics
            val screenDensity = metrics.density
            mWidth = progressCircleWidth * screenDensity
            mHeight = progressCircleHeight * screenDensity

            this.strokeWidth = strokeWidth.toFloat() * screenDensity
            this.centerRadius = centerRadius * screenDensity
            setColorIndex(0)
            setArrowDimensions(arrowWidth * screenDensity, arrowHeight * screenDensity)
            setInsets(mWidth.toInt(), mHeight.toInt())
        }
    }

    /**
     * Set the overall size for the progress spinner. This updates the radius
     * and stroke width of the ring.
     *
     * @param size One of [.LARGE] or
     * [.DEFAULT]
     */
    fun updateSizes(@ProgressDrawableSize size: Int) {
        if (size == LARGE) {
            setSizeParameters(
                    CIRCLE_DIAMETER_LARGE.toDouble(),
                    CIRCLE_DIAMETER_LARGE.toDouble(),
                    CENTER_RADIUS_LARGE.toDouble(),
                    STROKE_WIDTH_LARGE.toDouble(),
                    ARROW_WIDTH_LARGE.toFloat(),
                    ARROW_HEIGHT_LARGE.toFloat()
            )
        } else {
            setSizeParameters(
                    CIRCLE_DIAMETER.toDouble(),
                    CIRCLE_DIAMETER.toDouble(),
                    CENTER_RADIUS.toDouble(),
                    STROKE_WIDTH.toDouble(),
                    ARROW_WIDTH.toFloat(),
                    ARROW_HEIGHT.toFloat()
            )
        }
    }

    /**
     * @param show Set to true to display the arrowhead on the progress spinner.
     */
    fun showArrow(show: Boolean) {
        mRing.setShowArrow(show)
    }

    /**
     * @param scale Set the scale of the arrowhead for the spinner.
     */
    fun setArrowScale(scale: Float) {
        mRing.setArrowScale(scale)
    }

    /**
     * Set the start and end trim for the progress spinner arc.
     *
     * @param startAngle start angle
     * @param endAngle end angle
     */
    fun setStartEndTrim(startAngle: Float, endAngle: Float) {
        mRing.startTrim = startAngle
        mRing.endTrim = endAngle
    }

    /**
     * Set the amount of rotation to apply to the progress spinner.
     *
     * @param rotation Rotation is from [0..1]
     */
    fun setProgressRotation(rotation: Float) {
        mRing.rotation = rotation
    }

    /**
     * Update the background color of the circle image view.
     */
    fun setBackgroundColor(color: Int) {
        mRing.setBackgroundColor(color)
    }

    /**
     * Set the colors used in the progress animation from color resources.
     * The first color will also be the color of the bar that grows in response
     * to a user swipe gesture.
     *
     * @param colors
     */
    fun setColorSchemeColors(vararg colors: Int) {
        mRing.setColors(colors)
        mRing.setColorIndex(0)
    }

    override fun getIntrinsicHeight(): Int {
        return mHeight.toInt()
    }

    override fun getIntrinsicWidth(): Int {
        return mWidth.toInt()
    }

    override fun draw(c: Canvas) {
        val bounds = bounds
        val saveCount = c.save()
        c.rotate(mRotation, bounds.exactCenterX(), bounds.exactCenterY())
        mRing.draw(c, bounds)
        c.restoreToCount(saveCount)
    }

    override fun setAlpha(alpha: Int) {
        mRing.alpha = alpha
    }

    override fun getAlpha(): Int {
        return mRing.alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        mRing.setColorFilter(colorFilter)
    }

    private var rotation: Float
        get() = mRotation
        set(rotation) {
            mRotation = rotation
            invalidateSelf()
        }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun isRunning(): Boolean {
        val animators = mAnimators
        for (i in 0 until animators.size) {
            val animator = animators[i]
            if (animator.hasStarted() && !animator.hasEnded()) {
                return true
            }
        }
        return false
    }

    override fun start() {
        stopped = false
        mAnimation!!.reset()
        mRing.storeOriginals()
        // Already showing some part of the ring
        if (mRing.endTrim != mRing.startTrim) {
            mParent.startAnimation(mFinishAnimation)
        } else {
            mRing.setColorIndex(0)
            mRing.resetOriginals()
            mParent.startAnimation(mAnimation)
        }
    }

    override fun stop() {
        stopped = true
        mParent.clearAnimation()
        rotation = 0f
        mRing.setShowArrow(false)
        mRing.setColorIndex(0)
        mRing.resetOriginals()
    }

    private fun setupAnimators() {
        val ring = mRing
        val finishRingAnimation: Animation = object : Animation() {
            public override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                // shrink back down and complete a full rotation before starting other circles
                // Rotation goes between [0..1].
                val targetRotation = (floor((ring.startingRotation / MAX_PROGRESS_ARC).toDouble()) + 1f).toFloat()
                val startTrim = (ring.startingStartTrim
                        + (ring.startingEndTrim - ring.startingStartTrim)
                        * interpolatedTime)
                ring.startTrim = startTrim
                val rotation = (ring.startingRotation
                        + (targetRotation - ring.startingRotation) * interpolatedTime)
                ring.rotation = rotation
                ring.setArrowScale(1 - interpolatedTime)
            }
        }
        finishRingAnimation.interpolator = EASE_INTERPOLATOR
        finishRingAnimation.duration = (ANIMATION_DURATION / 2).toLong()
        finishRingAnimation.setAnimationListener(object : AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                if (stopped) {
                    return
                }
                ring.goToNextColor()
                ring.storeOriginals()
                ring.setShowArrow(false)
                mParent.startAnimation(mAnimation)
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        val animation: Animation = object : Animation() {
            public override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                // The minProgressArc is calculated from 0 to create an angle that
                // matches the stroke width.
                val minProgressArc = Math.toRadians(ring.strokeWidth
                        / (2 * Math.PI * ring.centerRadius)).toFloat()
                val startingEndTrim = ring.startingEndTrim
                val startingTrim = ring.startingStartTrim
                val startingRotation = ring.startingRotation

                // Offset the minProgressArc to where the endTrim is located.
                val minArc = MAX_PROGRESS_ARC - minProgressArc
                val endTrim = (startingEndTrim
                        + minArc * START_CURVE_INTERPOLATOR.getInterpolation(interpolatedTime))
                ring.endTrim = endTrim
                val startTrim = (startingTrim
                        + MAX_PROGRESS_ARC * END_CURVE_INTERPOLATOR
                        .getInterpolation(interpolatedTime))
                ring.startTrim = startTrim
                val rotation = startingRotation + 0.25f * interpolatedTime
                ring.rotation = rotation
            }
        }.apply {
            repeatCount = Animation.INFINITE
            repeatMode = Animation.RESTART
            interpolator = LINEAR_INTERPOLATOR
            duration = ANIMATION_DURATION.toLong()
            setAnimationListener(object : AnimationListener {
                override fun onAnimationStart(animation: Animation) {
                    mRotationCount = 0f
                }

                override fun onAnimationEnd(animation: Animation) {
                    // do nothing
                }

                override fun onAnimationRepeat(animation: Animation) {
                    ring.storeOriginals()
                    ring.goToNextColor()
                    ring.startTrim = ring.endTrim
                    mRotationCount = (mRotationCount + 1) % NUM_POINTS
                }
            })
        }
        mFinishAnimation = finishRingAnimation
        mAnimation = animation
    }

    private inner class Ring(private val mCallback: Callback) {
        private val mTempBounds = RectF()
        private val mPaint = Paint().apply {
            strokeCap = Paint.Cap.SQUARE
            isAntiAlias = true
            style = Paint.Style.STROKE
        }
        private val mArrowPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        private var mStartTrim = 0.0f
        private var mEndTrim = 0.0f
        private var mRotation = 0.0f
        private var mStrokeWidth = 5.0f
        var insets = 2.5f
            private set
        private lateinit var mColors: IntArray

        // mColorIndex represents the offset into the available mColors that the
        // progress circle should currently display. As the progress circle is
        // animating, the mColorIndex moves by one to the next available color.
        private var mColorIndex = 0
        var startingStartTrim = 0f
            private set
        var startingEndTrim = 0f
            private set

        /**
         * @return The amount the progress spinner is currently rotated, between [0..1].
         */
        var startingRotation = 0f
            private set
        private var mShowArrow = false
        private var mArrow: Path? = null
        private var mArrowScale = 0f

        /**
         * @param centerRadius Inner radius in px of the circle the progress
         * spinner arc traces.
         */
        var centerRadius = 0.0
        private var mArrowWidth = 0
        private var mArrowHeight = 0
        /**
         * @return Current alpha of the progress spinner and arrowhead.
         */
        /**
         * @param alpha Set the alpha of the progress spinner and associated arrowhead.
         */
        var alpha = 0
        private val mCirclePaint = Paint()
        private var mBackgroundColor = 0
        fun setBackgroundColor(color: Int) {
            mBackgroundColor = color
        }

        /**
         * Set the dimensions of the arrowhead.
         *
         * @param width Width of the hypotenuse of the arrow head
         * @param height Height of the arrow point
         */
        fun setArrowDimensions(width: Float, height: Float) {
            mArrowWidth = width.toInt()
            mArrowHeight = height.toInt()
        }

        /**
         * Draw the progress spinner
         */
        fun draw(c: Canvas, bounds: Rect) {
            val arcBounds = mTempBounds
            arcBounds.set(bounds)
            arcBounds.inset(insets, insets)
            val startAngle = (mStartTrim + mRotation) * 360
            val endAngle = (mEndTrim + mRotation) * 360
            val sweepAngle = endAngle - startAngle
            mPaint.color = mColors[mColorIndex]
            c.drawArc(arcBounds, startAngle, sweepAngle, false, mPaint)
            drawTriangle(c, startAngle, sweepAngle, bounds)
            if (alpha < 255) {
                mCirclePaint.color = mBackgroundColor
                mCirclePaint.alpha = 255 - alpha
                c.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), (bounds.width() / 2).toFloat(),
                        mCirclePaint)
            }
        }

        private fun drawTriangle(c: Canvas, startAngle: Float, sweepAngle: Float, bounds: Rect) {
            if (mShowArrow) {
                if (mArrow == null) {
                    mArrow = Path().apply { fillType = Path.FillType.EVEN_ODD }
                } else {
                    mArrow?.reset()
                }

                // Adjust the position of the triangle so that it is inset as
                // much as the arc, but also centered on the arc.
                val inset = insets.toInt() / 2 * mArrowScale
                val x = (centerRadius * cos(0.0) + bounds.exactCenterX()).toFloat()
                val y = (centerRadius * sin(0.0) + bounds.exactCenterY()).toFloat()

                // Update the path each time. This works around an issue in SKIA
                // where concatenating a rotation matrix to a scale matrix
                // ignored a starting negative rotation. This appears to have
                // been fixed as of API 21.
                mArrow?.run {
                    moveTo(0f, 0f)
                    lineTo(mArrowWidth * mArrowScale, 0f)
                    lineTo(mArrowWidth * mArrowScale / 2, mArrowHeight * mArrowScale)
                    offset(x - inset, y)
                    close()
                }
                // draw a triangle
                mArrowPaint.color = mColors[mColorIndex]
                c.rotate(startAngle + sweepAngle - ARROW_OFFSET_ANGLE, bounds.exactCenterX(),
                        bounds.exactCenterY())
                c.drawPath(mArrow!!, mArrowPaint)
            }
        }

        /**
         * Set the colors the progress spinner alternates between.
         *
         * @param colors Array of integers describing the colors. Must be non-`null`.
         */
        fun setColors(colors: IntArray) {
            mColors = colors
            // if colors are reset, make sure to reset the color index as well
            setColorIndex(0)
        }

        /**
         * @param index Index into the color array of the color to display in
         * the progress spinner.
         */
        fun setColorIndex(index: Int) {
            mColorIndex = index
        }

        /**
         * Proceed to the next available ring color. This will automatically
         * wrap back to the beginning of colors.
         */
        fun goToNextColor() {
            mColorIndex = (mColorIndex + 1) % mColors.size
        }

        fun setColorFilter(filter: ColorFilter?) {
            mPaint.colorFilter = filter
            invalidateSelf()
        }

        /**
         * @param strokeWidth Set the stroke width of the progress spinner in pixels.
         */
        var strokeWidth: Float
            get() = mStrokeWidth
            set(strokeWidth) {
                mStrokeWidth = strokeWidth
                mPaint.strokeWidth = strokeWidth
                invalidateSelf()
            }

        var startTrim: Float
            get() = mStartTrim
            set(startTrim) {
                mStartTrim = startTrim
                invalidateSelf()
            }

        var endTrim: Float
            get() = mEndTrim
            set(endTrim) {
                mEndTrim = endTrim
                invalidateSelf()
            }

        var rotation: Float
            get() = mRotation
            set(rotation) {
                mRotation = rotation
                invalidateSelf()
            }

        fun setInsets(width: Int, height: Int) {
            val minEdge = width.coerceAtMost(height).toFloat()
            val insets: Float = if (centerRadius <= 0 || minEdge < 0) {
                ceil((mStrokeWidth / 2.0f).toDouble()).toFloat()
            } else {
                (minEdge / 2.0f - centerRadius).toFloat()
            }
            this.insets = insets
        }

        /**
         * @param show Set to true to show the arrow head on the progress spinner.
         */
        fun setShowArrow(show: Boolean) {
            if (mShowArrow != show) {
                mShowArrow = show
                invalidateSelf()
            }
        }

        /**
         * @param scale Set the scale of the arrowhead for the spinner.
         */
        fun setArrowScale(scale: Float) {
            if (scale != mArrowScale) {
                mArrowScale = scale
                invalidateSelf()
            }
        }

        /**
         * If the start / end trim are offset to begin with, store them so that
         * animation starts from that offset.
         */
        fun storeOriginals() {
            startingStartTrim = mStartTrim
            startingEndTrim = mEndTrim
            startingRotation = mRotation
        }

        /**
         * Reset the progress spinner to default rotation, start and end angles.
         */
        fun resetOriginals() {
            startingStartTrim = 0f
            startingEndTrim = 0f
            startingRotation = 0f
            startTrim = 0f
            endTrim = 0f
            rotation = 0f
        }

        private fun invalidateSelf() {
            mCallback.invalidateDrawable(this@MaterialProgressDrawable)
        }
    }

    /**
     * Squishes the interpolation curve into the second half of the animation.
     */
    private class EndCurveInterpolator : AccelerateDecelerateInterpolator() {
        override fun getInterpolation(input: Float): Float {
            return super.getInterpolation(0f.coerceAtLeast((input - 0.5f) * 2.0f))
        }
    }

    /**
     * Squishes the interpolation curve into the first half of the animation.
     */
    private class StartCurveInterpolator : AccelerateDecelerateInterpolator() {
        override fun getInterpolation(input: Float): Float {
            return super.getInterpolation(1f.coerceAtMost(input * 2.0f))
        }
    }

    companion object {
        private val LINEAR_INTERPOLATOR: Interpolator = LinearInterpolator()
        private val END_CURVE_INTERPOLATOR: Interpolator = EndCurveInterpolator()
        private val START_CURVE_INTERPOLATOR: Interpolator = StartCurveInterpolator()
        private val EASE_INTERPOLATOR: Interpolator = AccelerateDecelerateInterpolator()

        // Maps to ProgressBar.Large style
        const val LARGE = 0

        // Maps to ProgressBar default style
        const val DEFAULT = 1
    }

    init {
        mRing.setColors(COLORS)
        updateSizes(DEFAULT)
        setupAnimators()
    }
}