package com.orangegangsters.github.swipyrefreshlayout.library

import android.content.Context
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.util.AttributeSet
import android.view.animation.Animation.AnimationListener
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatImageView

/**
 * Private class created to work around issues with AnimationListeners being
 * called before the animation is actually complete and support shadows on older
 * platforms.
 *
 * @hide
 */

// PX
private const val SHADOW_RADIUS = 3.5f
private const val SHADOW_ELEVATION = 4

class CircleImageView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {
    private var mListener: AnimationListener? = null
    private val mShadowRadius: Int

    init {
        val density = getContext().resources.displayMetrics.density
        mShadowRadius = (density * SHADOW_RADIUS).toInt()
        val circle = ShapeDrawable(OvalShape())
        elevation = SHADOW_ELEVATION * density
        background = circle
    }

    fun setColor(@ColorInt color: Int) {
        (background as ShapeDrawable).paint.color = color
    }

    fun setAnimationListener(listener: AnimationListener?) {
        mListener = listener
    }

    // public override fun onAnimationStart() {
    //     super.onAnimationStart()
    //     animation?.let { mListener?.onAnimationStart(it) }
    // }
    //
    // public override fun onAnimationEnd() {
    //     super.onAnimationEnd()
    //     animation?.let { mListener?.onAnimationEnd(it) }
    // }

    /**
     * Update the background color of the circle image view.
     */
    override fun setBackgroundColor(@ColorInt color: Int) {
        (background as? ShapeDrawable)?.paint?.color = color
    }
}