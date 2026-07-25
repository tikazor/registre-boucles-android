# Changelog

Toutes les évolutions notables de ce projet sont consignées ici.

Format : [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/).
Versionnement : [SemVer](https://semver.org/lang/fr/). Le `versionName` des APK
publiés suit `1.1.<numéro de run CI>` ; les entrées ci-dessous regroupent les
commits **par lot de travail**, pas par commit.

---

## [Non publié]

### Added
- **Nouveau logo Mnemosyne** (double spirale, la goutte pénètre la première
  onde) : icône de lancement adaptative — marque Pierre `#E4E7E2` sur fond
  Encre `#0B1620`, dessinée en vecteur (net à toutes les densités, couche
  `monochrome` pour les icônes thématisées d'Android 13+). La marque apparaît
  aussi dans la barre de titre et sur l'écran de premier lancement. Sources du
  jeu d'icônes conservées dans `design/mnemosyne-icones/`.
- **Tableau de bord cliquable** : les trois tuiles (« Ouvertes », « En retard »,
  « ≤ 7 jours ») deviennent des filtres. Un appui filtre la liste, un second
  revient à « Toutes ». La tuile active est signalée par un liseré et un fond
  teintés de sa propre couleur (palette inchangée). Deux nouveaux filtres :
  `EN_RETARD` et `BIENTOT`.
- **Documentation Diátaxis** (lot AND-05) : `docs/00_INDEX.md`,
  `docs/reference/data_model.md`, `docs/explanation/invariants.md`,
  `docs/explanation/architecture.md`, 4 guides `docs/how-to/`, ce `CHANGELOG.md`
  et `AGENTS.md` (contrat pour les futures sessions de travail).

### Fixed
- **Doublon du tableau de bord** : « Ouvertes » était affiché deux fois, en tuile
  et en filtre. La rangée de filtres ne porte plus que « Toutes » et
  « Fermées » ; chaque libellé apparaît désormais exactement une fois.
- **Cohérence indicateur ↔ filtre** : les catégories d'échéance sont définies une
  seule fois (`data/Echeance.kt`) et servent à la fois au compteur et au filtre,
  ce qui rend impossible qu'une tuile annonce un nombre différent du contenu
  qu'elle affiche. Couvert par `EcheanceTest`.

---

## AND-04 — Verrouillage de la supervision & hygiène — 2026-07-25

### Security
- **La supervision devient une propriété de l'application.** Toute boucle
  nouvelle déclarée `source: "ia"` est forcée au statut `proposee` à l'import,
  quel que soit le statut du fichier, sur les trois modes. Un JSON
  `source: "ia"` + `statut: "ouverte"` n'entre plus directement dans le registre
  actif. L'intention du producteur est journalisée par un mouvement de trace.
- **Garde CI permanente anti-réseau** : le build échoue si `INTERNET` apparaît
  dans le **manifest mergé** (app + dépendances) ou si une dépendance réseau est
  détectée. Contrôle exécuté avant la publication de la Release.

### Added
- Trace automatique à l'acceptation d'une proposition (mouvement
  « Proposition IA acceptée », variante « … après amendement »).
- Marqueur « statut inconnu » sur les cartes dont le statut n'est pas reconnu.

### Changed
- `accepter()` et `rejeter()` utilisent désormais la sauvegarde **stricte** :
  échec de sauvegarde = action abandonnée, erreur remontée à l'écran.
- Anti-rafale des backups : un backup de moins de 5 minutes est réutilisé au
  lieu d'en créer un nouveau (les imports en forcent toujours un frais).
  Rotation inchangée à 10.
- README étoffé (pitch, garantie hors-ligne, liens documentation).

### Fixed
- Le filtre « Fermées » utilise `estTerminal()` au lieu de `!estActive()` : une
  boucle au statut inconnu n'y atterrit plus par défaut et reste visible dans
  « Toutes ».

## AND-03 — Socle d'alimentation IA supervisée (hors ligne) — 2026-07-24

### Added
- **Provenance des boucles** : colonne `source` (`user` / `ia` / `import`),
  migration Room **3 → 4**, marqueur « IA » sur les cartes.
- **Statuts `proposee` et `rejetee`.** `proposee` n'est ni active ni terminale
  et reste hors de la liste et du widget ; `rejetee` est terminale et exige un
  motif journalisé.
- **Écran Supervision** : file des propositions, actions Accepter / Amender /
  Rejeter, avec sauvegarde avant chaque action.
- **Troisième mode d'import « Fusionner »** : mouvements et journaux ajoutés
  sans doublon et jamais supprimés ; arbitrage par boucle « garder l'existant /
  prendre l'entrant » avec diff ; `id`, `creee`, `statut` et `source` de
  l'existant toujours préservés.
- `docs/schema.md` — contrat JSON complet et auto-suffisant.
- `samples/ia-propositions-exemple.json` — jeu d'essai non nominatif.

### Changed
- L'écriture d'un état terminal est factorisée dans
  `executerTransitionTerminale()` ; `executerCloture()` y délègue. Une
  proposition ne peut pas être clôturée sans être acceptée d'abord.

## AND-02 — Intégrité des données & résilience — 2026-07-24

### Fixed
- **Deux définitions contradictoires d'« active » unifiées** dans
  `Statut.estActive()` : une boucle `defaut_applique` était active dans la liste
  et invisible du widget.
- **L'export ne perd plus de données** : les journaux de clôture y sont inclus,
  et `tiers` reste une chaîne (l'aller-retour export → import « Écraser » ne
  remplace plus les preuves par un texte générique).

### Added
- Format JSON canonique unique `{version, boucles, journaux}` (`version: 2`),
  partagé par l'export et les backups ; l'ancien format reste accepté.
- Sauvegarde de sécurité **avant** tout import destructif : si elle échoue,
  l'import est annulé.
- Backups exportables hors de l'app (Réglages → « Exporter le dernier backup »)
  et inclus dans la sauvegarde système Android.
- 15 tests unitaires JVM (aller-retour JSON, statuts, dates d'import, génération
  d'identifiants) — 18 au total.
- Schéma Room exporté et versionné (`app/schemas/`).
- `docs/decisions.md` — 4 ADR ouverts.

### Changed
- Les échecs d'écriture de fichier sont journalisés (`Log.e`) et remontés à
  l'utilisateur avec un message réel, au lieu d'un booléen silencieux.
- CI durcie : `./gradlew test` **avant** le build (les tests n'avaient jamais
  tourné en CI), rapport lint en artifact, `versionCode`/`versionName`
  dynamiques issus du numéro de run.

## AND-01 — Journal de clôture, typage du milieu, backup versionné — 2026-07-24

### Added
- **Entité `Journal`** et invariant fondateur : une clôture **exige** une entrée
  de journal (type + texte). `executerCloture()` est l'unique chemin vers
  `fermee`. Migration Room **2 → 3**. Écran d'historique des clôtures.
- **`milieu` typé** (enum `PRO` / `GOUVERNANCE` / `PROJET` / `PERSO`, tolérant
  aux anciennes valeurs) + filtre par milieu dans la liste. Migration **1 → 2**.
- **Backup local versionné** (boucles + journaux) à chaque clôture et depuis les
  Réglages ; rotation des 10 plus récents.
- Premier test unitaire JVM : « clôturer sans journal est impossible ».
- Import : entrée de journal par défaut pour toute boucle importée déjà fermée ;
  les fichiers de backup redeviennent réimportables.

### Changed
- Édition d'une boucle existante depuis les cartes (icône crayon), avec
  raccourci « Enregistrer » dans l'en-tête quand la feuille est partiellement
  ouverte.

## v1 — Socle initial — 2026-07-23 → 2026-07-24

### Added
- Application Android native : Kotlin, Jetpack Compose, Room, widget Glance.
- Import JSON au premier lancement, puis choix **Ajouter / Écraser** ; export
  JSON depuis la liste.
- Écran Liste : bandeau de statistiques, filtres par statut, recherche par
  mots-clés, filtres persistants en navigation.
- Widget défilant reprenant les cartes de l'app, avec clôture directe et
  deep-link vers la boucle concernée.
- Création en bottom sheet ; listes à choix unique (Type / Tiers / Milieu) et
  écran de configuration.
- Journal de crash local consultable (appui long sur le titre).
- CI GitHub Actions : build d'un APK signé et publication d'une Release à chaque
  push sur `main`.

### Changed
- Refonte visuelle « v2 » : charte marine/sarcelle, cartes en accordéon
  (remplaçant l'écran de détail), mode sombre.
- Renommage de l'application en **Register Mnemosyne**.
