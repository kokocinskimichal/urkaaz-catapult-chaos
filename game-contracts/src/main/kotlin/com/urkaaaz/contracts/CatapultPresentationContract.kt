package com.urkaaaz.contracts

/**
 * Shared presentation geometry for catapults and their projectile anchors.
 *
 * The vertical relation is expressed in world units so it remains stable
 * across maps; only the foundation height comes from the selected map.
 */
object CatapultPresentationContract {
    const val FORTRESS_CENTER_MARGIN = 490f
    const val CATAPULT_CENTER_OFFSET_FROM_FORTRESS = 40f
    const val ANCHOR_VERTICAL_OFFSET_FROM_FOUNDATION = -360f

    fun catapultCenterX(team: Team, worldWidth: Float): Float =
        if (team == Team.BLUE) {
            FORTRESS_CENTER_MARGIN - CATAPULT_CENTER_OFFSET_FROM_FORTRESS
        } else {
            worldWidth - FORTRESS_CENTER_MARGIN + CATAPULT_CENTER_OFFSET_FROM_FORTRESS
        }

    fun anchorY(foundationHeight: Float, worldHeight: Float): Float =
        foundationHeight +
            ANCHOR_VERTICAL_OFFSET_FROM_FOUNDATION * (worldHeight / WorldDimensions.HEIGHT)
}
