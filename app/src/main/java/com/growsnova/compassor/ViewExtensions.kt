package com.growsnova.compassor

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils

@SuppressLint("ClickableViewAccessibility")
fun View.applyTouchScale() {
    this.setOnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val anim = AnimationUtils.loadAnimation(context, R.anim.scale_down)
                v.startAnimation(anim)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val anim = AnimationUtils.loadAnimation(context, R.anim.scale_up)
                v.startAnimation(anim)
            }
        }
        false
    }
}
