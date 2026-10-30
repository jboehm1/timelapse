# MASTER_TODO — tableau de bord exhaustif Timelapse

**Date de référence : 2026-10-29. Référence de développement : PR #2 / `feat/android-capture-export-alpha`.**

> **Contrat central** : [PROJECT_SPEC.md](PROJECT_SPEC.md), [PRO_PARITY.md](PRO_PARITY.md), [CAMERA_CONTROL_UX.md](CAMERA_CONTROL_UX.md) et [AGENTS.md](../AGENTS.md). Ce fichier suit **toutes les étapes concrètes pour obtenir 100 %**. Les pourcentages sont des **estimations conservatrices de validation livrable, pas du nombre de lignes de code écrites**. Réexaminer après chaque test/PR et ne jamais les augmenter sans preuve.

## Légende / méthode de score obligatoire

| Score | Preuve exigée |
|---:|---|
| **0 %** | Fonction requise, aucune implémentation vérifiable. |
| **10 %** | Spécification et stratégie initiale ; pas de code fonctionnel complet. |
| **25 %** | Code partiel/prototype dans une branche, **non compilé/non validé** (ne pas dire "fonctionne"). |
| **40 %** | APK compilé/test unitaire exécuté et interface fonctionnelle au niveau build. |
| **55 %** | Tests automatisés pertinents terminés, fautes synthétiques vérifiées. |
| **75 %** | Test matériel S21 et preuves de résultats sur les paramètres/fonctions. |
| **90 %** | Charge longue et tests de reprise réalisés, correctifs mineurs éventuels. |
| **100 %** | **Critère d'acceptation rempli et documenté**, avec preuves (logs/commit/tests/données), aucune limitation bloquante. Pour les tâches de documentation seules, le document versionné complet constitue la preuve. |

Les scores 25 % indiquent du code encore partiel et non vérifié ; les scores 40 % indiquent un APK compilé mais **aucun test matériel probant**. Ne jamais les présenter comme des fonctionnalités déjà confirmées sur appareil. Les valeurs 100 % actuelles concernent des documents et règles **existants**, pas une app certifiée. Aucun test S21 ni APK validé. Les scores sont arrondis et calculés en pondérant les priorités **P0 ×5**, **P1 ×3**, **P2 ×1**. L'objectif est **100 % par ligne**, pas uniquement une moyenne de 100 %.

## Tableau de bord actuel

<!-- PROGRESS-SCORE-START -->

