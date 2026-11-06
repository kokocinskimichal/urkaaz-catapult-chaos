package com.urkaaaz.screens.campaign

import com.urkaaaz.android.R

internal object CampaignChapterTitleResources {
    fun forChapter(chapterId: String): Int =
        when (chapterId) {
            "green_frontier" -> R.string.chapter_title_green_frontier
            "broken_valley" -> R.string.chapter_title_broken_valley
            "ashen_march" -> R.string.chapter_title_ashen_march
            "whispering_marsh" -> R.string.chapter_title_whispering_marsh
            "ironwood" -> R.string.chapter_title_ironwood
            "northern_march" -> R.string.chapter_title_northern_march
            "runic_wastes" -> R.string.chapter_title_runic_wastes
            "red_peaks" -> R.string.chapter_title_red_peaks
            "final_warpath" -> R.string.chapter_title_final_warpath
            "last_siege" -> R.string.chapter_title_last_siege
            else -> error("unknown campaign chapter ID: $chapterId")
        }
}
