package com.urkaaaz.ui

import com.urkaaaz.contracts.MatchPhase
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.Team
import org.junit.Assert.assertEquals
import org.junit.Test

class BattlefieldCameraControllerTest {
    @Test
    fun zoomKeepsTheGestureFocusInPlaceAcrossCameraUpdates() {
        val camera = BattlefieldCameraController()
        val state = RenderStateMapper.map(
            MatchSnapshot(
                status = MatchStatus.RUNNING,
                phase = MatchPhase.PLAYER_TURN,
                activeTeam = Team.BLUE,
            ),
        )
        val viewportWidth = 1_000f
        val viewportHeight = 600f
        val focusX = 270f
        val focusY = 190f

        camera.update(state, viewportWidth, viewportHeight)
        val worldXAtFocus = camera.screenToWorldX(focusX)
        val worldYAtFocus = camera.screenToWorldY(focusY)

        camera.applyZoom(
            scaleFactor = 1.5f,
            focusX = focusX,
            focusY = focusY,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )
        camera.update(state, viewportWidth, viewportHeight)

        assertEquals(worldXAtFocus, camera.screenToWorldX(focusX), 0.001f)
        assertEquals(worldYAtFocus, camera.screenToWorldY(focusY), 0.001f)
    }

    @Test
    fun zoomCanReachTheExpandedMaximumScale() {
        val camera = BattlefieldCameraController()

        repeat(2) {
            camera.applyZoom(
                scaleFactor = 2f,
                focusX = 500f,
                focusY = 300f,
                viewportWidth = 1_000f,
                viewportHeight = 600f,
            )
        }

        assertEquals(4f, camera.transform.scale)
    }
}
