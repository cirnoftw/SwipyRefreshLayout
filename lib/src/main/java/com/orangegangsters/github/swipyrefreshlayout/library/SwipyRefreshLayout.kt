package com.orangegangsters.github.swipyrefreshlayout.library

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.DecelerateInterpolator
import android.view.animation.Transformation
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.pow

/*
* Copyright (C) 2013 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

/**
 * The SwipeRefreshLayout should be used whenever the user can refresh the
 * contents of a view via a vertical swipe gesture. The activity that
 * instantiates this view should add an OnRefreshListener to be notified
 * whenever the swipe to refresh gesture is completed. The SwipeRefreshLayout
 * will notify the listener each and every time the gesture is completed again;
 * the listener is responsible for correctly determining when to actually
 * initiate a refresh of its content. If the listener determines there should
 * not be a refresh, it must call setRefreshing(false) to cancel any visual
 * indication of a refresh. If an activity wishes to show just the progress
 * animation, it should call setRefreshing(true). To disable the gesture and
 * progress animation, call setEnabled(false) on the view.
 *
 *
 * This layout should be made the parent of the view that will be refreshed as a
 * result of the gesture and can only support one direct child. This view will
 * also be made the target of the gesture and will be forced to match both the
 * width and the height supplied in this layout. The SwipeRefreshLayout does not
 * provide accessibility events; instead, a menu item must be provided to allow
 * refresh of the content wherever this gesture is used.
 *
 */

const val TAG = "SwipyRefreshLayout"
private const val MAX_SWIPE_DISTANCE_FACTOR = .6f
private const val REFRESH_TRIGGER_DISTANCE = 120

private const val MAX_ALPHA = 255
private const val STARTING_PROGRESS_ALPHA = (.3f * MAX_ALPHA).toInt()
private const val CIRCLE_DIAMETER = 40
private const val CIRCLE_DIAMETER_LARGE = 56
private const val DECELERATE_INTERPOLATION_FACTOR = 2f
private const val INVALID_POINTER = -1
private const val DRAG_RATE = .5f

// Max amount of circle that can be filled by progress during swipe gesture,
// where 1.0 is a full circle
private const val MAX_PROGRESS_ANGLE = .8f
private const val SCALE_DOWN_DURATION = 150
private const val ALPHA_ANIMATION_DURATION = 300
private const val ANIMATE_TO_TRIGGER_DURATION = 200
private const val ANIMATE_TO_START_DURATION = 200

// Default background for the progress spinner
private const val CIRCLE_BG_LIGHT = -0x50506

// Default offset in dips from the top of the view to where the progress spinner should stop
private const val DEFAULT_CIRCLE_TARGET = 64
private val LAYOUT_ATTRS = intArrayOf(
        android.R.attr.enabled
)

@Suppress("MemberVisibilityCanBePrivate")
open class SwipyRefreshLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewGroup(context, attrs) {
    private var mTarget: View? = null // the target of the gesture
    private var mDirection: SwipyRefreshLayoutDirection? = null
    private var mBothDirection = false
    private var mListener: OnRefreshListener? = null
    private val mTouchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private var mTotalDragDistance = -1f
    private val mMediumAnimationDuration: Int = resources.getInteger(android.R.integer.config_mediumAnimTime)
    private var mCurrentTargetOffsetTop = 0

    // Whether or not the starting offset has been determined.
    private var mOriginalOffsetCalculated = false
    private var mInitialMotionY = 0f
    private var mInitialDownY = 0f
    private var mIsBeingDragged = false
    private var mActivePointerId = INVALID_POINTER

    // Whether this item is scaled up rather than clipped
    private val mScale = false

    // Target is returning to its start offset because it was cancelled or a
    // refresh was triggered.
    private var mReturningToStart = false
    private val mDecelerateInterpolator: DecelerateInterpolator = DecelerateInterpolator(DECELERATE_INTERPOLATION_FACTOR)
    private var mCircleViewIndex = -1
    protected var mFrom = 0
    private var mStartingScale = 0f
    protected var mOriginalOffsetTop = 0
    private var mProgress: MaterialProgressDrawable? = null
    private var mScaleAnimation: Animation? = null
    private var mScaleDownAnimation: Animation? = null
    private var mAlphaStartAnimation: Animation? = null
    private var mAlphaMaxAnimation: Animation? = null
    private var mScaleDownToStartAnimation: Animation? = null
    private val mSpinnerFinalOffset: Float
    private var mNotify = false
    private var mCircleWidth: Int
    private var mCircleHeight: Int

