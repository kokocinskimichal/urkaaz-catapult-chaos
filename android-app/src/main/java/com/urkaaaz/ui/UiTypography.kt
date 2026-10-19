package com.urkaaaz.ui

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.urkaaaz.android.R

internal object UiTypography {
    fun typeface(context: Context): Typeface =
        requireNotNull(ResourcesCompat.getFont(context, R.font.cinzel_semibold)) {
            "Cinzel SemiBold font resource could not be loaded"
        }
}
