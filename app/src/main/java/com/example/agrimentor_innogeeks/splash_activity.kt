package com.example.agrimentor_innogeeks

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieAnimationView
import androidx.core.graphics.toColorInt

class splash_activity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)

        val lottieAnimationView = findViewById<LottieAnimationView>(R.id.lottieAnimationView)

        lottieAnimationView.speed = 4.0f

        // Set Lottie animation to play only once
        lottieAnimationView.repeatCount = 0

        // Background Color Transition
        val colorAnim = ValueAnimator.ofArgb(Color.WHITE, "#90EE90".toColorInt())
        colorAnim.duration = 2000
        colorAnim.addUpdateListener { animator ->
            findViewById<View>(android.R.id.content).setBackgroundColor(animator.animatedValue as Int)
        }
        colorAnim.start()

        // Create circular reveal and fade out animation
        val circularFadeOut = ObjectAnimator.ofPropertyValuesHolder(
            lottieAnimationView,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 0.5f),
            PropertyValuesHolder.ofFloat("scaleY", 1f, 0.5f),
            PropertyValuesHolder.ofFloat("alpha", 1f, 0f)
        )

        // Make the animation start with acceleration and end slowly for a smooth effect
        circularFadeOut.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        circularFadeOut.duration = 1200
        circularFadeOut.startDelay = 2000 // Wait for Lottie animation to finish
        circularFadeOut.start()

        // Apply circular clip to make it look like the animation is becoming a circle
        lottieAnimationView.clipToOutline = true
        lottieAnimationView.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }

        // Delayed Transition to Login Screen
        Handler().postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 2800)
    }
}
