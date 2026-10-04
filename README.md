# Lucarne

Appli Android de guide des programmes, alimentée par un fichier XMLTV.
Tout se passe sur le téléphone : téléchargement, cache, parsing, affichage.
Aucun serveur intermédiaire, aucune bibliothèque tierce, aucun service Google.

## Ouvrir

Android Studio → **Open** → choisir ce dossier (celui qui contient `settings.gradle.kts`),
puis laisser la synchronisation Gradle se faire. JDK 17 requis (Gradle JDK dans les réglages).

Versions épinglées : AGP 8.7.0, Gradle 8.10.2, Kotlin 2.0.21, compose-bom 2024.10.01,
compileSdk 35, minSdk 26 (java.time sans desugaring).

Le `gradle-wrapper.jar` vient du dépôt officiel de Gradle (tag v8.10.2),
sha256 `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`.

Rien n'a été compilé côté rédaction : attends-toi à quelques corrections d'API Compose
selon la version résolue.

## À corriger avant le premier lancement

- `GuideRepository.DEFAULT_SOURCE_URL` : l'URL exacte du fichier TNT est à copier
  depuis <https://xmltvfr.fr/xmltv.php>.
- `GuideRepository.USER_AGENT` : mets l'URL de ton dépôt.

## Organisation

| Fichier | Rôle |
| --- | --- |
| `xmltv/XmltvModels.kt` | `Channel`, `Programme`, `Guide` (« en ce moment », « ce soir », grille du jour) |
| `xmltv/XmltvParser.kt` | Parsing XMLTV en flux via `XmlPullParser` |
| `xmltv/TntNumbering.kt` | Numéros de canal Arcom, en repli du fichier |
| `data/GuideRepository.kt` | Téléchargement conditionnel, cache, décompression, préférences |
| `ui/GuideUi.kt` | `GuideViewModel`, liste et fiche programme |
| `ui/ImageLoader.kt` | Chargeur d'images (cache mémoire + disque) |
| `ui/Theme.kt` | Thème Material 3, formes expressives, couleurs dynamiques |
| `ui/AboutScreen.kt` | À propos : licences (GPL, OFL), attribution des données |
| `ui/Type.kt` | Typographie Roboto Flex (police variable recommandée par M3 Expressive) |

## Décisions, et quand les remettre en cause

**Pas de base de données.** Le `.gz` est conservé tel quel et reparsé au lancement
(TNT : ~7 Mo décompressés), puis tout est en mémoire. À basculer vers Room le jour où
tu ajoutes un widget qui se rafraîchit souvent ou des rappels programmés à l'avance.

**Parsing en flux.** La mémoire dépend du nombre de diffusions gardées, pas de la taille
du fichier.

**Format détecté sur les octets**, pas sur l'extension : gzip, zip ou XML brut. Le `.xz`
exigerait une bibliothèque tierce : non géré.

**Numéros de chaîne.** Un `<display-name>` numérique du fichier gagne toujours ; sinon on
retombe sur la table Arcom du 6 juin 2025, à mettre à jour à chaque décision.

**Typographie.** Roboto Flex embarqué dans `res/font` (~1,8 Mo), parce que le
Downloadable Fonts provider de Google passe par les Play Services, exclus ici. Retour à
la police système : `Typography()` dans `Theme.kt` et suppression du fichier.

**Material 3 Expressive.** Appliqué : échelle de formes, rôles de couleur par conteneurs,
couleurs dynamiques, Roboto Flex, graisses portées par le type scale, boutons segmentés
pour le choix exclusif, grande barre de titre rétractable, edge-to-edge, icônes dans la
barre plutôt que des boutons texte.

Écarts assumés, faute d'API stable : `MaterialExpressiveTheme`, le `MotionScheme` à
ressorts, `ButtonGroup`, `FloatingToolbar` et le nouveau `LoadingIndicator` ont été
retirés de Material3 1.4.0 et ne vivent que sur la branche 1.5.0-alpha. Les cartes
utilisent `Surface` plutôt que `Card`, à comportement identique. Le basculement vers les
vraies API Expressive tient en trois lignes dans `ui/Theme.kt`.

**Économie de bande passante**, la source étant un service gratuit :
`If-None-Match` / `If-Modified-Since`, une vérification toutes les 15 minutes au plus,
User-Agent identifiable.

