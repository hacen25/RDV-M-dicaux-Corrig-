# Mes RDV médicaux — construire l'APK

## Option A : Android Studio (le plus simple)
1. Ouvre le dossier du projet dans Android Studio (File > Open).
2. Attends la fin de la synchronisation Gradle.
3. Menu Build > Build Bundle(s) / APK(s) > Build APK(s).
4. L'APK est dans `app/build/outputs/apk/debug/rdv-medicaux-debug.apk`.

## Option B : en ligne de commande
    ./gradlew assembleDebug      (Windows : gradlew.bat assembleDebug)

## Option C : sans rien installer (GitHub)
Pousse le projet sur un dépôt GitHub : l'onglet Actions construit l'APK
(workflow « Build APK »), à télécharger dans « Artifacts ».

## Après installation (important pour que l'alarme sonne téléphone verrouillé)
1. Accepte la notification quand l'appli la demande.
2. Si un bandeau orange apparaît en haut de l'appli, appuie sur « Corriger dans les réglages »
   et autorise les notifications plein écran.
3. Appuie sur « Tester l'alarme », verrouille le téléphone, ferme l'appli : l'alarme doit sonner
   et l'écran s'allumer dans 10 secondes.
4. Règle le volume « Alarme » du téléphone (réglage séparé du volume des médias).
5. Xiaomi / Huawei / Oppo / Samsung : mets l'appli en « Aucune restriction » dans la batterie
   et active le « Démarrage automatique », sinon le système peut tuer les alarmes.