    /**
     * @return Whether the SwipeRefreshWidget is actively showing refresh
     * progress.
     *//* notify *//* requires update */// scale and show
    /**
     * Notify the widget that refresh state has changed. Do not call this when
     * refresh is triggered by a swipe gesture.
     */
    var isRefreshing = false
        set(value) {
            if (value && field != value) {
                // scale and show
                field = value
                val endTarget = if (!mUsingCustomStart) {
                    when (mDirection) {
                        SwipyRefreshLayoutDirection.BOTTOM -> measuredHeight - mSpinnerFinalOffset.toInt()
                        SwipyRefreshLayoutDirection.TOP -> (mSpinnerFinalOffset - abs(mOriginalOffsetTop)).toInt()
                        else -> (mSpinnerFinalOffset - abs(mOriginalOffsetTop)).toInt()
                    }
                } else {
                    mSpinnerFinalOffset.toInt()
                }
                setTargetOffsetTopAndBottom(endTarget - mCurrentTargetOffsetTop,
                        true /* requires update */)
                mNotify = false
                startScaleUpAnimation(mRefreshListener)
            } else {
                setRefreshing(value, false /* notify */)
            }
        }

    var circleView: CircleImageView? = null
        private set

    // Whether the client has set a custom starting position;
    private val mUsingCustomStart = false
    private val mRefreshListener: AnimationListener = object : AnimationListener {
        override fun onAnimationStart(animation: Animation) {}
        override fun onAnimationRepeat(animation: Animation) {}
        override fun onAnimationEnd(animation: Animation) {
            if (isRefreshing) {
                // Make sure the progress view is fully visible
                mProgress!!.alpha = MAX_ALPHA
                mProgress!!.start()
                if (mNotify) {
                    if (mListener != null) {
                        mListener!!.onRefresh(mDirection)
                    }
                }
            } else {
                mProgress?.stop()
                circleView?.visibility = GONE
                // Return the circle to its start position
                if (mScale) {
                    setAnimationProgress(0f)
                } else {
                    setTargetOffsetTopAndBottom(mOriginalOffsetTop - mCurrentTargetOffsetTop,
                            true /* requires update */)
                }
            }
            mCurrentTargetOffsetTop = circleView!!.top
        }
    }

    /**
     * Constructor that is called when inflating SwipeRefreshLayout from XML.
     *
     * @param context
     * @param attrs
     */
    /**
     * Simple constructor to use when creating a SwipeRefreshLayout from code.
     */
    init {
        setWillNotDraw(false)

        val a = context.obtainStyledAttributes(attrs, LAYOUT_ATTRS)
        isEnabled = a.getBoolean(0, true)
        a.recycle()

        val a2 = context.obtainStyledAttributes(attrs, R.styleable.SwipyRefreshLayout)
        val direction = SwipyRefreshLayoutDirection.getFromInt(a2.getInt(R.styleable.SwipyRefreshLayout_srl_direction, 0))
        if (direction != SwipyRefreshLayoutDirection.BOTH) {
            mDirection = direction
            mBothDirection = false
        } else {
            mDirection = SwipyRefreshLayoutDirection.TOP
            mBothDirection = true
        }
        a2.recycle()

        val metrics = resources.displayMetrics
        mCircleWidth = (CIRCLE_DIAMETER * metrics.density).toInt()
        mCircleHeight = (CIRCLE_DIAMETER * metrics.density).toInt()

        createProgressView()

        isChildrenDrawingOrderEnabled = true
        // the absolute offset has to take into account that the circle starts at an offset
        mSpinnerFinalOffset = DEFAULT_CIRCLE_TARGET * metrics.density
    }

    /**
     * One of DEFAULT, or LARGE.
     */
    fun setSize(size: Int) {
        if (size != MaterialProgressDrawable.LARGE && size != MaterialProgressDrawable.DEFAULT) {
            return
        }
        val metrics = resources.displayMetrics
        if (size == MaterialProgressDrawable.LARGE) {
            mCircleWidth = (CIRCLE_DIAMETER_LARGE * metrics.density).toInt()
            mCircleHeight = mCircleWidth
        } else {
            mCircleWidth = (CIRCLE_DIAMETER * metrics.density).toInt()
            mCircleHeight = mCircleWidth
        }
        // force the bounds of the progress circle inside the circle view to
        // update by setting it to null before updating its size and then
        // re-setting it
        circleView?.setImageDrawable(null)
        mProgress?.updateSizes(size)
        circleView?.setImageDrawable(mProgress)
    }

