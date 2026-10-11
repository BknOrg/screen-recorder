package com.system.screenrecorder.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class DoodleOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootOverlay: FrameLayout? = null
    private var doodleView: ScreenDoodleView? = null

    val isDoodleActive: Boolean
        get() = rootOverlay != null

    var onCloseListener: (() -> Unit)? = null

    @SuppressLint("SetTextI18n")
    fun showDoodleOverlay() {
        if (rootOverlay != null) return

        val windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        val canvas = ScreenDoodleView(context).apply {
            this.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        doodleView = canvas
        root.addView(canvas)

        // Floating One UI Doodle Palette at Top
        val paletteContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = (14 * context.resources.displayMetrics.density).toInt()
            val padV = (8 * context.resources.displayMetrics.density).toInt()
            setPadding(padH, padV, padH, padV)

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24 * context.resources.displayMetrics.density
                setColor(Color.argb(230, 28, 28, 30)) // One UI dark surface
                setStroke((1 * context.resources.displayMetrics.density).toInt(), Color.argb(40, 255, 255, 255))
            }
            background = bg

            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply {
                topMargin = (50 * context.resources.displayMetrics.density).toInt()
            }
            this.layoutParams = lp
        }

        // Colors: Red, Yellow, Green, Blue, White, Black
        val colors = listOf(
            Color.parseColor("#FF3B30"), // Red
            Color.parseColor("#FFCC00"), // Yellow
            Color.parseColor("#34C759"), // Green
            Color.parseColor("#007AFF"), // Blue
            Color.WHITE,
            Color.BLACK
        )

        val colorViews = mutableListOf<View>()
        for (c in colors) {
            val colorDot = View(context).apply {
                val size = (24 * context.resources.displayMetrics.density).toInt()
                val dotLp = LinearLayout.LayoutParams(size, size).apply {
                    val m = (4 * context.resources.displayMetrics.density).toInt()
                    setMargins(m, 0, m, 0)
                }
                this.layoutParams = dotLp

                val dotBg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(c)
                    setStroke(
                        (1 * context.resources.displayMetrics.density).toInt(),
                        if (c == Color.WHITE) Color.LTGRAY else Color.argb(50, 0, 0, 0)
                    )
                }
                background = dotBg

                setOnClickListener {
                    canvas.setColor(c)
                    // Visual feedback
                    for (cv in colorViews) {
                        cv.scaleX = 1.0f
                        cv.scaleY = 1.0f
                    }
                    scaleX = 1.25f
                    scaleY = 1.25f
                }
            }
            colorViews.add(colorDot)
            paletteContainer.addView(colorDot)
        }
        colorViews.firstOrNull()?.scaleX = 1.25f
        colorViews.firstOrNull()?.scaleY = 1.25f

        // Separator
        val separator = View(context).apply {
            val sepLp = LinearLayout.LayoutParams(
                (1 * context.resources.displayMetrics.density).toInt(),
                (18 * context.resources.displayMetrics.density).toInt()
            ).apply {
                val m = (8 * context.resources.displayMetrics.density).toInt()
                setMargins(m, 0, m, 0)
            }
            this.layoutParams = sepLp
            setBackgroundColor(Color.argb(70, 255, 255, 255))
        }
        paletteContainer.addView(separator)

        // Undo Button
        val undoButton = TextView(context).apply {
            text = "⤺"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            val p = (6 * context.resources.displayMetrics.density).toInt()
            setPadding(p, 0, p, 0)
            setOnClickListener {
                canvas.undo()
            }
        }
        paletteContainer.addView(undoButton)

        // Clear All Button
        val clearButton = TextView(context).apply {
            text = "Bersihkan"
            textSize = 12f
            setTextColor(Color.parseColor("#FF453A"))
            gravity = Gravity.CENTER
            val p = (8 * context.resources.displayMetrics.density).toInt()
            setPadding(p, 0, p, 0)
            setOnClickListener {
                canvas.clear()
            }
        }
        paletteContainer.addView(clearButton)

        // Close Doodle Button
        val closeButton = TextView(context).apply {
            text = "✕"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            val p = (8 * context.resources.displayMetrics.density).toInt()
            setPadding(p, 0, p, 0)
            setOnClickListener {
                dismiss()
                onCloseListener?.invoke()
            }
        }
        paletteContainer.addView(closeButton)

        root.addView(paletteContainer)

        try {
            windowManager.addView(root, windowParams)
            rootOverlay = root
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun dismiss() {
        rootOverlay?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            rootOverlay = null
            doodleView = null
        }
    }
}
