package fr.guidetv.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import fr.guidetv.R

/**
 * Roboto Flex, la police variable recommandée par Material 3 Expressive.
 * Embarquée (res/font), donc aucun téléchargement ni service Google : le
 * Downloadable Fonts provider passe par les Play Services, exclus ici.
 *
 * Coût : ~1,8 Mo dans l'APK. Pour revenir à la police système, il suffit de
 * remplacer `GuideTypography` par `Typography()` dans Theme.kt et de supprimer
 * res/font/roboto_flex.ttf.
 *
 * Licence : SIL Open Font License 1.1 (THIRD-PARTY-LICENSES/RobotoFlex-OFL.txt).
 */
// FontVariation et la surcharge Font(..., variationSettings = ...) sont annotées
// @ExperimentalTextApi dans Compose : l'opt-in est obligatoire, pas facultatif.
@OptIn(ExperimentalTextApi::class)
private fun flex(weight: Int) = Font(
    resId = R.font.roboto_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val RobotoFlex = FontFamily(
    flex(300),
    flex(400),
    flex(500),
    flex(600),
    flex(700),
)

private val base = Typography()

/**
 * Mot-symbole « Lucarne » : Roboto Flex poussée sur ses axes — graisse 800 et chasse
 * élargie. Pas de seconde police à embarquer, et un titre qui se distingue du reste
 * de l'interface.
 */
@OptIn(ExperimentalTextApi::class)
val WordmarkFamily = FontFamily(
    Font(
        resId = R.font.roboto_flex,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(800),
            FontVariation.width(115f),
        ),
    ),
)

val WordmarkStyle = TextStyle(
    fontFamily = WordmarkFamily,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 26.sp,
    letterSpacing = 0.4.sp,
)

val GuideTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = RobotoFlex),
    displayMedium = base.displayMedium.copy(fontFamily = RobotoFlex),
    displaySmall = base.displaySmall.copy(fontFamily = RobotoFlex),
    headlineLarge = base.headlineLarge.copy(fontFamily = RobotoFlex, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = RobotoFlex, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = RobotoFlex, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = RobotoFlex, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = RobotoFlex, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = RobotoFlex),
    bodyLarge = base.bodyLarge.copy(fontFamily = RobotoFlex),
    bodyMedium = base.bodyMedium.copy(fontFamily = RobotoFlex),
    bodySmall = base.bodySmall.copy(fontFamily = RobotoFlex),
    labelLarge = base.labelLarge.copy(fontFamily = RobotoFlex, fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontFamily = RobotoFlex, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = RobotoFlex),
)
