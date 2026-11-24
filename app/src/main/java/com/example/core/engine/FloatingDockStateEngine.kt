package com.example.core.engine

/**
 * Pure Kotlin state machine controlling Floating Dock visibility, scroll hysteresis,
 * and modal/keyboard suppression. Shared contract between Android and iOS.
 */
data class DockScrollConfig(
    val hideDeltaThresholdPx: Float = 42f,
    val showDeltaThresholdPx: Float = 28f,
    val toggleDebounceMs: Long = 90L,
)

data class DockVisibilityState(
    val isVisible: Boolean = true,
    val isAtTop: Boolean = true,
    val isModalActive: Boolean = false,
    val isKeyboardOpen: Boolean = false,
    val reducedMotionEnabled: Boolean = false,
) {
    val shouldRenderDock: Boolean
        get() = isVisible && !isModalActive && !isKeyboardOpen
}

class FloatingDockStateMachine(
    private val config: DockScrollConfig = DockScrollConfig(),
) {
    private var accumulatedUpwardScrollPx: Float = 0f
    private var accumulatedDownwardScrollPx: Float = 0f
    private var lastToggleTimestampMs: Long = -10_000L

    var state: DockVisibilityState = DockVisibilityState()
        private set

    fun onScrollDelta(deltaY: Float, nowMs: Long): DockVisibilityState {
        if (state.isModalActive || state.isKeyboardOpen) {
            accumulatedUpwardScrollPx = 0f
            accumulatedDownwardScrollPx = 0f
            return state
        }

        if (deltaY > 0f) {
            accumulatedUpwardScrollPx += deltaY
            accumulatedDownwardScrollPx = 0f
            if (!state.isAtTop &&
                state.isVisible &&
                accumulatedUpwardScrollPx >= config.hideDeltaThresholdPx &&
                (nowMs - lastToggleTimestampMs) >= config.toggleDebounceMs
            ) {
                state = state.copy(isVisible = false)
                lastToggleTimestampMs = nowMs
                accumulatedUpwardScrollPx = 0f
            }
        } else if (deltaY < 0f) {
            accumulatedDownwardScrollPx += -deltaY
            accumulatedUpwardScrollPx = 0f
            if (!state.isVisible &&
                accumulatedDownwardScrollPx >= config.showDeltaThresholdPx &&
                (nowMs - lastToggleTimestampMs) >= config.toggleDebounceMs
            ) {
                state = state.copy(isVisible = true)
                lastToggleTimestampMs = nowMs
                accumulatedDownwardScrollPx = 0f
            }
        }
        return state
    }

    fun onAtTopChanged(isAtTop: Boolean, nowMs: Long = 0L): DockVisibilityState {
        if (state.isAtTop == isAtTop) return state
        state = state.copy(isAtTop = isAtTop)
        if (isAtTop) {
            accumulatedUpwardScrollPx = 0f
            accumulatedDownwardScrollPx = 0f
            if (!state.isVisible) {
                state = state.copy(isVisible = true)
                lastToggleTimestampMs = nowMs
            }
        } else if (
            !state.isModalActive &&
            !state.isKeyboardOpen &&
            state.isVisible &&
            accumulatedUpwardScrollPx >= config.hideDeltaThresholdPx &&
            (nowMs - lastToggleTimestampMs) >= config.toggleDebounceMs
        ) {
            state = state.copy(isVisible = false)
            lastToggleTimestampMs = nowMs
            accumulatedUpwardScrollPx = 0f
        }
        return state
    }

    fun onTabChanged(nowMs: Long = 0L): DockVisibilityState {
        accumulatedUpwardScrollPx = 0f
        accumulatedDownwardScrollPx = 0f
        if (!state.isVisible) {
            lastToggleTimestampMs = nowMs
        }
        state = state.copy(isVisible = true, isAtTop = true)
        return state
    }

    fun onModalActiveChanged(active: Boolean): DockVisibilityState {
        if (state.isModalActive == active) return state
        accumulatedUpwardScrollPx = 0f
        accumulatedDownwardScrollPx = 0f
        state = state.copy(
            isModalActive = active,
            isVisible = if (!active) true else state.isVisible,
        )
        return state
    }

    fun onKeyboardOpenChanged(open: Boolean): DockVisibilityState {
        if (state.isKeyboardOpen == open) return state
        state = state.copy(isKeyboardOpen = open)
        return state
    }

    fun onReducedMotionChanged(enabled: Boolean): DockVisibilityState {
        state = state.copy(reducedMotionEnabled = enabled)
        return state
    }
}
