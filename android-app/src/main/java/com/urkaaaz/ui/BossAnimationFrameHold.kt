package com.urkaaaz.ui

internal class BossAnimationFrameHold<T : Any> {
    private var bossEntityId: String? = null
    private var presentationKey: String? = null
    private var lastReadyFrame: T? = null

    fun resolve(
        bossEntityId: String,
        presentationKey: String,
        currentFrame: T?,
    ): T? {
        if (currentFrame != null) {
            this.bossEntityId = bossEntityId
            this.presentationKey = presentationKey
            lastReadyFrame = currentFrame
            return currentFrame
        }

        return lastReadyFrame.takeIf {
            this.bossEntityId == bossEntityId &&
                this.presentationKey == presentationKey
        }
    }

    fun clear() {
        bossEntityId = null
        presentationKey = null
        lastReadyFrame = null
    }
}
