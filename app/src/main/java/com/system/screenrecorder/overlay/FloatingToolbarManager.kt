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

    enum class DockSide { LEFT, RIGHT }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var toolbarView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var tvTimer: TextView? = null
    private var ivPauseResume: ImageView? = null
    private var ivDoodle: ImageView? = null
    private var ivStop: ImageView? = null
    private var ivCollapse: ImageView? = null
    private var redDot: View? = null
    private var edgeRedDot: View? = null

    var onPauseResumeListener: (() -> Unit)? = null
    var onStopListener: (() -> Unit)? = null
    var onDoodleToggleListener: (() -> Unit)? = null

    var isPaused: Boolean = false
        private set
    var currentSeconds: Long = 0L
        private set
    var isCollapsed: Boolean = false
        private set

    private var dockSide: DockSide = DockSide.LEFT
    private var currentX: Int = (20 * context.resources.displayMetrics.density).toInt()
    private var currentY: Int = (100 * context.resources.displayMetrics.density).toInt()

    val isShowing: Boolean
        get() = toolbarView != null

    private fun getScreenWidth(): Int = context.resources.displayMetrics.widthPixels
    private fun getScreenHeight(): Int = context.resources.displayMetrics.heightPixels

    private fun createLayoutParams(width: Int, height: Int, x: Int, y: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width,
            height,
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
            this.x = x
            this.y = y
        }
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    fun showToolbar() {
        if (toolbarView != null) return
        if (isCollapsed) {
            showCollapsedToolbar()
        } else {
            showExpandedToolbar()
        }
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun showExpandedToolbar() {
        val density = context.resources.displayMetrics.density
        val screenWidth = getScreenWidth()
        val screenHeight = getScreenHeight()

        // Ensure currentX is not right at screen edge when expanded
        val minX = (12 * density).toInt()
        val maxX = (screenWidth - (250 * density).toInt()).coerceAtLeast(minX)
        if (currentX < minX || currentX > maxX) {
            currentX = if (dockSide == DockSide.LEFT) minX else maxX
        }
        val minY = (20 * density).toInt()
        val maxY = (screenHeight - (80 * density).toInt()).coerceAtLeast(minY)
        currentY = currentY.coerceIn(minY, maxY)

        val params = createLayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            currentX,
            currentY
        )
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
            visibility = if (isPaused) View.INVISIBLE else View.VISIBLE
        }
        redDot = dot
        dragHandle.addView(dot)

        // Timer Text
        val timer = TextView(context).apply {
            text = formatDuration(currentSeconds)
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

        // 1. Pen / Doodle Button
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

        // 2. Pause / Resume Button
        val pauseBtn = ImageView(context).apply {
            setImageResource(if (isPaused) R.drawable.ic_tool_play else R.drawable.ic_tool_pause)
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

        // 3. Stop Button
        val stopBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_tool_stop)
            setColorFilter(Color.parseColor("#FF453A")) // One UI vibrant red tint
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(iconPadPx, iconPadPx, iconPadPx, iconPadPx)
            val lp = LinearLayout.LayoutParams(btnSizePx, btnSizePx).apply {
                marginEnd = (4 * density).toInt()
            }
            this.layoutParams = lp
            setOnClickListener {
                onStopListener?.invoke()
            }
        }
        ivStop = stopBtn
        container.addView(stopBtn)

        // Vertical Divider between stop and collapse
        val div2 = View(context).apply {
            val lp = LinearLayout.LayoutParams((1 * density).toInt(), (18 * density).toInt()).apply {
                marginStart = (2 * density).toInt()
                marginEnd = (6 * density).toInt()
            }
            this.layoutParams = lp
            setBackgroundColor(Color.argb(60, 255, 255, 255))
        }
        container.addView(div2)

        // 4. Collapse Button (Chevron)
        val collapseBtn = ImageView(context).apply {
            val chevronRes = if (dockSide == DockSide.LEFT) R.drawable.ic_chevron_left else R.drawable.ic_chevron_right
            setImageResource(chevronRes)
            setColorFilter(Color.argb(200, 255, 255, 255))
            scaleType = ImageView.ScaleType.FIT_CENTER
            val cPad = (8 * density).toInt()
            setPadding(cPad, cPad, cPad, cPad)
            val lp = LinearLayout.LayoutParams(btnSizePx, btnSizePx)
            this.layoutParams = lp
            setOnClickListener {
                collapseToolbar()
            }
        }
        ivCollapse = collapseBtn
        container.addView(collapseBtn)

        // Drag listener on dragHandle
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
                        currentX = params.x
                        currentY = params.y

                        // Update chevron direction according to screen midpoint
                        val midX = getScreenWidth() / 2
                        val newDock = if ((params.x + (container.width / 2)) < midX) DockSide.LEFT else DockSide.RIGHT
                        if (newDock != dockSide) {
                            dockSide = newDock
                            ivCollapse?.setImageResource(
                                if (dockSide == DockSide.LEFT) R.drawable.ic_chevron_left else R.drawable.ic_chevron_right
                            )
                        }

                        try {
                            windowManager.updateViewLayout(container, params)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        // Check if dropped near screen edge to auto-collapse
                        val edgeThreshold = 35 * density
                        val containerW = container.width.takeIf { it > 0 } ?: (220 * density).toInt()
                        if (params.x < edgeThreshold) {
                            dockSide = DockSide.LEFT
                            collapseToolbar()
                        } else if (params.x > (getScreenWidth() - containerW - edgeThreshold)) {
                            dockSide = DockSide.RIGHT
                            collapseToolbar()
                        } else {
                            currentX = params.x
                            currentY = params.y
                        }
                    }
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

    @SuppressLint("ClickableViewAccessibility")
    private fun showCollapsedToolbar() {
        val density = context.resources.displayMetrics.density
        val screenWidth = getScreenWidth()
        val screenHeight = getScreenHeight()

        val handleW = (24 * density).toInt()
        val handleH = (68 * density).toInt()

        val x = if (dockSide == DockSide.LEFT) 0 else (screenWidth - handleW)
        val minY = (20 * density).toInt()
        val maxY = (screenHeight - handleH - (40 * density).toInt()).coerceAtLeast(minY)
        currentY = currentY.coerceIn(minY, maxY)

        val params = createLayoutParams(handleW, handleH, x, currentY)
        layoutParams = params

        // Samsung Edge Panel Handle View
        val edgeHandle = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                val radius = 14 * density
                cornerRadii = if (dockSide == DockSide.LEFT) {
                    floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)
                } else {
                    floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
                }
                setColor(Color.argb(235, 26, 26, 28)) // One UI dark surface
                setStroke((1 * density).toInt(), Color.argb(45, 255, 255, 255))
            }
            background = bg
            elevation = 16 * density
        }

        // Red Recording Dot inside Edge Handle
        val dot = View(context).apply {
            val size = (7 * density).toInt()
            val lp = LinearLayout.LayoutParams(size, size).apply {
                bottomMargin = (6 * density).toInt()
            }
            this.layoutParams = lp
            val dotBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF3B30"))
            }
            background = dotBg
            visibility = if (isPaused) View.INVISIBLE else View.VISIBLE
        }
        edgeRedDot = dot
        edgeHandle.addView(dot)

        // Chevron Arrow inside Edge Handle pointing towards center of screen
        val chevron = ImageView(context).apply {
            val chevronRes = if (dockSide == DockSide.LEFT) R.drawable.ic_chevron_right else R.drawable.ic_chevron_left
            setImageResource(chevronRes)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val cSize = (14 * density).toInt()
            val lp = LinearLayout.LayoutParams(cSize, cSize)
            this.layoutParams = lp
        }
        edgeHandle.addView(chevron)

        // Touch handling: Drag up/down along edge OR tap/swipe away to expand
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        edgeHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (kotlin.math.abs(dy) > 8) {
                        isDragging = true
                        params.y = (initialY + dy).coerceIn(minY, maxY)
                        currentY = params.y
                        try {
                            windowManager.updateViewLayout(edgeHandle, params)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else if ((dockSide == DockSide.LEFT && dx > 20 * density) ||
                        (dockSide == DockSide.RIGHT && dx < -20 * density)
                    ) {
                        // Swipe outward triggers expand
                        expandToolbar()
                        return@setOnTouchListener true
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // Tap on edge handle triggers expand
                        expandToolbar()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(edgeHandle, params)
            toolbarView = edgeHandle
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun collapseToolbar() {
        if (toolbarView == null || isCollapsed) return
        val density = context.resources.displayMetrics.density
        val screenWidth = getScreenWidth()
        val pillWidth = toolbarView?.width?.takeIf { it > 0 } ?: (220 * density).toInt()
        val centerX = currentX + pillWidth / 2
        dockSide = if (centerX < screenWidth / 2) DockSide.LEFT else DockSide.RIGHT

        dismiss()
        isCollapsed = true
        showCollapsedToolbar()
    }

    fun expandToolbar() {
        if (toolbarView == null || !isCollapsed) return
        dismiss()
        isCollapsed = false
        showExpandedToolbar()
    }

    fun updateDuration(seconds: Long) {
        currentSeconds = seconds
        tvTimer?.text = formatDuration(seconds)
    }

    fun updatePausedState(isPaused: Boolean) {
        this.isPaused = isPaused
        ivPauseResume?.setImageResource(
            if (isPaused) R.drawable.ic_tool_play else R.drawable.ic_tool_pause
        )
        redDot?.visibility = if (isPaused) View.INVISIBLE else View.VISIBLE
        edgeRedDot?.visibility = if (isPaused) View.INVISIBLE else View.VISIBLE
    }

    private fun formatDuration(seconds: Long): String {
        val minutes = seconds / 60
        val remainingSecs = seconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, remainingSecs)
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
        tvTimer = null
        ivPauseResume = null
        ivDoodle = null
        ivStop = null
        ivCollapse = null
        redDot = null
        edgeRedDot = null
    }
}
