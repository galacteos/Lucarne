package fr.guidetv.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Habillage Material 3 Expressive : formes très arrondies, conteneurs colorés,
 * couleurs dynamiques du fond d'écran (Android 12+).
 *
 * Les API Expressive elles-mêmes (MaterialExpressiveTheme, MotionScheme,
 * ButtonGroup, LoadingIndicator…) ne sont PAS dans le Material3 stable : elles
 * ont été retirées de la 1.4.0 et vivent sur la branche 1.5.0-alpha. Tant que le
 * projet reste sur une version stable, on applique le langage visuel sans les
 * composants.
 *
 * Pour basculer sur les vraies API, une fois
 * `androidx.compose.material3:material3:1.5.0-alphaXX` déclaré :
 *
 *   @OptIn(ExperimentalMaterial3ExpressiveApi::class)
 *   MaterialExpressiveTheme(colorScheme = scheme, shapes = ExpressiveShapes, content = content)
 *
 * et remplacer l'appel à MaterialTheme ci-dessous. Le reste de l'appli ne bouge pas.
 */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun GuideTvTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = ExpressiveShapes,
        typography = GuideTypography,
        content = content,
    )
}
