package com.zhenxiang.blur

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Path
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import com.zhenxiang.blur.model.CornersRadius
import java.util.function.Consumer
import kotlin.math.roundToInt

class SystemBlurController(
    private val view: View,
    @ColorInt backgroundColour: Int = Color.TRANSPARENT,
    blurBackgroundColourOpacity: Float = DEFAULT_BLUR_BACKGROUND_COLOUR_OPACITY,
    blurRadius: Int = DEFAULT_BLUR_RADIUS,
    cornerRadius: CornersRadius = CornersRadius.all(0f),
): View.OnAttachStateChangeListener {

    private var windowManager: WindowManager? = null
    private val crossWindowBlurListener = Consumer<Boolean> {
        blurEnabled = it
    }

    private var blurEnabled: Boolean = false
    set(value) {
        if (value != field) {
            field = value
            updateBackgroundColour()
        }
    }

    @ColorInt
    var backgroundColour = backgroundColour
    set (value) {
        field = value
        updateBackgroundColour()
    }
    // Opacity applied to the background colour when blur is available
    var blurBackgroundColourOpacity = blurBackgroundColourOpacity
    set(value) {
        field = value
        updateBackgroundColour()
    }
    var blurRadius = blurRadius
    set(value) {
        field = value

        val bg = view.background
        when (bg) {
            is BackgroundBlurDrawable -> bg.setBlurRadius(value)
        }
    }
    var cornerRadius = cornerRadius
    set(value) {
        field = value

        val bg = view.background
        when (bg) {
            is BackgroundBlurDrawable -> setCornerRadius(bg, value)
            is ShapeDrawable -> bg.shape = getShapeFromCorners(value)
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // On api 31 and above background init is done in onViewAttachedToWindow
            view.addOnAttachStateChangeListener(this)
        } else {
            // On pre api 31 init background here
            val shape = ShapeDrawable()
            shape.shape = getShapeFromCorners(cornerRadius)
            shape.paint.color = backgroundColour

            view.background = shape
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onViewAttachedToWindow(_v: View) {
        windowManager = getWindowManager(view.context)
        windowManager?.addCrossWindowBlurEnabledListener(crossWindowBlurListener)

        view.createBackgroundBlurDrawable()?.let {
            // Configure blur drawable with current values
            it.setColor(
                if (blurEnabled) applyOpacityToColour(backgroundColour, blurBackgroundColourOpacity) else backgroundColour
            )
            it.setBlurRadius(blurRadius)
            setCornerRadius(it, cornerRadius)

            view.background = it
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onViewDetachedFromWindow(_v: View) {
        // Clear blur drawable
        if (view.background is BackgroundBlurDrawable) {
            view.background = null
        }

        windowManager?.removeCrossWindowBlurEnabledListener(crossWindowBlurListener)
        windowManager = null
    }

    private fun updateBackgroundColour() {
        val bg = view.background
        when (bg) {
            is BackgroundBlurDrawable -> {
                bg.setColor(
                    if (blurEnabled) applyOpacityToColour(backgroundColour, blurBackgroundColourOpacity) else backgroundColour
                )
            }
            is ShapeDrawable -> bg.paint.color = backgroundColour
        }
    }

    private fun setCornerRadius(blurDrawable: BackgroundBlurDrawable, corners: CornersRadius) {
        blurDrawable.setCornerRadius(
            corners.topLeft, corners.topRight, corners.bottomLeft, corners.bottomRight)

        view.outlineProvider = getViewOutlineProvider(corners)
    }

    private fun getShapeFromCorners(corners: CornersRadius): RoundRectShape {
        return RoundRectShape(getCornersFloatArray(corners), null, null)
    }

    private fun getCornersFloatArray(corners: CornersRadius): FloatArray {
        return floatArrayOf(
            corners.topLeft,
            corners.topLeft,
            corners.topRight,
            corners.topRight,
            corners.bottomRight,
            corners.bottomRight,
            corners.bottomLeft,
            corners.bottomLeft,
        )
    }

    private fun getViewOutlineProvider(corners: CornersRadius): ViewOutlineProvider {
        return object: ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {

                val radius = getCornersFloatArray(corners)
                val path = Path()
                path.addRoundRect(
                    0f, 0f, view.width.toFloat(), view.height.toFloat(), radius, Path.Direction.CW
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    outline.setPath(path)
                } else {
                    outline.setConvexPath(path)
                }
            }
        }
    }

    private fun getWindowManager(context: Context): WindowManager {
        return context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    companion object {
        const val DEFAULT_BLUR_BACKGROUND_COLOUR_OPACITY = 0.7f
        const val DEFAULT_BLUR_RADIUS = 25

        @ColorInt
        fun applyOpacityToColour(@ColorInt colour: Int, opacity: Float): Int {
            val targetAlpha = Color.alpha(colour) * opacity
            return ColorUtils.setAlphaComponent(colour, targetAlpha.roundToInt())
        }
    }
}