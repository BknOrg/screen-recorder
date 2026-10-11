package com.system.screenrecorder.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class CountdownOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var countDownTimer: CountDownTimer? = null

    @SuppressLint("SetTextI18n")
    fun showCountdown(seconds: Int, onFinished: () -> Unit) {
        if (seconds <= 0) {
            onFinished()
            return
        }

        dismiss()

        val windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(80, 0, 0, 0)) // Subtle dimming
        }

        val centerContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            this.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        // Circular badge for number
        val numberCircle = TextView(context).apply {
            textSize = 56f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            val sizePx = (110 * context.resources.displayMetrics.density).toInt()
            this.layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(220, 30, 30, 32)) // Samsung dark One UI surface
                setStroke((2 * context.resources.displayMetrics.density).toInt(), Color.argb(100, 255, 255, 255))
            }
            background = bg
            text = "$seconds"
        }

        // "Lewati" (Skip) pill button
        val skipButton = TextView(context).apply {
            text = "Lewati"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            val padH = (22 * context.resources.displayMetrics.density).toInt()
            val padV = (10 * context.resources.displayMetrics.density).toInt()
            setPadding(padH, padV, padH, padV)
            val marginT = (20 * context.resources.displayMetrics.density).toInt()
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = marginT
            }
            this.layoutParams = lp
            val pillBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 30 * context.resources.displayMetrics.density
                setColor(Color.argb(200, 45, 45, 48))
            }
            background = pillBg
            setOnClickListener {
                dismiss()
                onFinished()
            }
        }

        centerContainer.addView(numberCircle)
        centerContainer.addView(skipButton)
        root.addView(centerContainer)

        try {
            windowManager.addView(root, windowParams)
            overlayView = root
        } catch (e: Exception) {
            e.printStackTrace()
            onFinished()
            return
        }

        // Animate countdown
        countDownTimer = object : CountDownTimer((seconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secRemaining = (millisUntilFinished / 1000) + 1
                numberCircle.text = "$secRemaining"

                // Scale pulse animation
                val scaleAnim = ValueAnimator.ofFloat(1.3f, 1.0f).apply {
                    duration = 350
                    interpolator = OvershootInterpolator()
                    addUpdateListener {
                        val v = it.animatedValue as Float
                        numberCircle.scaleX = v
                        numberCircle.scaleY = v
                    }
                }
                scaleAnim.start()
            }

            override fun onFinish() {
                dismiss()
                onFinished()
            }
        }.start()
    }

    fun dismiss() {
        countDownTimer?.cancel()
        countDownTimer = null
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
    }
}
