package com.example.ui.navigation

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.engine.DockScrollConfig
import com.example.core.engine.DockVisibilityState
import com.example.core.engine.FloatingDockStateMachine
import com.example.core.ports.CrossPlatformDesignTokens
import com.example.ui.theme.ExcevoColors

@Stable
class ComposeFloatingDockController(
    config: DockScrollConfig = DockScrollConfig(),
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    private val machine = FloatingDockStateMachine(config)
    var state by mutableStateOf(machine.state)
        private set

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // In Compose, dragging upward to scroll down produces negative available.y,
            // so upward scroll displacement is -available.y.
            if (available.y != 0f) {
                state = machine.onScrollDelta(deltaY = -available.y, nowMs = clockMs())
            }
            return Offset.Zero
        }
    }

    fun onAtTopChanged(isAtTop: Boolean) {
        state = machine.onAtTopChanged(isAtTop, clockMs())
    }

    fun onTabChanged() {
        state = machine.onTabChanged(clockMs())
    }

    fun onModalActiveChanged(active: Boolean) {
        state = machine.onModalActiveChanged(active)
    }

    fun onKeyboardOpenChanged(open: Boolean) {
        state = machine.onKeyboardOpenChanged(open)
    }

    fun onReducedMotionChanged(enabled: Boolean) {
        state = machine.onReducedMotionChanged(enabled)
    }
}

@Composable
fun rememberFloatingDockController(): ComposeFloatingDockController {
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
    return remember(reducedMotion) {
        ComposeFloatingDockController().apply {
            onReducedMotionChanged(reducedMotion)
        }
    }
}

@Composable
fun ExcevoFloatingDock(
    activeTab: MainTab,
    visibilityState: DockVisibilityState,
    onSelectTab: (MainTab) -> Unit,
    onCenterPlusClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = visibilityState.reducedMotionEnabled
    val enterAnim = if (reduced) {
        fadeIn(animationSpec = snap())
    } else {
        slideInVertically(
            initialOffsetY = { it + 48 },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        ) + fadeIn(animationSpec = tween(CrossPlatformDesignTokens.DOCK_SHOW_DURATION_MS))
    }
    val exitAnim = if (reduced) {
        fadeOut(animationSpec = snap())
    } else {
        slideOutVertically(
            targetOffsetY = { it + 48 },
            animationSpec = tween(
                durationMillis = CrossPlatformDesignTokens.DOCK_HIDE_DURATION_MS,
                easing = FastOutSlowInEasing,
            ),
        ) + fadeOut(animationSpec = tween(CrossPlatformDesignTokens.DOCK_HIDE_DURATION_MS))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = visibilityState.shouldRenderDock,
            enter = enterAnim,
            exit = exitAnim,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .testTag("excevo_floating_dock"),
                contentAlignment = Alignment.BottomCenter,
            ) {
                val capsuleShape = RoundedCornerShape(CrossPlatformDesignTokens.DOCK_CAPSULE_RADIUS_DP.dp)

                // Soft translucent frosted glass capsule
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CrossPlatformDesignTokens.DOCK_HEIGHT_DP.dp)
                        .shadow(
                            elevation = 16.dp,
                            shape = capsuleShape,
                            ambientColor = ExcevoColors.AccentPurple.copy(alpha = 0.28f),
                            spotColor = Color.Black.copy(alpha = 0.65f),
                        )
                        .clip(capsuleShape),
                ) {
                    // Subtle backdrop blur layer on Android 12+
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .then(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    Modifier.blur(18.dp)
                                } else {
                                    Modifier
                                }
                            )
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        ExcevoColors.ElevatedSurface.copy(alpha = 0.88f),
                                        ExcevoColors.StudioPanel.copy(alpha = 0.94f),
                                    )
                                )
                            )
                    )

                    Surface(
                        color = Color.Transparent,
                        shape = capsuleShape,
                        border = BorderStroke(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    ExcevoColors.AccentLavender.copy(alpha = 0.28f),
                                    ExcevoColors.HairlineBorder.copy(alpha = 0.85f),
                                )
                            ),
                        ),
                        modifier = Modifier.matchParentSize(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            DockNavSlot(
                                tab = MainTab.HOME,
                                selected = activeTab == MainTab.HOME,
                                reducedMotion = reduced,
                                onClick = { onSelectTab(MainTab.HOME) },
                                modifier = Modifier.weight(1f),
                            )
                            DockNavSlot(
                                tab = MainTab.PROJECTS,
                                selected = activeTab == MainTab.PROJECTS,
                                reducedMotion = reduced,
                                onClick = { onSelectTab(MainTab.PROJECTS) },
                                modifier = Modifier.weight(1f),
                            )
                            // Central spacer for elevated Plus button
                            Box(modifier = Modifier.weight(1f))
                            DockNavSlot(
                                tab = MainTab.PRESETS,
                                selected = activeTab == MainTab.PRESETS,
                                reducedMotion = reduced,
                                onClick = { onSelectTab(MainTab.PRESETS) },
                                modifier = Modifier.weight(1f),
                            )
                            DockNavSlot(
                                tab = MainTab.PROFILE,
                                selected = activeTab == MainTab.PROFILE,
                                reducedMotion = reduced,
                                onClick = { onSelectTab(MainTab.PROFILE) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // Elevated Circular Gradient Plus Button (Center Position #3)
                CenterDockPlusButton(
                    reducedMotion = reduced,
                    onClick = onCenterPlusClick,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-CrossPlatformDesignTokens.DOCK_CENTER_ELEVATION_OFFSET_DP).dp),
                )
            }
        }
    }
}

@Composable
private fun DockNavSlot(
    tab: MainTab,
    selected: Boolean,
    reducedMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(tab.labelRes)
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "dockIconScale",
    )

    Column(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = 6.dp)
            .testTag("nav_tab_${tab.name}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (selected) ExcevoColors.AccentPurpleContainer.copy(alpha = 0.85f) else Color.Transparent
                )
                .padding(horizontal = 12.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = label,
                tint = if (selected) ExcevoColors.AccentLavender else ExcevoColors.TextSecondary,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) ExcevoColors.AccentLavender else ExcevoColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CenterDockPlusButton(
    reducedMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1.0f,
        animationSpec = if (reducedMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "plusPressScale",
    )

    Box(
        modifier = modifier
            .size(CrossPlatformDesignTokens.DOCK_CENTER_BUTTON_SIZE_DP.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 14.dp,
                shape = CircleShape,
                ambientColor = ExcevoColors.AccentVioletGlow.copy(alpha = 0.6f),
                spotColor = ExcevoColors.AccentPurple.copy(alpha = 0.7f),
            )
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        ExcevoColors.AccentVioletGlow,
                        ExcevoColors.AccentPurple,
                    )
                )
            )
            .border(
                width = 1.5.dp,
                color = ExcevoColors.AccentLavender.copy(alpha = 0.65f),
                shape = CircleShape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .testTag("dock_center_plus_button"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = "New Composition",
            tint = Color.White,
            modifier = Modifier.size(30.dp),
        )
    }
}
