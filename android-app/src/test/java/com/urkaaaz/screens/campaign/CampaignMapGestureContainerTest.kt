package com.urkaaaz.screens.campaign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignMapGestureContainerTest {
    @Test
    fun horizontalSwipeDirectionFollowsDragDirection() {
        assertEquals(
            CampaignChapterNavigationDirection.NEXT,
            chapterDirectionForSwipe(deltaX = -80f, minimumDistance = 48f),
        )
        assertEquals(
            CampaignChapterNavigationDirection.PREVIOUS,
            chapterDirectionForSwipe(deltaX = 80f, minimumDistance = 48f),
        )
    }

    @Test
    fun shortDragDoesNotNavigateToAnotherChapter() {
        assertNull(chapterDirectionForSwipe(deltaX = 47f, minimumDistance = 48f))
        assertNull(chapterDirectionForSwipe(deltaX = -47f, minimumDistance = 48f))
    }

    @Test
    fun verticalOrShortMovementDoesNotInterceptTheMapGesture() {
        assertFalse(isHorizontalChapterSwipe(deltaX = 60f, deltaY = 70f, minimumDistance = 48f))
        assertFalse(isHorizontalChapterSwipe(deltaX = 47f, deltaY = 0f, minimumDistance = 48f))
        assertTrue(isHorizontalChapterSwipe(deltaX = -48f, deltaY = 5f, minimumDistance = 48f))
    }
}