    override fun getChildDrawingOrder(childCount: Int, i: Int): Int {
        return when {
            mCircleViewIndex < 0 -> i
            i == childCount - 1 -> mCircleViewIndex // Draw the selected child last
            i >= mCircleViewIndex -> i + 1 // Move the children after the selected child earlier one
            else -> i // Keep the children before the selected child the same
        }
    }

    private fun createProgressView() {
        circleView = CircleImageView(context).apply {
            setColor(CIRCLE_BG_LIGHT)
            setImageDrawable(mProgress)
            visibility = GONE
        }
        mProgress = MaterialProgressDrawable(context, this).apply {
            setBackgroundColor(CIRCLE_BG_LIGHT)
        }
        addView(circleView)
    }

    /**
     * Set the listener to be notified when a refresh is triggered via the swipe
     * gesture.
     */
    fun setOnRefreshListener(listener: OnRefreshListener?) {
        mListener = listener
    }

    private fun startScaleUpAnimation(listener: AnimationListener?) {
        circleView?.visibility = VISIBLE
        mProgress?.alpha = MAX_ALPHA
        mScaleAnimation = object : Animation() {
            public override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                setAnimationProgress(interpolatedTime)
            }
        }
        mScaleAnimation?.duration = mMediumAnimationDuration.toLong()
        circleView?.apply {
            if (listener != null)
                setAnimationListener(listener)
            clearAnimation()
            startAnimation(mScaleAnimation)
        }
    }

    private fun setAnimationProgress(progress: Float) {
        circleView?.apply {
            scaleX = progress
            scaleY = progress
        }
    }

    private fun setRefreshing(refreshing: Boolean, notify: Boolean) {
        if (isRefreshing != refreshing) {
            mNotify = notify
            ensureTarget()
            if (isRefreshing) {
                animateOffsetToCorrectPosition(mCurrentTargetOffsetTop, mRefreshListener)
            } else {
                startScaleDownAnimation(mRefreshListener)
            }
        }
    }

    private fun startScaleDownAnimation(listener: AnimationListener?) {
        mScaleDownAnimation = object : Animation() {
            public override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                setAnimationProgress(1 - interpolatedTime)
            }
        }
        mScaleDownAnimation?.duration = SCALE_DOWN_DURATION.toLong()
        circleView?.apply {
            setAnimationListener(listener)
            clearAnimation()
            startAnimation(mScaleDownAnimation)
        }
    }

    private fun startProgressAlphaStartAnimation() {
        mAlphaStartAnimation = startAlphaAnimation(mProgress!!.alpha, STARTING_PROGRESS_ALPHA)
    }

    private fun startProgressAlphaMaxAnimation() {
        mAlphaMaxAnimation = startAlphaAnimation(mProgress!!.alpha, MAX_ALPHA)
    }

    private fun startAlphaAnimation(startingAlpha: Int, endingAlpha: Int): Animation {
        val alpha: Animation = object : Animation() {
            public override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                mProgress?.alpha = (startingAlpha + ((endingAlpha - startingAlpha)
                        * interpolatedTime)).toInt()
            }
        }
        alpha.duration = ALPHA_ANIMATION_DURATION.toLong()
        // Clear out the previous animation listeners.
        circleView?.apply {
            setAnimationListener(null)
            clearAnimation()
            startAnimation(alpha)
        }
        return alpha
    }

    /**
     * Set the background color of the progress spinner disc.
     *
     * @param colorRes Resource id of the color.
     */
    fun setProgressBackgroundColor(colorRes: Int) {
        circleView?.setBackgroundColor(colorRes)
        mProgress?.setBackgroundColor(ContextCompat.getColor(context, colorRes))
    }

    @Deprecated("Use {@link #setColorSchemeResources(int...)}", ReplaceWith("setColorSchemeResources(*colors)"))
    fun setColorScheme(vararg colors: Int) {
        setColorSchemeResources(*colors)
    }

    /**
     * Set the color resources used in the progress animation from color resources.
     * The first color will also be the color of the bar that grows in response
     * to a user swipe gesture.
     *
     * @param colorResIds
     */
    fun setColorSchemeResources(vararg colorResIds: Int) {
        val colorRes = IntArray(colorResIds.size)
        for (i in colorResIds.indices) {
            colorRes[i] = ContextCompat.getColor(context, colorResIds[i])
        }
        setColorSchemeColors(*colorRes)
    }

    /**
     * Set the colors used in the progress animation. The first
     * color will also be the color of the bar that grows in response to a user
     * swipe gesture.
     *
     * @param colors
     */
    fun setColorSchemeColors(vararg colors: Int) {
        ensureTarget()
        mProgress?.setColorSchemeColors(*colors)
    }

    private fun ensureTarget() {
        // Don't bother getting the parent height if the parent hasn't been laid
        // out yet.
        if (mTarget == null) {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child != circleView) {
                    mTarget = child
                    break
                }
            }
        }
        if (mTotalDragDistance == -1f) {
            if (parent != null && (parent as View).height > 0) {
                val metrics = resources.displayMetrics
                mTotalDragDistance = ((parent as View).height * MAX_SWIPE_DISTANCE_FACTOR)
                        .coerceAtMost(REFRESH_TRIGGER_DISTANCE * metrics.density)
            }
        }
    }

    /**
     * Set the distance to trigger a sync in dips
     *
     * @param distance
     */
    fun setDistanceToTriggerSync(distance: Int) {
        mTotalDragDistance = distance.toFloat()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = measuredWidth
        val height = measuredHeight
        if (childCount == 0) {
            return
        }
        if (mTarget == null) {
            ensureTarget()
        }
        if (mTarget == null) {
            return
        }
        val child: View? = mTarget
        val childLeft = paddingLeft
        val childTop = paddingTop
        val childWidth = width - paddingLeft - paddingRight
        val childHeight = height - paddingTop - paddingBottom
        child?.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight)
        val circleWidth = circleView!!.measuredWidth
        val circleHeight = circleView!!.measuredHeight
        circleView?.layout(width / 2 - circleWidth / 2, mCurrentTargetOffsetTop,
                width / 2 + circleWidth / 2, mCurrentTargetOffsetTop + circleHeight)
    }

    public override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (mTarget == null) {
            ensureTarget()
        }
        if (mTarget == null) {
            return
        }
        mTarget?.measure(MeasureSpec.makeMeasureSpec(
                measuredWidth - paddingLeft - paddingRight,
                MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(
                measuredHeight - paddingTop - paddingBottom, MeasureSpec.EXACTLY))
        circleView?.measure(MeasureSpec.makeMeasureSpec(mCircleWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(mCircleHeight, MeasureSpec.EXACTLY))
        if (!mUsingCustomStart && !mOriginalOffsetCalculated) {
            mOriginalOffsetCalculated = true
            when (mDirection) {
                SwipyRefreshLayoutDirection.BOTTOM -> {
                    mOriginalOffsetTop = measuredHeight
                    mCurrentTargetOffsetTop = mOriginalOffsetTop
                }
                SwipyRefreshLayoutDirection.TOP -> {
                    mOriginalOffsetTop = -circleView!!.measuredHeight
                    mCurrentTargetOffsetTop = mOriginalOffsetTop
                }
                else -> {
                    mOriginalOffsetTop = -circleView!!.measuredHeight
                    mCurrentTargetOffsetTop = mOriginalOffsetTop
                }
            }
        }
        mCircleViewIndex = -1
        // Get the index of the circleview.
        for (index in 0 until childCount) {
            if (getChildAt(index) == circleView) {
                mCircleViewIndex = index
                break
            }
        }
    }

    /**
     * @return Whether it is possible for the child view of this layout to
     * scroll up. Override this if the child view is a custom view.
     */
    fun canChildScrollUp(): Boolean {
        return mTarget?.canScrollVertically(-1) ?: false
    }

    //    public boolean canChildScrollUp() {
    //        if (android.os.Build.VERSION.SDK_INT < 14) {
    //            if (mTarget instanceof AbsListView) {
    //                final AbsListView absListView = (AbsListView) mTarget;
    //                if (absListView.getLastVisiblePosition() + 1 == absListView.getCount()) {
    //                    int lastIndex = absListView.getLastVisiblePosition() - absListView.getFirstVisiblePosition();
    //
    //                    boolean res = absListView.getChildAt(lastIndex).getBottom() == absListView.getPaddingBottom();
    //
    //                    return res;
    //                }
    //                return true;
    //            } else {
    //                return mTarget.getScrollY() > 0;
    //            }
    //        } else {
    //            return ViewCompat.canScrollVertically(mTarget, 1);
    //        }
    //    }
    fun canChildScrollDown(): Boolean {
        return mTarget?.canScrollVertically(1) ?: false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        ensureTarget()
        val action: Int = ev.action
        if (mReturningToStart && action == MotionEvent.ACTION_DOWN) {
            mReturningToStart = false
        }
        when (mDirection) {
            SwipyRefreshLayoutDirection.BOTTOM -> if (!isEnabled || mReturningToStart || !mBothDirection && canChildScrollDown() || this.isRefreshing) {
                // Fail fast if we're not in a state where a swipe is possible
                return false
            }
            SwipyRefreshLayoutDirection.TOP -> if (!isEnabled || mReturningToStart || !mBothDirection && canChildScrollUp() || this.isRefreshing) {
                // Fail fast if we're not in a state where a swipe is possible
                return false
            }
            else -> if (!isEnabled || mReturningToStart || !mBothDirection && canChildScrollUp() || this.isRefreshing) {
                return false
            }
        }
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                setTargetOffsetTopAndBottom(mOriginalOffsetTop - circleView!!.top, true)
                mActivePointerId = ev.getPointerId(0)
                mIsBeingDragged = false
                val initialDownY = getMotionEventY(ev, mActivePointerId)
                if (initialDownY == -1f) {
                    return false
                }
                mInitialDownY = initialDownY
                if (mActivePointerId == INVALID_POINTER) {
                    return false
                }
                val y = getMotionEventY(ev, mActivePointerId)
                if (y == -1f) {
                    return false
                }
                if (mBothDirection) {
                    if (y > mInitialDownY) {
                        setRawDirection(SwipyRefreshLayoutDirection.TOP)
                    } else if (y < mInitialDownY) {
                        setRawDirection(SwipyRefreshLayoutDirection.BOTTOM)
                    }
                    if (mDirection === SwipyRefreshLayoutDirection.BOTTOM && canChildScrollDown()
                            || mDirection === SwipyRefreshLayoutDirection.TOP && canChildScrollUp()) {
                        mInitialDownY = y
                        return false
                    }
                }
                val yDiff: Float = when (mDirection) {
                    SwipyRefreshLayoutDirection.BOTTOM -> mInitialDownY - y
                    SwipyRefreshLayoutDirection.TOP -> y - mInitialDownY
                    else -> y - mInitialDownY
                }
                if (yDiff > mTouchSlop && !mIsBeingDragged) {
                    mInitialMotionY = when (mDirection) {
                        SwipyRefreshLayoutDirection.BOTTOM -> mInitialDownY - mTouchSlop
                        SwipyRefreshLayoutDirection.TOP -> mInitialDownY + mTouchSlop
                        else -> mInitialDownY + mTouchSlop
                    }
                    mIsBeingDragged = true
                    mProgress!!.alpha = STARTING_PROGRESS_ALPHA
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (mActivePointerId == INVALID_POINTER) {
                    return false
                }
                val y = getMotionEventY(ev, mActivePointerId)
                if (y == -1f) {
                    return false
                }
                if (mBothDirection) {
                    if (y > mInitialDownY) {
                        setRawDirection(SwipyRefreshLayoutDirection.TOP)
                    } else if (y < mInitialDownY) {
                        setRawDirection(SwipyRefreshLayoutDirection.BOTTOM)
                    }
                    if (mDirection === SwipyRefreshLayoutDirection.BOTTOM && canChildScrollDown()
                            || mDirection === SwipyRefreshLayoutDirection.TOP && canChildScrollUp()) {
                        mInitialDownY = y
                        return false
                    }
                }
                val yDiff: Float = when (mDirection) {
                    SwipyRefreshLayoutDirection.BOTTOM -> mInitialDownY - y
                    SwipyRefreshLayoutDirection.TOP -> y - mInitialDownY
                    else -> y - mInitialDownY
                }
                if (yDiff > mTouchSlop && !mIsBeingDragged) {
                    mInitialMotionY = when (mDirection) {
                        SwipyRefreshLayoutDirection.BOTTOM -> mInitialDownY - mTouchSlop
                        SwipyRefreshLayoutDirection.TOP -> mInitialDownY + mTouchSlop
                        else -> mInitialDownY + mTouchSlop
                    }
                    mIsBeingDragged = true
                    mProgress!!.alpha = STARTING_PROGRESS_ALPHA
                }
            }
            MotionEvent.ACTION_POINTER_UP -> onSecondaryPointerUp(ev)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mIsBeingDragged = false
                mActivePointerId = INVALID_POINTER
            }
        }
        return mIsBeingDragged
    }

    private fun getMotionEventY(ev: MotionEvent, activePointerId: Int): Float {
        val index: Int = ev.findPointerIndex(activePointerId)
        return if (index < 0) (-1).toFloat() else ev.getY(index)
    }

    override fun requestDisallowInterceptTouchEvent(b: Boolean) {
        // Nope.
    }

    private fun isAnimationRunning(animation: Animation?): Boolean {
        return animation != null && animation.hasStarted() && !animation.hasEnded()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        try {
            val action: Int = ev.action
            if (mReturningToStart && action == MotionEvent.ACTION_DOWN) {
                mReturningToStart = false
            }
            when (mDirection) {
                SwipyRefreshLayoutDirection.BOTTOM -> if (!isEnabled || mReturningToStart || canChildScrollDown() || this.isRefreshing) {
                    // Fail fast if we're not in a state where a swipe is possible
                    return false
                }
                SwipyRefreshLayoutDirection.TOP -> if (!isEnabled || mReturningToStart || canChildScrollUp() || this.isRefreshing) {
                    // Fail fast if we're not in a state where a swipe is possible
                    return false
                }
                else -> if (!isEnabled || mReturningToStart || canChildScrollUp() || this.isRefreshing) {
                    return false
                }
            }
            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    mActivePointerId = ev.getPointerId(0)
                    mIsBeingDragged = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val pointerIndex: Int = ev.findPointerIndex(mActivePointerId)
                    if (pointerIndex < 0) {
                        return false
                    }
                    val y: Float = ev.getY(pointerIndex)
                    val overScrollTop: Float = when (mDirection) {
                        SwipyRefreshLayoutDirection.BOTTOM -> (mInitialMotionY - y) * DRAG_RATE
                        SwipyRefreshLayoutDirection.TOP -> (y - mInitialMotionY) * DRAG_RATE
                        else -> (y - mInitialMotionY) * DRAG_RATE
                    }
                    if (mIsBeingDragged) {
                        mProgress!!.showArrow(true)
                        val originalDragPercent = overScrollTop / mTotalDragDistance
                        if (originalDragPercent < 0) {
                            return false
                        }
                        val dragPercent = 1f.coerceAtMost(abs(originalDragPercent))
                        val adjustedPercent = (dragPercent - .4).coerceAtLeast(0.0).toFloat() * 5 / 3
                        val extraOS = abs(overScrollTop) - mTotalDragDistance
                        val slingshotDist = if (mUsingCustomStart)
                            mSpinnerFinalOffset - mOriginalOffsetTop
                        else mSpinnerFinalOffset
                        val tensionSlingshotPercent = 0f.coerceAtLeast(extraOS.coerceAtMost(slingshotDist * 2) / slingshotDist)
                        val tensionPercent = (tensionSlingshotPercent / 4 - (tensionSlingshotPercent / 4).toDouble().pow(2.0)).toFloat() * 2f
                        val extraMove = slingshotDist * tensionPercent * 2

                        // int targetY = mOriginalOffsetTop + (int) ((slingshotDist * dragPercent) + extraMove);
                        val targetY: Int = if (mDirection === SwipyRefreshLayoutDirection.TOP) {
                            mOriginalOffsetTop + (slingshotDist * dragPercent + extraMove).toInt()
                        } else {
                            mOriginalOffsetTop - (slingshotDist * dragPercent + extraMove).toInt()
                        }
                        // where 1.0f is a full circle
                        if (circleView!!.visibility != VISIBLE) {
                            circleView!!.visibility = VISIBLE
                        }
                        if (!mScale) {
                            circleView?.apply {
                                scaleX = 1f
                                scaleY = 1f
                            }
                        }
                        if (overScrollTop < mTotalDragDistance) {
                            if (mScale) {
                                setAnimationProgress(overScrollTop / mTotalDragDistance)
                            }
                            mProgress?.apply {
                                if (alpha > STARTING_PROGRESS_ALPHA && !isAnimationRunning(mAlphaStartAnimation)) {
                                    // Animate the alpha
                                    startProgressAlphaStartAnimation()
                                }
                                setStartEndTrim(0f, MAX_PROGRESS_ANGLE.coerceAtMost((adjustedPercent * .8f)))
                                setArrowScale(1f.coerceAtMost(adjustedPercent))
                            }
                        } else {
                            if (mProgress!!.alpha < MAX_ALPHA
                                    && !isAnimationRunning(mAlphaMaxAnimation)) {
                                // Animate the alpha
                                startProgressAlphaMaxAnimation()
                            }
                        }
                        val rotation = (-0.25f + .4f * adjustedPercent + tensionPercent * 2) * .5f
                        mProgress?.setProgressRotation(rotation)
                        setTargetOffsetTopAndBottom(targetY - mCurrentTargetOffsetTop,
                                true /* requires update */)
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    val index: Int = ev.actionIndex
                    mActivePointerId = ev.getPointerId(index)
                }
                MotionEvent.ACTION_POINTER_UP -> onSecondaryPointerUp(ev)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (mActivePointerId == INVALID_POINTER) {
                        return false
                    }
                    val pointerIndex: Int = ev.findPointerIndex(mActivePointerId)
                    val y: Float = ev.getY(pointerIndex)
                    val overScrollTop: Float = when (mDirection) {
                        SwipyRefreshLayoutDirection.BOTTOM -> (mInitialMotionY - y) * DRAG_RATE
                        SwipyRefreshLayoutDirection.TOP -> (y - mInitialMotionY) * DRAG_RATE
                        else -> (y - mInitialMotionY) * DRAG_RATE
                    }
                    mIsBeingDragged = false
                    if (overScrollTop > mTotalDragDistance) {
                        setRefreshing(refreshing = true, notify = true /* notify */)
                    } else {
                        // cancel refresh
                        this.isRefreshing = false
                        mProgress?.setStartEndTrim(0f, 0f)
                        var listener: AnimationListener? = null
                        if (!mScale) {
                            listener = object : AnimationListener {
                                override fun onAnimationStart(animation: Animation) {}
                                override fun onAnimationEnd(animation: Animation) {
                                    if (!mScale) {
                                        startScaleDownAnimation(null)
                                    }
                                }

                                override fun onAnimationRepeat(animation: Animation) {}
                            }
                        }
                        animateOffsetToStartPosition(mCurrentTargetOffsetTop, listener)
                        mProgress!!.showArrow(false)
                    }
                    mActivePointerId = INVALID_POINTER
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "An exception occured during SwipyRefreshLayout onTouchEvent $e")
        }
        return true
    }

    private fun animateOffsetToCorrectPosition(from: Int, listener: AnimationListener?) {
        mFrom = from
        mAnimateToCorrectPosition.reset()
        mAnimateToCorrectPosition.duration = ANIMATE_TO_TRIGGER_DURATION.toLong()
        mAnimateToCorrectPosition.interpolator = mDecelerateInterpolator
        if (listener != null) {
            circleView!!.setAnimationListener(listener)
        }
        circleView!!.clearAnimation()
        circleView!!.startAnimation(mAnimateToCorrectPosition)
    }

    private fun animateOffsetToStartPosition(from: Int, listener: AnimationListener?) {
        if (mScale) {
            // Scale the item back down
            startScaleDownReturnToStartAnimation(from, listener)
        } else {
            mFrom = from
            mAnimateToStartPosition.reset()
            mAnimateToStartPosition.duration = ANIMATE_TO_START_DURATION.toLong()
            mAnimateToStartPosition.interpolator = mDecelerateInterpolator
            if (listener != null) {
                circleView!!.setAnimationListener(listener)
            }
            circleView!!.clearAnimation()
            circleView!!.startAnimation(mAnimateToStartPosition)
        }
    }

    private val mAnimateToCorrectPosition: Animation = object : Animation() {
        public override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            val endTarget: Int = if (!mUsingCustomStart) {
                when (mDirection) {
                    SwipyRefreshLayoutDirection.BOTTOM -> measuredHeight - mSpinnerFinalOffset.toInt()
                    SwipyRefreshLayoutDirection.TOP -> (mSpinnerFinalOffset - abs(mOriginalOffsetTop)).toInt()
                    else -> (mSpinnerFinalOffset - abs(mOriginalOffsetTop)).toInt()
                }
            } else {
                mSpinnerFinalOffset.toInt()
            }
            val targetTop: Int = mFrom + ((endTarget - mFrom) * interpolatedTime).toInt()
            val offset = targetTop - circleView!!.top
            setTargetOffsetTopAndBottom(offset, false /* requires update */)
        }
    }

    private fun moveToStart(interpolatedTime: Float) {
        val targetTop: Int = mFrom + ((mOriginalOffsetTop - mFrom) * interpolatedTime).toInt()
        val offset = targetTop - circleView!!.top
        setTargetOffsetTopAndBottom(offset, false /* requires update */)
    }

    private val mAnimateToStartPosition: Animation = object : Animation() {
        public override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            moveToStart(interpolatedTime)
        }
    }

    private fun startScaleDownReturnToStartAnimation(from: Int, listener: AnimationListener?) {
        mFrom = from
        mStartingScale = circleView?.scaleX ?: 0f
        mScaleDownToStartAnimation = object : Animation() {
            public override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                val targetScale = mStartingScale + -mStartingScale * interpolatedTime
                setAnimationProgress(targetScale)
                moveToStart(interpolatedTime)
            }
        }
        mScaleDownToStartAnimation?.duration = SCALE_DOWN_DURATION.toLong()
        circleView?.apply {
            if (listener != null)
                setAnimationListener(listener)
            clearAnimation()
            startAnimation(mScaleDownToStartAnimation)
        }
    }

    private fun setTargetOffsetTopAndBottom(offset: Int, requiresUpdate: Boolean) {
        circleView?.apply {
            bringToFront()
            offsetTopAndBottom(offset)
        }
        mCurrentTargetOffsetTop = circleView!!.top
    }

    private fun onSecondaryPointerUp(ev: MotionEvent) {
        val pointerIndex: Int = ev.actionIndex
        val pointerId: Int = ev.getPointerId(pointerIndex)
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            val newPointerIndex = if (pointerIndex == 0) 1 else 0
            mActivePointerId = ev.getPointerId(newPointerIndex)
        }
    }

    /**
     * Classes that wish to be notified when the swipe gesture correctly
     * triggers a refresh should implement this interface.
     */
    interface OnRefreshListener {
        fun onRefresh(direction: SwipyRefreshLayoutDirection?)
    }

    fun getDirection(): SwipyRefreshLayoutDirection {
        return if (mBothDirection) SwipyRefreshLayoutDirection.BOTH else mDirection!!
    }

    fun setDirection(direction: SwipyRefreshLayoutDirection) {
        if (direction === SwipyRefreshLayoutDirection.BOTH) {
            mBothDirection = true
        } else {
            mBothDirection = false
            mDirection = direction
        }
        when (mDirection) {
            SwipyRefreshLayoutDirection.BOTTOM -> {
                mOriginalOffsetTop = measuredHeight
                mCurrentTargetOffsetTop = mOriginalOffsetTop
            }
            SwipyRefreshLayoutDirection.TOP -> {
                mOriginalOffsetTop = -circleView!!.measuredHeight
                mCurrentTargetOffsetTop = mOriginalOffsetTop
            }
            else -> {
                mOriginalOffsetTop = -circleView!!.measuredHeight
                mCurrentTargetOffsetTop = mOriginalOffsetTop
            }
        }
    }

    // only TOP or Bottom
    private fun setRawDirection(direction: SwipyRefreshLayoutDirection) {
        if (mDirection === direction) {
            return
        }
        mDirection = direction
        when (mDirection) {
            SwipyRefreshLayoutDirection.BOTTOM -> {
                mOriginalOffsetTop = measuredHeight
                mCurrentTargetOffsetTop = mOriginalOffsetTop
            }
            SwipyRefreshLayoutDirection.TOP -> {
                mOriginalOffsetTop = -circleView!!.measuredHeight
                mCurrentTargetOffsetTop = mOriginalOffsetTop
            }
            else -> {
                mOriginalOffsetTop = -circleView!!.measuredHeight
                mCurrentTargetOffsetTop = mOriginalOffsetTop
            }
        }
    }

    companion object {
        // Maps to ProgressBar default style
        const val DEFAULT = MaterialProgressDrawable.DEFAULT

        // Maps to ProgressBar.Large style
        const val LARGE = MaterialProgressDrawable.LARGE
    }
}