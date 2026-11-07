package mx.matica.presentation.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.motion.MaticaMotion
import mx.matica.design.tokens.MaticaLayout
import mx.matica.design.tokens.MaticaPalette
import mx.matica.design.tokens.MaticaShapes
import mx.matica.design.tokens.MaticaSpacing
import mx.matica.presentation.components.MaticaGlyph
import mx.matica.presentation.components.MaticaIcon
import mx.matica.presentation.route.MaticaNavigator
import mx.matica.presentation.route.MaticaRoute

/**
 * Destino de navegación de primer nivel en la barra inferior de Mática.
 */
enum class MaticaNavTab(
    val label: String,
    val icon: MaticaIcon,
    val route: MaticaRoute,
) {
    HOME("Inicio", MaticaIcon.HOME, MaticaRoute.Home),
    ROUTE("Ruta", MaticaIcon.ROUTE, MaticaRoute.RouteMap),
    REVIEW("Repaso", MaticaIcon.REFRESH, MaticaRoute.ReviewQueue),
    PROFILE("Perfil", MaticaIcon.PROFILE, MaticaRoute.Profile),
}

/**
 * Barra inferior de navegación de Mática (Figma `11:114`, `11:139`, `11:189`).
 *
 * Expone los 4 destinos principales con un contenedor pill activo, objetivo táctil
 * medido (≥ 48 dp) y contraste verificado en temas claro y oscuro.
 */
@Composable
fun MaticaNavigationBar(
    currentRoute: MaticaRoute,
    navigator: MaticaNavigator,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotionStyle.current
    val animDuration = if (motion.reduced) 0 else motion.ms(MaticaMotion.durations.short)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 1.dp,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = MaticaLayout.maxContentWidth)
                    .heightIn(min = 60.dp)
                    .padding(horizontal = MaticaSpacing.sm, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MaticaNavTab.values().forEach { tab ->
                    val isSelected = when (tab) {
                        MaticaNavTab.HOME -> currentRoute is MaticaRoute.Home
                        MaticaNavTab.ROUTE -> currentRoute is MaticaRoute.RouteMap || currentRoute is MaticaRoute.SkillDetail
                        MaticaNavTab.REVIEW -> currentRoute is MaticaRoute.ReviewQueue
                        MaticaNavTab.PROFILE -> currentRoute is MaticaRoute.Profile || currentRoute is MaticaRoute.Settings || currentRoute is MaticaRoute.Rewards
                    }

                    val activeBg = MaterialTheme.colorScheme.primaryContainer
                    val activeFg = MaterialTheme.colorScheme.primary
                    val inactiveFg = MaterialTheme.colorScheme.onSurfaceVariant

                    val pillBg by animateColorAsState(
                        targetValue = if (isSelected) activeBg else Color.Transparent,
                        animationSpec = tween(durationMillis = animDuration, easing = FastOutSlowInEasing),
                        label = "tab-bg-${tab.name}",
                    )

                    val itemColor by animateColorAsState(
                        targetValue = if (isSelected) activeFg else inactiveFg,
                        animationSpec = tween(durationMillis = animDuration, easing = FastOutSlowInEasing),
                        label = "tab-fg-${tab.name}",
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(MaticaShapes.medium))
                            .background(pillBg)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true),
                                onClick = {
                                    if (!isSelected) {
                                        navigator.resetTo(tab.route)
                                    }
                                },
                            )
                            .semantics {
                                this.role = Role.Tab
                                this.selected = isSelected
                                this.contentDescription = "${tab.label}${if (isSelected) ", pestaña activa" else ""}"
                            }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            MaticaGlyph(
                                icon = tab.icon,
                                tint = itemColor,
                                modifier = Modifier.size(22.dp),
                                strokeWidthDp = if (isSelected) 2.4f else 2f,
                            )
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                ),
                                color = itemColor,
                            )
                        }
                    }
                }
            }
        }
    }
}
