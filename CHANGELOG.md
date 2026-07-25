# Changelog

Toutes les évolutions notables de ce projet sont consignées ici.

Format : [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/).
Versionnement : [SemVer](https://semver.org/lang/fr/). Le `versionName` des APK
publiés suit `1.1.<numéro de run CI>` ; les entrées ci-dessous regroupent les
commits **par lot de travail**, pas par commit.

---

## [Non publié]

### Added
- **Couche de capture multi-sources** (lot AND-06) : une porte d'entrée pour les
  notes prises ailleurs, en deux taps et sans quitter l'application d'origine.
  - **Capture depuis n'importe quelle app** : partage (`ACTION_SEND`) et sélection
    de texte (`ACTION_PROCESS_TEXT`), filtre `text/plain` strict — aucune image,
    aucun OCR. Une feuille basse montre l'aperçu, un bouton enregistre, et on
    revient immédiatement à l'app d'origine (l'application complète ne s'ouvre pas,
    la capture ne laisse pas de fenêtre dans les récents).
  - **Une note brute n'est pas une boucle** : les captures vivent dans leur propre
    table, en amont du registre. Rien n'entre dans les boucles sans une action
    explicite — sans cette séparation, on remplacerait un éparpillement par un
    dépotoir.
  - **Boîte de réception** : badge chiffré dans la barre (masqué si vide), liste du
    plus récent au plus ancien, filtres par statut, recherche plein texte,
    consultation du texte intégral **non modifiable**, et « Créer une boucle » qui
    ouvre le formulaire existant pré-rempli. La capture passe alors en `TRAITEE` et
    garde l'identifiant de la boucle produite.
  - **Aucune suppression de capture, jamais** (invariant I14) : on ignore, on
    réactive, on ne supprime pas. Le DAO n'expose aucune méthode d'effacement, et
    un test le vérifie par réflexion.
  - **Aucune analyse du contenu dans l'application** (invariant I15) : pas de
    mots-clés, pas d'échéance devinée, aucune boucle créée automatiquement.
    L'analyse se fait dehors et revient par la supervision.
  - **Déduplication par empreinte** : SHA-256 du texte normalisé (espaces, retours
    et forme Unicode neutralisés ; casse et ponctuation conservées). Repartager la
    même note informe « déjà capturé le … depuis … » au lieu de créer un doublon
    ou de lever une erreur.
  - **Export « lot d'analyse »** (`lot-analyse-<aaaaMMjj-HHmm>.json`) : les captures
    brutes sortent pour être analysées ailleurs, et passent en `EXPORTEE`
    (réversible si le lot n'a pas servi). Backup strict avant écriture.
  - **Direct Share** : `share-target` et raccourci publié à l'exécution, pour que
    l'application remonte dans la feuille de partage au lieu d'y croupir des
    semaines.
  - Migration Room **6 → 7**. 30 tests supplémentaires (127 au total).
- **Synchronisation bidirectionnelle entre appareils** (lot AND-08), par dossier
  partagé et sans une ligne de réseau :
  - **Protocole à un écrivain par fichier** : chaque appareil dépose son état
    complet (format v3) dans `etat-<CODE>.json` et ne modifie jamais le fichier
    d'un autre. Cette seule règle élimine les conflits d'écriture — la fusion
    redevient un problème de lecture, donc de calcul (invariant I11). Le dossier
    est choisi une fois (SAF, autorisation persistante) ; c'est l'application de
    synchronisation de l'utilisateur qui transporte les fichiers.
  - **Moteur de fusion en fonction pure** : `calculerFusionSync()` ne fait rien,
    elle rend un plan que le repository applique en une transaction. C'est ce qui
    permet de vérifier en JVM les deux propriétés difficiles — **idempotence**
    (fusionner deux fois le même fichier ne change rien la seconde fois) et
    **symétrie** (A fusionne B, B fusionne A, les deux bases convergent).
  - **Règles de résolution** : union dédupliquée des mouvements et journaux ;
    une clôture l'emporte toujours sur une version active, quelles que soient les
    dates ; au-delà de 60 s d'écart le plus récent gagne ; en deçà, conflit et
    aucune écriture. `id`, `creee` et `source` ne sont jamais écrasés.
  - **Aucun écrasement silencieux** (invariant I13) : chaque champ remplacé écrit
    un mouvement « titre : "…" remplacé par "…" (sync depuis PRO) ».
  - **Tombstones exploitées** : une suppression plus récente que la modification
    entrante empêche la résurrection ; plus ancienne, la boucle revient et la
    trace disparaît. Une suppression faite ailleurs sur une boucle encore vivante
    ici n'efface **rien** — elle ouvre un arbitrage, parce qu'effacer la boucle
    emporterait ses mouvements et ses journaux de clôture.
  - **Garde-fou d'horloge** : un fichier qui se déclare exporté avec plus de
    10 minutes d'avance interrompt la fusion et demande confirmation — tout
    l'arbitrage temporel repose sur ces dates.
  - **Écran Synchronisation** : dossier, identité, bouton manuel, compte rendu,
    conflits à arbitrer (réutilisant l'affichage de diff du mode « Fusionner ») et
    **historique consultable et non modifiable** — nouvelle table
    `evenements_sync`, jamais purgée. Migration Room **5 → 6**.
  - **Backup strict forcé avant toute fusion** (invariant I12) : échec de
    sauvegarde = rien n'est fusionné.
  - 27 tests supplémentaires (97 au total), dont l'idempotence et la symétrie.
- **Socle multi-appareils** (lot AND-07) — le registre devient duplicable sans
  perte, sans réseau et sans coordination :
  - **Identité de l'appareil** : un code de 1 à 4 lettres, saisi une fois sur un
    écran bloquant au premier lancement. Sur une base déjà peuplée (restauration,
    import), le préfixe dominant des identifiants existants est **suggéré**, pour
    que la séquence continue sans rupture.
  - **Identifiants préfixés par appareil** (`B-041`, `PRO-007`) : chaque appareil
    ne compte que les siens pour calculer le suivant. Deux appareils hors ligne
    ne peuvent plus émettre le même identifiant — la collision devient
    structurellement impossible (ADR-02 tranché, option B ; invariant I9). Les
    identifiants historiques ne sont **jamais** réécrits ; les non-conformes sont
    seulement signalés dans le rapport d'import.
  - **Horodatage de modification** : colonnes `modifieeLe` / `modifieePar`,
    estampillées à chaque écriture. `null` se lit comme « jamais modifiée » et
    retombe sur la date de création (données antérieures).
  - **Traces de suppression** (*tombstones*) : table `suppressions`, écrite dans
    la **même transaction** que la suppression. Une boucle effacée laisse
    désormais la trace de son effacement, au lieu de devenir indistinguable
    d'une boucle jamais reçue — sans quoi le premier import venu la
    ressusciterait (invariant I10). AND-07 les enregistre et les transporte ;
    leur arbitrage à la fusion reste à faire.
  - **Format d'échange v3** : racine enrichie de `codeAppareil`, `exporteLe` et
    `suppressions`. Les fichiers v1 et v2 restent importables sans changement.
  - Migration Room **4 → 5**. 15 tests supplémentaires (70 au total).

### Security
- **Le code appareil est exclu des sauvegardes système**, dans `backup_rules.xml`
  comme dans `data_extraction_rules.xml` (`<cloud-backup>` **et**
  `<device-transfer>`). Restaurer une sauvegarde sur un second téléphone n'en
  fait donc pas un clone qui émettrait les mêmes identifiants. L'exclusion
  Android étant à granularité de **fichier** et non de clé, cette identité vit
  dans un fichier de préférences dédié `registre-appareil.xml`.

### Changed
- **Charte graphique « Encre & Patine »** (piste 1a de la proposition
  graphique) : la barre de titre passe en Encre `#0B1620`, la couleur de fond de
  l'icône — l'application et son icône forment désormais la même famille. Les
  actions passent en patine (`#3E7A6C` en clair, `#79B4A6` en sombre), les
  échéances proches en bronze, les retards en `#B3544A` / `#D08379`. Les
  pastilles d'action deviennent des disques teintés au lieu de cercles bordés.
  La barre système suit l'Encre.
  Conséquence technique : les accents ne sont plus des constantes globales
  (ils diffèrent entre clair et sombre) mais sont fournis par le thème via
  `Mnemosyne.couleurs`.

### Added
- **Nouveau logo Mnemosyne** (double spirale, la goutte pénètre la première
  onde) : icône de lancement adaptative — marque Pierre `#E4E7E2` sur fond
  Encre `#0B1620`, dessinée en vecteur (net à toutes les densités, couche
  `monochrome` pour les icônes thématisées d'Android 13+). La marque apparaît
  aussi dans la barre de titre (trait 2,6, palier « 20 px » de la proposition
  graphique) et sur l'écran de premier lancement (trait 1,2). Sources du jeu
  d'icônes conservées dans `design/mnemosyne-icones/`.
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
- **Badges des statuts `proposee` et `rejetee`** : ils n'avaient ni libellé
  accentué (« Proposee ») ni couleur propre — une boucle **rejetée** portait la
  couleur du badge « Ouverte ». Elles ont désormais leur libellé (« Proposée »,
  « Rejetée ») et leur couleur ; un statut inconnu prend la couleur neutre des
  boucles fermées au lieu de celle d'« ouverte ».
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
