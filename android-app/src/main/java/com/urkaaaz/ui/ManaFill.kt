package com.urkaaaz.ui

internal object ManaFill {
    fun fraction(current: Int, maximum: Int): Float {
        if (maximum <= 0) return 0f
        return current.coerceIn(0, maximum).toFloat() / maximum
    }
}
