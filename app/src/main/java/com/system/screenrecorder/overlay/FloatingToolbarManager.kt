package com.system.screenrecorder.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.system.screenrecorder.R
import java.util.Locale

class FloatingToolbarManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var toolbarView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var tvTimer: TextView? = null
    private var ivPauseResume: ImageView? = null
    private var ivDoodle: ImageView? = null
    private var ivStop: ImageView? = null
    private var redDot: View? = null

    var onPauseResumeListener: (() -> Unit)? = null
    var onStopListener: (() -> Unit)? = null
    var onDoodleToggleListener: (() -> Unit)? = null

    val isShowing: Boolean
        get() = toolbarView != null

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    fun showToolbar() {
        if (toolbarView != null) return

        val density = context.resources.displayMetrics.density

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (20 * density).toInt()
            y = (100 * density).toInt()
        }
        layoutParams = params

        // Main Pill Container (Samsung One UI Dark Capsule)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = (12 * density).toInt()
            val padV = (6 * density).toInt()
            setPadding(padH, padV, padH, padV)

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 26 * density
                setColor(Color.argb(235, 26, 26, 28)) // One UI dark surface
                setStroke((1 * density).toInt(), Color.argb(45, 255, 255, 255))
            }
            background = bg
            elevation = 16 * density
        }

        // --- Left Section: Drag Handle & Timer ---
        val dragHandle = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padR = (10 * density).toInt()
            setPadding(0, 0, padR, 0)
        }

        // Red Recording Dot
        val dot = View(context).apply {
            val size = (8 * density).toInt()
            val lp = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = (6 * density).toInt()
            }
            this.layoutParams = lp
            val dotBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF3B30"))
            }
            background = dotBg
        }
        redDot = dot
        dragHandle.addView(dot)

        // Timer Text
        val timer = TextView(context).apply {
            text = "00:00"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }
        tvTimer = timer
        dragHandle.addView(timer)

        container.addView(dragHandle)

        // Vertical Divider between drag handle and buttons
        val div1 = View(context).apply {
            val lp = LinearLayout.LayoutParams((1 * density).toInt(), (18 * density).toInt()).apply {
                marginEnd = (8 * density).toInt()
            }
            this.layoutParams = lp
            setBackgroundColor(Color.argb(60, 255, 255, 255))
        }
        container.addView(div1)

        // --- Right Section: Action Buttons (Doodle, Pause/Play, Stop) ---
        val btnSizePx = (36 * density).toInt()
        val iconPadPx = (7 * density).toInt()

        // 1. Pen / Doodle Button (compose.png -> ic_tool_doodle)
        val doodleBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_tool_doodle)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(iconPadPx, iconPadPx, iconPadPx, iconPadPx)
            val lp = LinearLayout.LayoutParams(btnSizePx, btnSizePx).apply {
                marginEnd = (4 * density).toInt()
            }
            this.layoutParams = lp
            setOnClickListener {
                onDoodleToggleListener?.invoke()
            }
        }
        ivDoodle = doodleBtn
        container.addView(doodleBtn)

        // 2. Pause / Resume Button (pause.png / play-button.png)
        val pauseBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_tool_pause)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(iconPadPx, iconPadPx, iconPadPx, iconPadPx)
            val lp = LinearLayout.LayoutParams(btnSizePx, btnSizePx).apply {
                marginEnd = (4 * density).toInt()
            }
            this.layoutParams = lp
            setOnClickListener {
                onPauseResumeListener?.invoke()
            }
        }
        ivPauseResume = pauseBtn
        container.addView(pauseBtn)

        // 3. Stop Button (stop-button.png -> ic_tool_stop)
        val stopBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_tool_stop)
            setColorFilter(Color.parseColor("#FF453A")) // One UI vibrant red tint
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(iconPadPx, iconPadPx, iconPadPx, iconPadPx)
            val lp = LinearLayout.LayoutParams(btnSizePx, btnSizePx)
            this.layoutParams = lp
            setOnClickListener {
                onStopListener?.invoke()
            }
        }
        ivStop = stopBtn
        container.addView(stopBtn)

        // Dedicated Drag Listener on dragHandle (does NOT block buttons!)
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(dx) > 6 || kotlin.math.abs(dy) > 6) {
                        isDragging = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try {
                            windowManager.updateViewLayout(container, params)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(container, params)
            toolbarView = container
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateDuration(seconds: Long) {
        val minutes = seconds / 60
        val remainingSecs = seconds % 60
        val timeString = String.format(Locale.getDefault(), "%02d:%02d", minutes, remainingSecs)
        tvTimer?.text = timeString
    }

    fun updatePausedState(isPaused: Boolean) {
        ivPauseResume?.setImageResource(
            if (isPaused) R.drawable.ic_tool_play else R.drawable.ic_tool_pause
        )
        redDot?.visibility = if (isPaused) View.INVISIBLE else View.VISIBLE
    }

    fun dismiss() {
        toolbarView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            toolbarView = null
        }
    }
}