**Progression pondérée estimée : 23 %** — **14/233 tâches à 100 %** (surtout documents et pipeline de build). **CAPTURE-P0-001 reste BLOQUANT : aucune preuve de capture sur Galaxy S21. Les builds sont diagnostiques / UNVERIFIED. Compilation historique : [run #40978329365](https://github.com/jboehm1/timelapse/actions/runs/40978329365). Voir le rapport de continuation pour la validation de la révision actuelle.** Les fonctions caméra ne sont pas validées par une compilation seule.

| Domaine | Tâches | Score pondéré |
|---|---:|---:|
| A — Build, outils, CI et livraison | 17 | **31 %** |
| B — Projets, Photolapse, import et stockage | 19 | **26 %** |
| C — Capture caméra, objectifs, format et qualité | 22 | **16 %** |
| D — Focus, exposition, balances et interface de mesure | 23 | **15 %** |
| E — Programmation, écran OFF et consommation | 20 | **15 %** |
| F — Fiabilité, sécurité matérielle et récupération | 30 | **22 %** |
| G — Export, formats, effets et montage vidéo | 27 | **29 %** |
| H — Commandes externes, UI, ergonomie | 18 | **20 %** |
| I — Confidentialité, sécurité, droits, commercialisation | 15 | **19 %** |
| J — Tests, mesures, robustesse validée et release | 24 | **11 %** |
| K — Documentation, gouvernance et suivi du projet | 18 | **54 %** |

<!-- PROGRESS-SCORE-END -->

## Ordre de développement contraignant

1. **Débloquer la compilation (A-01, A-02, A-03/A-04, A-05, A-06/J-04)** : compiler un APK local ou avec runner privé, collecter les erreurs réelles. Tant que non fait, aucun élément caméra/export ne peut dépasser 25 %.
2. **Tester capture et écran OFF (C-01/02/03, D-01..12, E-05, J-05/06)** sur S21, corriger permis AF/AE et pertes d'images.
3. **Rendre les originales fiables (B-08/09/10/11, F-01..13)** avant longues sessions.
4. **Valider export (G-01..27)** avec vidéos réelles et probes d'encodeur, Motion Blur et Light Trails.
5. **Prouver vrai cycle jour/nuit (E-01..20)** malgré Android 15 / Samsung / Doze.
6. **Compléter PRO intégral (B/C/D/G/H + I)** : RAW, import, codecs, timestamps, SD, BT, remote, photolapse et multi-projets.
7. **Exécuter tests 8h → 72h → 7j (J)** ; atteindre 100 % uniquement avec résultats mesurés et publiés dans le dépôt privé.

### Bloqueurs transversaux actuels

- C-01 / D-01 / G-01 : code désormais compilé ; rester ≤40 % tant que J-04 (installation et essai S21) reste à 0 %.
- E-09/J-11 : reprise nocturne avec écran éteint sur S21 non démontrée ; Android limite le lancement caméra depuis le background.
- F-10/F-11 : sauvegarde/journal pas suffisamment robuste pour promettre zéro perte.
- J-14/J-16/J-19 : aucun test d'injection de pannes réellement exécuté sur Android.
- A-07 : dépôt désormais **public** ; standard GitHub Actions disponible, test de compilation et lint réussi. Choix commercial et visibilité du code à évaluer.

## Checklist détaillée (toutes tâches à 100 % requises)

<!-- PROGRESS-LIST-START -->

### A. Build, outils, CI et livraison — 31 %

| Fait | ID | Priorité | Avancement | Fonction / garde-fou | Critère de réussite |
|---|---|---|---:|---|---|
| [x] | A-01 | P0 | **100 %** | Projet Android Gradle reproductible | Compilation debug propre sur un clone vierge |
| [x] | A-02 | P0 | **100 %** | APK debug téléchargeable | APK généré et SHA-256 publié |
| [ ] | A-03 | P0 | **40 %** | SDK Android installé pour build autonome | Toolchain officielle installée et testée |
| [ ] | A-04 | P0 | **25 %** | Script build Windows sans Actions | Exécuté de bout en bout sur Windows |
| [ ] | A-05 | P0 | **10 %** | Gradle wrapper versionné | gradlew et gradlew.bat reproduisent le build |
| [x] | A-06 | P0 | **100 %** | CI exécute compilation et tests | Run vert avec journaux et artefact APK |
| [ ] | A-07 | P0 | **10 %** | Alternative runner auto-hébergé privé | Runner branché, durci et reproductible |
| [ ] | A-08 | P1 | **0 %** | Cache fiable dépendances | Build hors réseau après initialisation |
| [ ] | A-09 | P1 | **0 %** | Versions épinglées et mises à jour dépendances | Lockfile, contrôle versions et vérification SDK |
| [ ] | A-10 | P0 | **0 %** | Signature debug vs release | Signing séparé, clés jamais commitées |
| [ ] | A-11 | P1 | **0 %** | Création AAB release | AAB signé et validé pour distribution |
| [ ] | A-12 | P0 | **0 %** | Tests d'installation et mises à jour | Installation fraîche + migration de version sans perte de photos |
| [ ] | A-13 | P1 | **0 %** | Distributions alpha et historique releases | Tag, changelog, artefact, hashes et rollback |
| [ ] | A-14 | P1 | **0 %** | Compatibilité Android 10 à 15+ | Matrice API réelle compilée et testée |
| [ ] | A-15 | P1 | **0 %** | Vérification licences des dépendances | SBOM, obligations et exclusions validées |
| [ ] | A-16 | P1 | **55 %** | Contrôle des avertissements lint/static analysis | Lint + analyse statique sans défaut majeur |
| [ ] | A-17 | P2 | **0 %** | Construction reproductible/hachage | Build déterministe comparé en CI locale |

### B. Projets, Photolapse, import et stockage — 26 %

| Fait | ID | Priorité | Avancement | Fonction / garde-fou | Critère de réussite |
|---|---|---|---:|---|---|
| [ ] | B-01 | P0 | **55 %** | Projets Timelapse sans quota artificiel | Créer reprendre renommer supprimer 100+ projets |
| [ ] | B-02 | P0 | **55 %** | Projets Photolapse indépendants | Ajout manuel de photos avec ordre explicite |
| [ ] | B-03 | P0 | **25 %** | Import massif JPEG et PNG | Importer 10000 fichiers avec mémoire bornée |
| [ ] | B-04 | P0 | **10 %** | Import via photothèque Android | Sélection multiple et permissions explicites |
| [ ] | B-05 | P1 | **0 %** | Import RAW/DNG et EXIF | Conserver origines et orientations validées |
| [ ] | B-06 | P0 | **25 %** | Export des photos originales par dossiers | Exporter individuellement sans modification d'original |
| [ ] | B-07 | P0 | **25 %** | Export ZIP facultatif | Archive exacte validée par comparaison de hashes |
| [ ] | B-08 | P0 | **25 %** | Stockage interne fichiers originaux | Photos accessibles au projet après relance |
| [ ] | B-09 | P0 | **10 %** | Stockage SD/USB externe via SAF | Persistance URI et gestion du média retiré |
| [ ] | B-10 | P0 | **40 %** | Journal/index des photos durable | Réconciliation après fermeture brutale et crash |
| [ ] | B-11 | P0 | **40 %** | Écriture atomique et reprise partiels | Pas de photo tronquée présentée comme complète |
| [ ] | B-12 | P0 | **10 %** | Détection stockage plein et quotas | Arrêt sûr avant seuil avec notification |
| [ ] | B-13 | P1 | **55 %** | Estimation stockage restant en jours | Prévision avec taille réelle des dernières images |
| [ ] | B-14 | P0 | **0 %** | Éviter doublons import et reprendre | Index conservé, aucune photo originale écrasée |
| [ ] | B-15 | P1 | **40 %** | Miniatures et galerie de projets | Navigation fluide sur 10000+ photos |
| [ ] | B-16 | P1 | **55 %** | Suppression confirmée et corbeille | Aucun effacement irréversible involontaire |
| [ ] | B-17 | P1 | **0 %** | Export/backup projet complet portable | Réimporter métadonnées+images sur nouvel appareil |
| [ ] | B-18 | P1 | **0 %** | Tris temporels et métadonnées EXIF | Ordre déterministe, orientation vérifiée |
| [ ] | B-19 | P2 | **0 %** | Espace cloud facultatif | Transfert opt-in hors chemin critique de capture |

### C. Capture caméra, objectifs, format et qualité — 16 %

| Fait | ID | Priorité | Avancement | Fonction / garde-fou | Critère de réussite |
|---|---|---|---:|---|---|
| [ ] | C-01 | P0 | **40 %** | Service caméra indépendant de l'écran | Capture écran éteint sur S21 confirmée |
| [ ] | C-02 | P0 | **40 %** | JPEG originaux haute résolution | Résolution réelle EXIF et qualité vérifiées |
| [ ] | C-03 | P0 | **40 %** | Intervalle configurable 1 à 3600 s | Mesure de cadence avec p50 p95 p99 |
| [ ] | C-04 | P0 | **25 %** | Éviter les rafales après retard | Frames manquées comptées sans rattrapage |
| [ ] | C-05 | P0 | **25 %** | Sélection des objectifs exposés | Chaque ID réel avant/arrière fonctionne |
| [ ] | C-06 | P1 | **10 %** | Focale, champ et caméra capacités | Caractéristiques détectées et présentées |
| [ ] | C-07 | P0 | **10 %** | Résolution de capture par objectif | Choix 4K/haute résolution réellement supporté |
| [ ] | C-08 | P1 | **0 %** | RAW Bayer DNG quand supporté | Capture Camera2 RAW_SENSOR et DngCreator valide |
| [ ] | C-09 | P1 | **0 %** | JPEG+RAW simultanés | Métadonnées et appariement identiques |
| [ ] | C-10 | P1 | **0 %** | Balance des blancs et paramètres capteur | Modes détectés par objectif |
| [ ] | C-11 | P1 | **0 %** | Contrôle orientation et rotation | Portrait/paysage sans changement de cadrage |
| [ ] | C-12 | P0 | **10 %** | Cadre stable entre plusieurs journées | Aucune modification de crop imprévue |
| [ ] | C-13 | P0 | **10 %** | Capture écran physique OFF | Dumpsys power prouve état display OFF |
| [ ] | C-14 | P1 | **0 %** | Délai d'exposition et shutter long | Cadence respecte temps exposition + traitement |
| [ ] | C-15 | P1 | **40 %** | Photo unique immédiate | Télécommande/volume sans collision avec timer |
| [ ] | C-16 | P1 | **40 %** | Compteur et progression en direct | Tentatives, photos sauvées et ratées distinctes |
| [ ] | C-17 | P1 | **0 %** | Contrôle résolution/cadence adaptatif | Capteur limite thermique sans corruption |
| [ ] | C-18 | P1 | **0 %** | Flou et photo noire détectés | Qualité signalée sans suppression d'image |
| [ ] | C-19 | P1 | **0 %** | Anti-flicker capture/posttraitement | Transition jour/nuages non clignotante sur vidéos |
| [ ] | C-20 | P2 | **0 %** | Prévisualisation faible puissance | Mesures RAM et batterie conformes au budget |
| [ ] | C-21 | P1 | **0 %** | Contrôles exposition/mise au point persistants | Appliqués après réouverture de session |
| [ ] | C-22 | P1 | **0 %** | Gestion sans camera ou objectifs indisponibles | Explication visible et reprise guidée |

### D. Focus, exposition, balances et interface de mesure — 15 %

| Fait | ID | Priorité | Avancement | Fonction / garde-fou | Critère de réussite |
|---|---|---|---:|---|---|
| [ ] | D-01 | P0 | **25 %** | Zone Focus verte indépendante | Tap AF ne déplace pas zone AE orange |
| [ ] | D-02 | P0 | **25 %** | Zone Exposure jaune indépendante | Tap AE ne modifie pas zone AF verte |
| [ ] | D-03 | P0 | **25 %** | Mode tap Focus / Exposure / Both | Actions séparées observées sur S21 |
| [ ] | D-04 | P0 | **25 %** | Taille des zones ajustable | Centre et rayon corrects dans coordonnées capteur |
| [ ] | D-05 | P0 | **25 %** | Mémorisation zones par projet et objectif | Après redémarrage les deux zones réapparaissent |
| [ ] | D-06 | P0 | **25 %** | Retour visuel REQUESTED / LOCKED / FAILED | Ne jamais afficher succès sur simple touch |
| [ ] | D-07 | P0 | **25 %** | Verrouillage AF après acquisition | Focus ne repart pas automatiquement au bout de 5 s |
| [ ] | D-08 | P0 | **25 %** | Verrouillage AE distinct | Verrouiller la lumière sans modifier le focus |
| [ ] | D-09 | P0 | **25 %** | Mise au point manuelle dioptries | Vérifier plage camera2 et résultat net S21 |
| [ ] | D-10 | P0 | **25 %** | ISO et shutter manuels | Plages Camera2 vérifiées sur objectif |
| [ ] | D-11 | P0 | **10 %** | AF continu et zone AF auto | Transition sûre vers contrôle continu |
| [ ] | D-12 | P0 | **10 %** | AE adaptative sur zone jour/nuit | AE suit les nuages tout en préservant AF |
| [ ] | D-13 | P1 | **0 %** | Compensation exposition ±EV | Valeurs sur plages capacités OEM |
| [ ] | D-14 | P1 | **0 %** | Affichage ISO/shutter réel | Résultat CaptureResult synchronisé et affiché |
| [ ] | D-15 | P1 | **0 %** | Mise au point en mètres en plus de dioptries | UI claire y compris infini |
| [ ] | D-16 | P1 | **0 %** | Histogramme et clipping | Détection hautes lumières/perte détails |
| [ ] | D-17 | P1 | **0 %** | AWB Auto / Lock indépendant | Verrouillage réel contrôlé par capture result |
| [ ] | D-18 | P1 | **0 %** | Kelvin / gains AWB manuels si supportés | Aucune fausse commande OEM |
| [ ] | D-19 | P1 | **0 %** | Zone AWB distincte si prise en charge | Affichage et support MAX_REGIONS_AWB |
| [ ] | D-20 | P0 | **10 %** | Mapping rotation/crop aperçu vers capteur | Points au même lieu après rotation et changement lens |
| [ ] | D-21 | P0 | **10 %** | Restauration des états AF/AE après nuit | Vérifier hardware après caméra rebind |
| [ ] | D-22 | P1 | **0 %** | Presets scène chantier | Sauver/télécharger réglages d'un chantier |
| [ ] | D-23 | P1 | **0 %** | Recalibrage focus sans chasse excessive | Recalibrage ponctuel optionnel et traçable |

### E. Programmation, écran OFF et consommation — 15 %

| Fait | ID | Priorité | Avancement | Fonction / garde-fou | Critère de réussite |
|---|---|---|---:|---|---|
| [ ] | E-01 | P0 | **25 %** | Plages quotidiennes heures début/fin | Mesure 07:00–18:00 sur vraie journée |
| [ ] | E-02 | P0 | **10 %** | Date de début et date de fin absolues | Arrêt planifié au bon jour et heure |
| [ ] | E-03 | P0 | **40 %** | Choix jours de la semaine | Weekends et jours fériés facultatifs |
| [ ] | E-04 | P0 | **55 %** | Gestion fuseau horaire et DST | Tests transitions hiver/été et heure reculée |
| [ ] | E-05 | P0 | **25 %** | Écran physiquement éteint pendant capture | Capture réelle sans écran noir simulé |
| [ ] | E-06 | P0 | **25 %** | Veille nocturne sans images | Pas de photos pendant fenêtre interdite |
| [ ] | E-07 | P0 | **25 %** | Caméra libérée durant nuit | Libération mesurée, réouverture vérifiée sur S21 |
| [ ] | E-08 | P0 | **10 %** | Alarme de réveil lors du matin | Réveil mesuré en Doze Android 15 |
| [ ] | E-09 | P0 | **0 %** | Reprise matin sans action utilisateur | Test S21 sur 3 jours avec écran OFF |
| [ ] | E-10 | P0 | **10 %** | Service FGS caméra et notification | Permissions Android 14/15, Android FGS validés |
| [ ] | E-11 | P0 | **0 %** | Reprise après kill par système | Retour sûr ou alerte action requise |
| [ ] | E-12 | P0 | **0 %** | Comportement après reboot / force-stop | Limites Android documentées et testées |
| [ ] | E-13 | P1 | **0 %** | Mode écran noir de secours | Ne jamais confondre noir OLED et écran OFF |
| [ ] | E-14 | P1 | **0 %** | Mode économie batterie adaptable | Compare caméra tenue ouverte vs réouverte |
| [ ] | E-15 | P1 | **0 %** | WakeLock borné et relâché | Pas de fuite WakeLock et reprise fiable |
| [ ] | E-16 | P1 | **0 %** | Heures exactes vs réveil Doze différé | Mesure retard et badge de précision |
| [ ] | E-17 | P1 | **40 %** | Schedule persistant après mise à jour app | Projet et horaires inchangés |
| [ ] | E-18 | P1 | **0 %** | Auto-start seulement sous permissions autorisées | Aucun contournement du modèle Android |
| [ ] | E-19 | P2 | **0 %** | Pilotage appareils gérés/kiosk optionnel | Documentation et tests device-owner |
| [ ] | E-20 | P1 | **0 %** | Comparaison consommation 24h 3 stratégies | Graphes Wh nuit noire/écran OFF/caméra idle |

### F. Fiabilité, sécurité matérielle et récupération — 22 %

| Fait | ID | Priorité | Avancement | Fonction / garde-fou | Critère de réussite |
|---|---|---|---:|---|---|
| [ ] | F-01 | P0 | **25 %** | Protection thermique initiale | Pause immédiate à statut SEVERE |
| [ ] | F-02 | P0 | **55 %** | Hystérésis thermique et cooldown | Pas de boucle chaud/refroidi |
| [ ] | F-03 | P0 | **25 %** | Vérification espace disponible | Arrêt sûr avec réserve disque |
| [ ] | F-04 | P0 | **25 %** | Batterie faible et charge détectée | Pause sûre quand non branché seuil atteint |
| [ ] | F-05 | P0 | **25 %** | Tentatives caméra bornées | Max échecs et délai exponentiel testés |
| [ ] | F-06 | P0 | **25 %** | Watchdog capture bloquée | Timeout photo et état recover explicite |
| [ ] | F-07 | P0 | **40 %** | Prévention ANR UI | Encodeur et I/O hors thread UI validés |
| [ ] | F-08 | P0 | **10 %** | Prévention OOM/mémoire bornée | 48h sans croissance mémoire significative |
| [ ] | F-09 | P0 | **10 %** | File capture à taille bornée | Une image active; retard => skip et audit |
| [ ] | F-10 | P0 | **40 %** | Écriture atomique fsync et réconciliation | Kill en pleine sauvegarde, aucune image valide perdue |
| [ ] | F-11 | P0 | **40 %** | Manifest/journal d'intégrité durable | Reconstruction après corruption partielle |
| [ ] | F-12 | P0 | **10 %** | Photos originales immuables | Export raté ne modifie aucun hash source |
| [ ] | F-13 | P0 | **40 %** | Journal d'événements rotatif | Horodatage, frame IDs, thermals, erreurs et reprise |
| [ ] | F-14 | P0 | **40 %** | Diagnostics exportables et consentement | ZIP anonymisé pour analyse |
| [ ] | F-15 | P0 | **0 %** | Gestion sous tension USB instable | Débranchement/connexion repris ou pause sûre |
| [ ] | F-16 | P0 | **0 %** | Détection surchauffe en charge | Pause même si batterie 100% |
| [ ] | F-17 | P0 | **10 %** | Gestion caméra occupée par autre app | Retenter borné puis message explicite |
| [ ] | F-18 | P0 | **0 %** | Stockage SD retiré ou en lecture seule | Aucune écriture hasardeuse, alerte et reprise |
| [ ] | F-19 | P0 | **0 %** | Limites poids vidéo et support format | Préflight allocation, codec et fichiers |
| [ ] | F-20 | P0 | **40 %** | Surveillance progression indépendamment UI | Démontre captures sans activité en avant-plan |
| [ ] | F-21 | P1 | **0 %** | Recovery après permission retirée | Erreur claire sans boucle |
| [ ] | F-22 | P1 | **0 %** | Eviter mise en veille optimisations OEM | Samsung policies testées et notifications adaptées |
| [ ] | F-23 | P0 | **25 %** | Gestion stockage endommagé et JPEG tronqué | Fichier partiel détecté et quarantiné |
| [ ] | F-24 | P0 | **40 %** | Comptabilisation frames manquées | Journal taux et plus gros intervalle |
| [ ] | F-25 | P1 | **0 %** | Gestion peu d'espace RAM et pressure | Dégrader sans perdre photos sauvegardées |
| [ ] | F-26 | P1 | **40 %** | Concurrence capture/export/import | Capture prioritaire, aucune deadlock |
| [ ] | F-27 | P1 | **0 %** | Protection contre boucles de restart | Budget global de tentatives et arrêt explicite |
| [ ] | F-28 | P1 | **0 %** | Téléphone trop chaud pour toucher | Notification/action sécurité, pas override |
| [ ] | F-29 | P0 | **40 %** | Mode dégradation contrôlée | Captures ralentissent, reprennent avec hysteresis |
| [ ] | F-30 | P1 | **0 %** | Test mémoire/thermique avec instrumentation | Rapports p95, courbes et seuils |

### G. Export, formats, effets et montage vidéo — 29 %

| Fait | ID | Priorité | Avancement | Fonction / garde-fou | Critère de réussite |
|---|---|---|---:|---|---|
| [ ] | G-01 | P0 | **40 %** | Choix export photos ou vidéo | Les deux produisent des fichiers lisibles vérifiés |
| [ ] | G-02 | P0 | **55 %** | MP4 H.264 | ffprobe vérifie codec, durée et lecture |
| [ ] | G-03 | P0 | **55 %** | Vidéo sans effet (None) | Chaque image correspond à un original |
| [ ] | G-04 | P0 | **55 %** | Motion Blur temporel | N images intégrées avec réglage force |
| [ ] | G-05 | P0 | **55 %** | Light Trails | Composition max-luminance, intensité configurable |
| [ ] | G-06 | P0 | **55 %** | Force et fenêtre des effets | 1 3 5 7 9 images, tests artefacts |
| [ ] | G-07 | P1 | **0 %** | Effet poids temporels et ghosting réglable | Réglages clairs vs moyenne brute |
| [ ] | G-08 | P0 | **55 %** | Encodeur matériel MediaCodec/MediaMuxer | MP4 produit sans stall et métadonnées correctes |
| [ ] | G-09 | P0 | **25 %** | Résolution 1080p | Sortie validée sur S21 |
| [ ] | G-10 | P0 | **25 %** | Résolution 4K si supportée | Source réellement 4K et encodeur validé |
| [ ] | G-11 | P1 | **25 %** | Résolution 8K si supportée | Source et encodeur 8K vérifiés, refus propre sinon |
| [ ] | G-12 | P0 | **40 %** | FPS 24 25 30 60 | Durée=N/FPS vérifiée |
| [ ] | G-13 | P1 | **10 %** | FPS 29.97 réel | Horodatage fractionnaire correct |
| [ ] | G-14 | P0 | **25 %** | Bitrate Auto Low Medium High | Taille et qualité comparées |
| [ ] | G-15 | P1 | **0 %** | Bitrate personnalisé | Champ Mbps borné selon codec |
| [ ] | G-16 | P0 | **25 %** | Choix codecs AVC HEVC AV1 selon support | Liste vérifiée matériel et MP4 testées |
| [ ] | G-17 | P1 | **0 %** | Codec autres/APV si OEM le permet | Capability gating et test |
| [ ] | G-18 | P0 | **25 %** | Overlay timestamp couleur/taille/position | Visuellement correct avec timezone et EXIF |
| [ ] | G-19 | P1 | **0 %** | Prévisualisation effets et rendu court | Prévisualiser sans encoder toute séquence |
| [ ] | G-20 | P0 | **40 %** | Export atomique, suppression vidéo partielle | Interruption laisse originaux et aucun MP4 corrompu public |
| [ ] | G-21 | P0 | **40 %** | Progression et annulation export | Stop propre, reprise éventuelle |
| [ ] | G-22 | P1 | **25 %** | Optimisation GPU et cache image pour longues séquences | 10k images sans OOM et temps mesuré |
| [ ] | G-23 | P1 | **0 %** | Orientation, recadrage et aspect export | Conserver composition 16:9 ou 9:16 |
| [ ] | G-24 | P1 | **0 %** | Lecture MP4 vérifiée sur Android/Windows | Compatibilité H264 et HEVC testée |
| [ ] | G-25 | P1 | **0 %** | Export inversé/accélération | Reverse et speed ramps sans toucher originaux |
| [ ] | G-26 | P1 | **0 %** | Anti-flicker au montage | Courbes luminosité corrigées sans débordement |
| [ ] | G-27 | P1 | **0 %** | Conversion RAW en MP4 | DNG décodé dans budget mémoire |

### H. Commandes externes, UI, ergonomie — 20 %

| Fait | ID | Priorité | Avancement | Fonction / garde-fou | Critère de réussite |
|---|---|---|---:|---|---|
| [ ] | H-01 | P0 | **25 %** | Interface mobile de capture basique | Start Stop Status Preview utilisables |
| [ ] | H-02 | P0 | **25 %** | Tap séparé focus / exposition | Marqueurs verts/jaunes corrects sur téléphone |
| [ ] | H-03 | P0 | **40 %** | Mises en garde fonctions non validées | Aucun label pro prêt avant tests |
| [ ] | H-04 | P1 | **25 %** | Volume +/− commande shutter | Start/stop et photo sans double déclenchement |
| [ ] | H-05 | P1 | **10 %** | Bluetooth remote standard | Média key event selon appareil testé |
| [ ] | H-06 | P1 | **0 %** | Bluetooth sans interface active | Capacités Android et permissions validées |
| [ ] | H-07 | P1 | **0 %** | Interface Wi-Fi locale contrôle et diagnostic | API locale authentifiée, sans dépendance Internet |
| [ ] | H-08 | P1 | **0 %** | Vue dernière photo remote | Pas de transfert d'originaux à chaque polling |
| [ ] | H-09 | P1 | **0 %** | Authentification/pairing remote | Token rotation et réseau local uniquement |
| [ ] | H-10 | P1 | **0 %** | Historique erreurs et état reprise | Affichage clair avec cause et action |
| [ ] | H-11 | P1 | **0 %** | Notification action Pause/Stop | Boutons fiables même écran éteint |
| [ ] | H-12 | P1 | **55 %** | Navigation projets / export ergonomique | Workflow complet avec arrière-plan |
| [ ] | H-13 | P1 | **55 %** | Réglages persistants et presets | Après reboot et update conservés |
| [ ] | H-14 | P1 | **0 %** | Aide contextuelle et valeurs supportées | Explication dioptre, AE, RAW, codec et nuit |
| [ ] | H-15 | P1 | **25 %** | Accessibilité et grands éléments | TalkBack, contraste et cibles tactiles |
| [ ] | H-16 | P2 | **25 %** | Thèmes sombre/clair et langues | FR, EN, DE intégralement traduits |
| [ ] | H-17 | P1 | **55 %** | Bouton test de capture et qualité | Vérifier zone, exposition avant session |
| [ ] | H-18 | P1 | **0 %** | Avertissements chaleur et stockage proactifs | Badges couleur et notification claire |

### I. Confidentialité, sécurité, droits, commercialisation — 19 %

| Fait | ID | Priorité | Avancement | Fonction / garde-fou | Critère de réussite |
|---|---|---|---:|---|---|
| [x] | I-01 | P0 | **100 %** | Politique indépendante sans APK propriétaire en Git | Consigne présente dans AGENTS |
| [ ] | I-02 | P0 | **10 %** | Audit permissions caméra/notifications/stockage | Aucune permission excessive ni contournement |
| [ ] | I-03 | P0 | **10 %** | Stockage par défaut local | Aucun upload sans consentement |
| [ ] | I-04 | P0 | **0 %** | Pas de secrets ni clés signées dans le dépôt | Scan secrets et historique |
| [ ] | I-05 | P0 | **0 %** | TLS/pairing pour contrôle Wi-Fi | Requêtes authentifiées, scope LAN |
| [ ] | I-06 | P0 | **0 %** | Permissions SAF minimales | Accès limité à arbres sélectionnés |
| [ ] | I-07 | P1 | **0 %** | Prévention path traversal import/export | Fichiers invalides refusés sans écrasement |
| [ ] | I-08 | P1 | **0 %** | Vérification dépendances et vulnérabilités | Audit SBOM et CVE |
| [ ] | I-09 | P1 | **0 %** | Sécurité des journaux et métadonnées EXIF | Pas de données privées exposées par défaut |
| [x] | I-10 | P0 | **100 %** | Choix code propriétaire / dépôt privé documenté | Politique de base présente dans spec et README |
| [ ] | I-11 | P1 | **0 %** | Gestion licence logicielle et copyrights | Licence approuvée par propriétaire |
| [ ] | I-12 | P1 | **0 %** | Privacy policy / conformité stores | Texte et collecte réelle audités |
| [ ] | I-13 | P1 | **0 %** | Séparation versions debug / commerciale | Aucun debug secret dans builds publiées |
| [ ] | I-14 | P1 | **0 %** | Signatures et mises à jour sécurisées | Clés protégées et stratégie rotation |
| [ ] | I-15 | P2 | **0 %** | Gestion crash telemetry opt-in | Aucune transmission sans consentement |

### J. Tests, mesures, robustesse validée et release — 11 %

| Fait | ID | Priorité | Avancement | Fonction / garde-fou | Critère de réussite |
|---|---|---|---:|---|---|
| [ ] | J-01 | P0 | **55 %** | Tests purs Kotlin politique calendrier | Tous scénarios exécutés et journal CI vert |
| [x] | J-02 | P0 | **100 %** | Tests Android JVM exécutables | Gradle testDebugUnitTest vert |
| [ ] | J-03 | P0 | **55 %** | Tests instrumentation caméra simulée | Émulateur et fausses erreurs validés |
| [ ] | J-04 | P0 | **0 %** | APK compile et s'installe S21 | Installation et lancement constatés |
| [ ] | J-05 | P0 | **0 %** | Photo réelle / métadonnées / fichiers | Capture JPEG décodable avec EXIF |
| [ ] | J-06 | P0 | **0 %** | Tap AF AE deux zones réelles | Test mur/sky sur S21 |
| [ ] | J-07 | P0 | **0 %** | Exposition et focus manual mesurés | CaptureResult + photos correspondantes |
| [ ] | J-08 | P0 | **0 %** | Motion blur/trails export vérifié | ffprobe et comparaison image référence |
| [ ] | J-09 | P0 | **0 %** | Test capture 1h | Taux frames vs plan, zéro perte inexpliquée |
| [ ] | J-10 | P0 | **0 %** | Test capture 8h écran OFF | Pas crash, logs et durée batterie |
| [ ] | J-11 | P0 | **0 %** | Test veille/reprise chaque matin 72h | Horaires exacts, écran physiquement OFF |
| [ ] | J-12 | P0 | **0 %** | Test 7j continu planifié | Taux pertes, pics température, histogrammes |
| [ ] | J-13 | P0 | **0 %** | Test batterie drain et recharge | Seuils sûrs et reprise sans corruption |
| [ ] | J-14 | P0 | **0 %** | Test thermique léger à sévère | Pause sûre et hysteresis vérifiées |
| [ ] | J-15 | P0 | **0 %** | Test disque plein / carte retirée | Photos existantes intactes, reprise explicite |
| [ ] | J-16 | P0 | **0 %** | Test kill process/force stop/reboot | Comportement honnête et sûr |
| [ ] | J-17 | P0 | **0 %** | Test Doze et OEM Samsung | Latence de réveil mesurée |
| [ ] | J-18 | P1 | **0 %** | Test mémoire 10k/50k images | Borne RAM et export sans crash |
| [ ] | J-19 | P1 | **0 %** | Test interruptions export vidéo | MP4 partielle nettoyée, originaux hachés |
| [ ] | J-20 | P1 | **0 %** | Tests de migration projets et versions | Aucune perte après upgrade |
| [ ] | J-21 | P1 | **0 %** | Test multi-objectifs et SD externe | Toutes capacités S21 observées |
| [ ] | J-22 | P1 | **0 %** | Rapport benchmark qualité Velocity Lapse | Comparaison fonctionnelle sans copier code |
| [ ] | J-23 | P0 | **0 %** | Release gate G0/G1/G2/G3/G4/G5 | Aucune version stable sans preuves matérielles |
| [ ] | J-24 | P1 | **55 %** | CI prévention régressions et matrice erreurs | PR bloquée si tests échouent |

### K. Documentation, gouvernance et suivi du projet — 54 %

| Fait | ID | Priorité | Avancement | Fonction / garde-fou | Critère de réussite |
|---|---|---|---:|---|---|
| [x] | K-01 | P0 | **100 %** | README de présentation | Point d'entrée disponible |
| [x] | K-02 | P0 | **100 %** | PROJECT_SPEC contraintes centrales | Exigences et gates numérotés |
| [x] | K-03 | P0 | **100 %** | AGENTS instructions développeurs | Obligation de lire spec et preuve tests |
| [x] | K-04 | P0 | **100 %** | ARCHITECTURE service jour/nuit | Diagramme et limites Android documentés |
| [x] | K-05 | P0 | **100 %** | TEST_PLAN prérequis de validation | T01–T16 et métriques disponibles |
| [x] | K-06 | P0 | **100 %** | PRO_PARITY catalogue Velocity PRO | Toutes options fournies par utilisateur listées |
| [x] | K-07 | P0 | **100 %** | CAMERA_CONTROL_UX points AF AE | Comportement indépendant et retours spécifiés |
| [x] | K-08 | P0 | **100 %** | MASTER_TODO avancement détaillé | Chaque ticket avec ID priorité score et preuve |
| [ ] | K-09 | P0 | **0 %** | Journal de décisions ADR versionné | Chaque choix retenu/rejeté motivé |
| [ ] | K-10 | P0 | **0 %** | Registre risques et limitations Android | Chaque risque probabilité/impact/mitigation |
| [ ] | K-11 | P1 | **0 %** | Changelog technique et produit | Commits reliés aux items et releases |
| [ ] | K-12 | P1 | **0 %** | Guide installation S21 et permissions | Tutoriel avec capture attendue |
| [ ] | K-13 | P1 | **0 %** | Guide exploitation chantier multi-semaines | Alimentation, fixation, chaleur, stockage, sauvegardes |
| [ ] | K-14 | P1 | **0 %** | Guide manuel focus et exposition | Zones indépendantes, AE extérieure, dioptries |
| [ ] | K-15 | P1 | **0 %** | Documentation API contrôle local | Pairing, auth, erreurs |
| [ ] | K-16 | P1 | **0 %** | Registre preuves test appareils | OS, build, hash APK, logs et mesures |
| [ ] | K-17 | P1 | **0 %** | Checklist release sécurité/qualité | Barrières bloquantes explicites |
| [ ] | K-18 | P1 | **0 %** | Inventaire dépendances et licences | SBOM lisible et contrôlé |

<!-- PROGRESS-LIST-END -->

## Changement de pourcentages / discipline de développement

À chaque PR : 1. indiquer les ID couverts ; 2. ajouter tests reproductibles ; 3. fournir lien commit, logs CI, APK SHA et modèle Android, si applicable ; 4. modifier scores uniquement selon la grille ; 5. calculer de nouveau les moyennes des groupes et de l'ensemble ; 6. mettre à jour les limitations réelles et **ne pas fusionner les fonctionnalités critiques sans tests**. Une PR doc peut atteindre 100 % uniquement pour son document, pas pour le code qu'elle décrit.

**Vérification exhaustive** : les tâches PRO-001..018 restent aussi normatives dans [PRO_PARITY.md](PRO_PARITY.md) et les exigences CAM/REL/EXP dans [PROJECT_SPEC.md](PROJECT_SPEC.md). Le présent tableau détaillé ne remplace pas ces critères ; le développeur doit croiser les trois sources avant de considérer une tâche finie.

Dernière source de mesure : [build #40972789344, 2026-10-30](BUILD_REPORT_2026-10-30.md) ; scores conservateurs **à revérifier à chaque commit**.
