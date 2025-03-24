package com.xiaoxiaoying.timeflip

import android.content.Context
import android.util.TypedValue

object SizeUtils {
    @JvmStatic
    @JvmOverloads
    fun Context.getTextSize(textSize: Float, unit: Int = TypedValue.COMPLEX_UNIT_DIP): Float {
        return TypedValue.applyDimension(
            unit,
            textSize,
            this.resources.displayMetrics
        )
    }
}