## Licences

Le code de cette application est sous **GPL-3.0-or-later** (fichier `LICENSE`).
Dépendances : AndroidX, Jetpack Compose et la bibliothèque standard Kotlin, toutes sous
Apache-2.0 ; aucune bibliothèque tierce.

**Les données ne sont pas libres.** Le générateur XML TV Fr est sous licence MIT, mais les
grilles qu'il produit sont agrégées depuis des éditeurs commerciaux (Télé-Loisirs,
Télérama, Orange, Bouygues…). Aucune licence n'est accordée sur ces contenus.

**Les visuels le sont encore moins.** Les vignettes sont servies par les serveurs des
éditeurs et restent leur propriété. L'application n'en redistribue aucune : chaque
appareil les demande directement à l'éditeur, comme le ferait un navigateur. C'est le
point faible du projet, et la raison pour laquelle le réglage d'affichage des images doit
rester accessible à l'utilisateur.

Police Roboto Flex : SIL Open Font License 1.1, texte dans
`THIRD-PARTY-LICENSES/RobotoFlex-OFL.txt`.

Table des logos de chaînes (`res/raw/channel_logos.json`) : dérivée du projet XML TV Fr,
déclaré sous MIT ; voir `THIRD-PARTY-LICENSES/XMLTVFr-NOTICE.txt`.

Les deux textes de licence sont aussi embarqués dans l'APK (`res/raw/`) et lisibles
depuis l'écran « À propos » : la GPL impose de transmettre sa licence avec le programme,
l'OFL impose d'accompagner la police de la sienne.

Aucune donnée de programme n'est embarquée : ni grille, ni résumé, ni visuel d'émission,
ni logo de chaîne.

Aucune condition d'utilisation n'est publiée par XML TV Fr : le site n'affiche qu'un
avertissement sur le coût de la bande passante et un lien de don. L'absence de conditions
n'est pas une autorisation, d'où le message au mainteneur avant publication.

## Publier sur F-Droid

Déjà en place : licence GPL-3.0-or-later avec son texte (`LICENSE`), licences des
composants embarquées et lisibles dans l'application, aucune dépendance propriétaire ni
traceur, sources XMLTV configurables dans les réglages — ce qui évite l'étiquette
« Tethered Network Services » —, icône adaptative avec variante monochrome, métadonnées
fastlane (titre, résumé, description, journal de version, icône 512), `versionCode` 1 et
`versionName` 1.0.

Restent à faire, dans l'ordre :

1. Publier le dépôt (Codeberg, GitLab ou GitHub) et poser une étiquette `v0.1`.
2. Remplacer les adresses d'exemple : `USER_AGENT` dans `GuideRepository`, `SourceCode`
   et `Repo` dans `fdroid-metadata.yml`.
3. Décider de l'identifiant d'application. Il vaut encore `fr.guidetv` alors que
   l'application s'appelle Lucarne ; un identifiant ne se change plus après publication.
4. Ajouter deux ou trois captures d'écran dans
   `fastlane/metadata/android/fr-FR/images/phoneScreenshots/`.
5. Ouvrir une demande d'inclusion (RFP) sur le gitlab de fdroiddata, avec le gabarit
   `fdroid-metadata.yml`.
6. Écrire au mainteneur de la source XMLTV avant la mise en ligne.

## Suite

1. Écran de réglages : URL de la source, sélection des chaînes, interrupteur des images
   (`repo.sourceUrl`, `repo.selectedChannels`, `repo.showImages` existent déjà).
2. Rappels. Sur Android 14+, `SCHEDULE_EXACT_ALARM` est refusée par défaut aux nouvelles
   installations : demander l'autorisation, ou déléguer à l'agenda via un `Intent`.
3. Rafraîchissement de fond : `WorkManager`, contrainte réseau, une fois par jour.
4. En-têtes SPDX dans les sources.
   Palette de repli personnalisée pour les appareils antérieurs à Android 12, qui
   n'ont pas les couleurs dynamiques et affichent donc la palette de base de Material 3.
5. F-Droid : dépôt public, métadonnées (`fastlane/metadata/android/fr-FR/` est amorcé),
   build reproductible, et un mot au mainteneur de la source avant publication.
