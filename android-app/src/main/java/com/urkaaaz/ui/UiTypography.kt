package com.urkaaaz.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView
import androidx.annotation.DimenRes
import androidx.core.widget.TextViewCompat
import androidx.core.content.res.ResourcesCompat
import com.urkaaaz.android.R
import kotlin.math.roundToInt

internal object UiTypography {
    fun typeface(context: Context): Typeface =
        requireNotNull(ResourcesCompat.getFont(context, R.font.cinzel_semibold)) {
            "Cinzel SemiBold font resource could not be loaded"
        }

    fun applyButtonTypeface(textView: TextView) {
        textView.typeface = typeface(textView.context)
    }

    fun applyTextSize(textView: TextView, @DimenRes sizeResource: Int) {
        textView.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            textView.resources.getDimension(sizeResource),
        )
    }

    fun applyTextSize(textView: TextView, control: ButtonTypographyControl) {
        applyTextSize(textView, control.dimensionResource)
    }

    fun applyAutoSizeTextSize(
        textView: TextView,
        control: ButtonTypographyControl,
        minimumTextSizeSp: Float = 7f,
    ) {
        val value = TypedValue()
        textView.resources.getValue(control.dimensionResource, value, true)
        val maximumTextSizeSp = TypedValue.complexToFloat(value.data)
        val sizesInPixels = buildList {
            var sizeSp = minimumTextSizeSp
            while (sizeSp < maximumTextSizeSp) {
                add(
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP,
                        sizeSp,
                        textView.resources.displayMetrics,
                    ).roundToInt(),
                )
                sizeSp += 0.5f
            }
            add(textView.resources.getDimension(control.dimensionResource).roundToInt())
        }.distinct().toIntArray()
        TextViewCompat.setAutoSizeTextTypeUniformWithPresetSizes(
            textView,
            sizesInPixels,
            TypedValue.COMPLEX_UNIT_PX,
        )
    }

    fun textSizeInPixels(context: Context, control: ButtonTypographyControl): Int =
        context.resources.getDimension(control.dimensionResource).roundToInt()
}
