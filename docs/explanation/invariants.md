---
title: Invariants structurels de l'application
type: explanation
status: current
updated: 2026-07-25
source: app/src/main/java/com/pontat/registreboucles/data/{Cloture,Statut,Fusion,FusionSync,DossierSync,EvenementSync,Coercition,Backup,CodeAppareil,Identifiants,Suppression,Capture,StatutCapture,EmpreinteCapture,PreparationCapture,CaptureBoucle,SupervisionCaptures,BoucleRepository,BoucleDao}.kt, app/src/main/AndroidManifest.xml, app/src/main/res/xml/{backup_rules,data_extraction_rules}.xml, .github/workflows/build.yml, app/src/test/java/**
---

# Invariants structurels

Les règles que l'application garantit **par construction**, pas par discipline
de l'utilisateur. Chacune a un énoncé, une raison d'être, un point
d'application dans le code, une couverture de test réelle, et ce qui la
casserait.

Ce document est la référence à lire **avant** de modifier le modèle, la
supervision, l'import ou la CI. Un lot qui touche à un invariant met ce
document à jour dans le même lot.

> **Vocabulaire.** « Terminal » = `fermee`, `rejetee`, `defaut_applique`.
> « Actif » = `ouverte`, `en_cours`. Cf.
> [`../reference/data_model.md`](../reference/data_model.md) §5.1.

---

## I1 — Aucun état terminal sans entrée Journal

**Énoncé.** Toute écriture d'un statut terminal garantit une entrée `Journal`
(type + texte non vide). La garantie tient sur **les trois canaux d'écriture**,
par deux mécanismes :
- **Canal commande** (l'utilisateur agit depuis l'UI) : garantie par
  `executerTransitionTerminale()`, qui crée le journal **avant** d'écrire le
  statut et refuse un texte vide.
- **Canaux réplication et import** (application d'un état venu d'ailleurs) :
  garantie par `completerJournaux()`, qui **backfille** l'entrée manquante —
  toute boucle terminale sans journal reçoit une entrée par défaut.

Aucun statut terminal ne peut exister sans journal, **quel que soit le chemin**.

**Pourquoi.** C'est la doctrine de l'app : une boucle ne se ferme pas parce
qu'on s'en désintéresse, elle se ferme **contre une preuve**. Sans cette règle,
le registre deviendrait une liste de tâches ordinaire où l'on coche sans rendre
compte.

**Les trois canaux d'écriture.** Une écriture de statut n'a pas toujours la même
provenance, et la garantie ne passe pas par le même mécanisme selon le canal :
- **Commande** — l'utilisateur agit (UI). Gardes de transition strictes (I2),
  `executerTransitionTerminale` est le chemin.
- **Réplication** — synchronisation entre **mes** appareils via
  `etat-<CODE>.json` : on applique un état **déjà validé à l'origine**. Les
  gardes I2 ne sont pas rejouées (elles romperaient la convergence, cf. I2) ; la
  garantie de journal est portée par `completerJournaux()`.
- **Import** — fichier externe (produit par une IA ou édité à la main) : ce
  n'est **pas** de la réplication de confiance ; les écarts y sont signalés
  (cf. AND-10). La garantie de journal reste portée par `completerJournaux()`.

**Où.**
- Canal commande : `Cloture.kt` → `executerTransitionTerminale()` :
  `require(statutCible.estTerminal())`, `require(texte.isNotBlank())`, puis
  `insererJournal()` **avant** `mettreAJourBoucle()`. `executerCloture()` est un
  alias (`statutCible = FERMEE`) ; `rejeter()` dans `BoucleRepository` passe par
  cette fonction. C'est le seul chemin qui applique aussi les gardes I2.
- Canaux réplication et import : `BoucleRepository.completerJournaux()` est
  appelé sur l'application du plan de sync (`appliquerPlanSync`), sur l'arbitrage
  (`arbitrerPrendreDistant`) et sur les trois imports. Une boucle terminale sans
  journal y reçoit « Clôture importée (sans preuve d'origine) » (texte non vide).

**Testé par.** `ClotureTest` (3 tests) :
`cloture_cree_toujours_une_entree_journal_et_ferme`,
`fermeture_sans_texte_de_journal_est_impossible`,
`aucune_fermeture_ne_laisse_la_boucle_sans_journal`.
`TransitionStatutTest` : `rejeter_sans_motif_est_impossible`,
`rejeter_une_proposee_avec_motif_ecrit_journal_et_passe_a_rejetee`.

**Ce qui le casserait.** Un chemin d'écriture terminale qui n'appellerait **ni**
`executerTransitionTerminale` **ni** `completerJournaux` — typiquement un
`dao.mettreAJour(boucle.copy(statut = "fermee"))` ajouté dans un écran ou le
widget. Aucun test ne détecterait ce contournement : c'est une règle à tenir en
revue de code (cf. `AGENTS.md`).

---

## I2 — Garde-fous de transition (canal commande)

**Énoncé.** Les gardes de transition — `FERMEE` seulement depuis une boucle
**active**, `REJETEE` seulement depuis une boucle **`PROPOSEE`** — s'appliquent
au **canal commande** (I1). Une proposition ne peut donc pas être clôturée depuis
l'UI : il faut d'abord l'accepter.
Le **canal réplication** (synchronisation entre mes appareils,
`etat-<CODE>.json`) n'y est **pas** soumis, à dessein : il applique un état
**déjà validé à l'origine** sur l'appareil pair. Une boucle passée
`PROPOSEE → OUVERTE → FERMEE` là-bas arrive ici en un seul saut de statut ;
rejouer les gardes la rejetterait alors qu'elle est parfaitement légitime — on
romprait la convergence.
Le **canal import** (fichier externe, produit par une IA ou édité à la main)
n'est **pas** de la réplication de confiance : les écarts y sont signalés
(cf. AND-10, non livré).

**Pourquoi.** Sans ces gardes sur le canal commande, une proposition d'IA jamais
examinée pourrait être « clôturée » directement et rejoindre l'histoire du
registre comme un engagement réellement tenu : la supervision serait contournable
par le bas. Mais les rejouer sur le canal réplication serait une **régression**,
pas un durcissement — l'appareil pair a déjà fait passer la boucle par la
supervision, et lui réappliquer les gardes casserait la convergence légitime.
La confiance suit la provenance : réplication = confiance, import = méfiance.

**Où.** `Cloture.kt`, dans `executerTransitionTerminale()` : le `when` sur
`statutCible` vérifie l'état **courant** (`Statut.depuis(boucle.statut)`) avant
d'écrire ; `accepterProposition()` exige symétriquement `PROPOSEE`. Ces contrôles
ne s'exécutent que sur le **canal commande**. Le canal réplication applique le
plan de `calculerFusionSync` via `appliquerPlanSync`, qui adopte le statut de
l'émetteur **sans** rejouer le `when` — la garantie de journal restant portée par
`completerJournaux()` (I1). Le traitement des écarts d'import relève d'AND-10.

**Testé par.** `TransitionStatutTest` : `une_proposee_ne_peut_pas_etre_cloturee`,
`on_ne_peut_pas_rejeter_une_boucle_non_proposee`,
`accepter_une_boucle_non_proposee_est_refuse`.

**Ce qui le casserait.** Assouplir le `when` du canal commande (par exemple
accepter `REJETEE` depuis n'importe quel statut « pour corriger une erreur ») ;
ou, à l'inverse, **ajouter** ces gardes sur le canal réplication « par
cohérence » : une clôture légitime venue d'un appareil pair serait alors rejetée
et les deux bases cesseraient de converger. Si un besoin d'assouplissement du
canal commande apparaît, il justifie un lot dédié, pas un `require` retiré.

---

## I3 — Une seule définition d'« active »

**Énoncé.** « Active » est défini une seule fois, par `Statut.estActive()`
(`OUVERTE ∪ EN_COURS`). Les requêtes SQL du DAO sont le **miroir** de cette
définition, jamais une définition concurrente.

**Pourquoi.** L'audit du 24/07 a montré ce qui arrive quand elles divergent :
`ListeScreen` utilisait `statut != "fermee"` tandis que le DAO utilisait
`statut IN ('ouverte','en_cours')`. Une boucle en `defaut_applique` était donc
**active dans la liste et invisible du widget** — deux vérités pour la même
donnée. Corrigé en AND-02.

**Où.** `Statut.kt` (`estActive`, `estTerminal`, `estProposition` + extensions
sur `Boucle`). Côté SQL : `BoucleDao.compterActives()` et
`prochainesEcheances()` avec `statut IN ('ouverte','en_cours')`, précédées d'un
commentaire qui impose de rester synchronisées. Côté UI : `ListeScreen` filtre
via `estActive()` / `estTerminal()`, plus aucun littéral de statut.

**Testé par.** `StatutTest` : `estActive_et_estTerminal_sont_exclusifs`,
`proposee_n_est_ni_active_ni_terminale`, `valeur_stockee_est_reversible`
(garantit que `valeurStockee()` ↔ `depuis()` sont réversibles, donc que les
littéraux SQL correspondent bien aux valeurs écrites).

**Trou de couverture assumé.** Aucun test ne compare *automatiquement* le SQL du
DAO à `estActive()` : cela exigerait une base Room instrumentée. La
synchronisation repose sur le commentaire du DAO et la revue. Ajouter une valeur
à `Statut` sans mettre à jour les deux requêtes SQL est le scénario de
régression le plus probable de tout le projet.

**Extension aux catégories d'échéance.** Le même principe s'applique aux
catégories « en retard » et « ≤ 7 jours » du tableau de bord : elles sont
définies une seule fois dans `Echeance.kt` (`estEnRetard`,
`estEcheanceProche`), et `ListeScreen` s'en sert **à la fois** pour le compteur
affiché sur la tuile et pour le filtre que la tuile déclenche. Un indicateur ne
peut donc pas annoncer un nombre différent de ce que son filtre montre.
Couvert par `EcheanceTest` (5 tests), dont l'exclusivité retard/proche et
l'inclusion dans les actives.

---

## I4 — Backup avant toute écriture destructive

**Énoncé.** Aucun import ni aucune action de supervision n'écrit en base sans
qu'une sauvegarde complète ait réussi. Si la sauvegarde échoue, l'opération est
**abandonnée**.

**Pourquoi.** L'app est une mémoire, pas un brouillon : un JSON partiel plus un
clic ne doivent jamais pouvoir effacer l'histoire. Un backup qui échoue en
silence est pire qu'un backup absent, parce qu'il crée une fausse confiance.

**Où.** `BoucleRepository` :
- `creerBackupStrict()` lève `BackupException` (et `Log.e`) si l'écriture
  échoue ; `creerBackup()` est la variante best-effort (retourne `null`).
- Appelé en **première ligne** de `importerAjouter()`, `importerEcraser()`,
  `importerFusionner()` — avec `forcer = true` pour garantir un fichier frais —
  et de `accepter()` / `rejeter()` (AND-04, sans forçage : anti-rafale).
- `BoucleViewModel` intercepte l'exception et la remonte à l'UI
  (`erreurImport`, `erreurSupervision`) : l'échec est visible.
- Anti-rafale : un backup non forcé de moins de **5 minutes** est réutilisé
  (`doitReutiliserBackup`), pour que cinq actions d'affilée n'éjectent pas
  l'historique. Rotation : les **10** plus récents sont conservés.

**Testé par.** `BackupRafaleTest` (6 tests) couvre la décision d'anti-rafale
(réutilisation, `forcer`, backup ancien, aucun backup, nom illisible, calcul
d'âge).

**Trou de couverture assumé.** Le fait que les trois imports *appellent*
`creerBackupStrict()` n'est pas vérifié par un test (le `Repository` dépend de
Room et d'un `Context` Android ; il n'est pas testé en JVM). C'est garanti par
lecture du code uniquement.

**Ce qui le casserait.** Ajouter un nouveau chemin d'écriture destructive (un
4ᵉ mode d'import, une purge, une restauration) sans l'appel de backup en
première ligne.

---

## I5 — Zéro réseau, par construction

**Énoncé.** L'application est incapable de sortir du téléphone. Elle ne
déclare pas la permission `INTERNET`, n'embarque aucun client HTTP, aucun SDK
de LLM. L'IA est **hors** de l'app.

**Pourquoi.** Le registre contient des mentions de personnes suivies. La
garantie la plus solide n'est pas une politique d'usage, c'est une **incapacité
technique** : sans permission `INTERNET`, aucun code, même ajouté par erreur, ne
peut émettre une requête.

**Où.** `app/src/main/AndroidManifest.xml` ne contient aucune
`uses-permission android:name="android.permission.INTERNET"`. Aucune dépendance
réseau dans `app/build.gradle.kts`. Les propositions d'IA arrivent
exclusivement par un fichier JSON importé à la main (SAF).

**Vérifié par.** Un step CI **permanent** (AND-04), exécuté après
`assembleRelease` et **avant** la publication de la Release, qui échoue si :
- `android.permission.INTERNET` apparaît dans le **manifest mergé**
  (`app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`) —
  c'est là qu'une permission injectée par une dépendance apparaîtrait, invisible
  dans le source ;
- `app/build.gradle.kts` mentionne `okhttp`, `retrofit` ou `ktor-client`.

**Nuance honnête.** Le manifest mergé contient d'autres permissions, injectées
transitivement par Glance/WorkManager : `WAKE_LOCK`,
`ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, et
`…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Aucune n'autorise une sortie
réseau (`ACCESS_NETWORK_STATE` permet seulement de *lire* l'état de
connectivité) et aucun worker de synchronisation n'existe dans le projet. La
garde CI ne cible que `INTERNET`, le seul verrou réellement structurant.
→ *à confirmer* : faut-il élargir la garde à une liste blanche stricte de
permissions ? Non tranché.

**Ce qui le casserait.** Une dépendance qui déclare `INTERNET` dans son
manifest (la CI l'attraperait), ou un lot qui ajouterait la permission
« temporairement pour tester » (interdit permanent, cf. `AGENTS.md`).

---

## I6 — Supervision non contournable

**Énoncé.** Toute boucle **nouvelle** déclarée `source = ia` est forcée en
statut `proposee` à l'import, **quel que soit** le statut déclaré dans le
fichier. Elle passe obligatoirement par l'écran Supervision.

**Pourquoi.** Avant AND-04, la supervision reposait sur la bonne volonté du
producteur : un JSON avec `source: "ia"` et `statut: "ouverte"` entrait
directement dans le registre actif. La garantie « rien n'entre sans mon accord »
était donc déclarative. Elle est désormais **appliquée par l'app**.

**Où.** `Coercition.kt` → `coercerPropositionsIA(entrantes, idsExistants,
maintenant)`, fonction **pure**. Appelée par les trois imports du
`BoucleRepository` :
- `importerAjouter` / `importerFusionner` : `idsExistants` = ids réellement en
  base ⇒ seules les boucles **nouvelles** sont coercées ;
- `importerEcraser` : `idsExistants` = `emptySet()` (la base vient d'être
  vidée) ⇒ **toutes** les boucles IA sont coercées.

L'intention du producteur n'est pas perdue mais **journalisée** : si le statut
déclaré différait, un mouvement `declaration` est créé —
« Statut déclaré "…" ramené à "proposee" (source IA, supervision obligatoire) ».

**Testé par.** `CoercitionTest` (5 tests) : coercition + trace, pas de trace si
déjà `proposee`, boucle non-IA intacte, boucle **existante** non coercée (cas
Fusionner), mode Écraser coerçant tout.

**Ce qui le casserait.** Appeler un import sans passer par
`coercerPropositionsIA`, ou passer à `importerEcraser` la liste des ids
existants au lieu d'un set vide (les propositions IA entreraient alors actives).

---

## I7 — Fusion additive : on n'efface jamais (et ce que la fusion préserve selon le canal)

**Énoncé.** Aucune fusion n'efface de mouvement ni de journal, sur **tous les
canaux** : l'import de propositions (mode « Fusionner ») comme la réplication
entre appareils font une union dédupliquée sur `(boucleId, date, contenu)` et
`(boucleId, date, texte)`, jamais une suppression. Pour une boucle existante,
`id`, `creee` et `source` sont **toujours** préservés, sur les deux canaux. En
revanche `statut` n'est préservé que sur le **canal import de propositions** :
sur le canal réplication, le moteur de synchronisation adopte le statut distant
— un état terminal l'emporte, puis le dernier modifié gagne au-delà de 60 s.
C'est **I13**, non I7, qui régit le statut en réplication.

**Pourquoi.** Deux canaux, deux intentions. À l'import, une IA qui enrichit une
boucle existante doit pouvoir compléter la description sans jamais réécrire le
cycle de vie ni s'attribuer la provenance : le statut local fait foi. En
réplication, les appareils sont des pairs de confiance qui doivent converger ;
figer le statut y empêcherait une clôture faite sur un téléphone de se propager
à l'autre — exactement ce que I13 impose d'éviter. Ce qui ne varie pas d'un
canal à l'autre : un mouvement ou un journal déjà écrit est une trace
historique, que rien — ni un fichier importé, ni un fichier d'état pair — ne
supprime, et `id`/`creee`/`source` d'une boucle existante, jamais réécrits.

**Où.**
- **Import** — `Fusion.kt` → `calculerFusion()`, fonction **pure** : id absent ⇒
  boucle créée telle quelle (statut d'origine) ; id présent **et** choisi dans
  `prendreEntrant` ⇒ `existante.copy(...)` sur les **seuls** champs descriptifs
  (`type, titre, origine, echeance, tiers, preuveAttendue, blocage, impact,
  defaut, milieu`) — donc `id`, `creee`, `statut`, `source` restent intacts.
- **Réplication** — `FusionSync.kt` → `calculerFusionSync()` / `adopter()` :
  `locale.copy(...)` copie les mêmes champs descriptifs **plus `statut`**
  (règles 4a terminal / 4b dernier modifié), et laisse `id`, `creee`, `source`
  intacts (règle 4c, `diffsScalaires` les exclut). Chaque champ écrasé, statut
  compris, produit un mouvement de traçage (I13).
- **Les deux** — mouvements et journaux entrants ajoutés puis dédupliqués,
  jamais supprimés ; `calculerConflits()` (import) et la règle 4b (réplication)
  produisent le diff champ par champ présenté à l'arbitrage.

**Testé par.** `FusionTest` (7 tests), dont
`prendre_entrant_remplace_les_scalaires_mais_preserve_statut_source_creee`,
`mouvements_dedupliques_sur_boucleId_date_contenu`,
`aucun_journal_perdu_et_dedup`, `garder_existant_ne_touche_pas_la_boucle`
(canal import) ; `FusionSyncTest` (19 tests) pour l'adoption du statut distant
et la préservation de `id`/`creee`/`source` en réplication.

**Ce qui le casserait.** Ajouter `statut` ou `source` aux champs copiés par
`calculerFusion` (canal import) ; retirer `statut` de `adopter()`, ou y ajouter
`id`/`creee`/`source` (canal réplication) ; élargir la clé de déduplication (par
exemple à `(boucleId, date)` seul), sur l'un ou l'autre canal, écraserait des
mouvements de même date au contenu différent.

---

## I8 — Aucune donnée masquée silencieusement

**Énoncé.** Une boucle dont le statut n'est pas reconnu reste **visible** dans
« Toutes », signalée par un marqueur « statut inconnu ». Aucun filtre ne la fait
disparaître sans le dire.

**Pourquoi.** C'est une app-mémoire. Une donnée invisible est une donnée perdue :
si un statut legacy n'entre dans aucune catégorie, l'utilisateur doit le voir et
le corriger, pas le découvrir six mois plus tard.

**Où.** `Statut.depuis()` renvoie `null` pour une valeur inconnue (jamais de
retombée silencieuse sur `OUVERTE`). Une telle boucle n'est donc **ni** active
**ni** terminale : le filtre « Ouvertes » (`estActive()`) et le filtre
« Fermées » (`estTerminal()`, corrigé en AND-04) l'excluent tous les deux, mais
« Toutes » l'affiche, avec l'étiquette `EtiquetteInconnu` dans `ListeScreen`.
À l'import, un statut inconnu est en revanche **rejeté** avec un message
explicite (`JsonImporter`) : on n'accepte plus de nouvelles données invalides,
on tolère seulement l'historique déjà en base.

**Testé par.** `StatutTest.statut_inconnu_en_base_n_est_ni_actif_ni_terminal_mais_reste_visible`,
`StatutTest.depuis_null_vide_ou_inconnu_renvoie_null`,
`ImportInvalideTest.statut_inconnu_est_rejete_avec_message_lisible`.

**Trou de couverture assumé.** L'affichage effectif du marqueur n'est pas testé
(aucun test d'UI dans le projet) — vérification visuelle nécessaire.

**Ce qui le casserait.** Un filtre « Fermées » revenant à `!estActive()` (la
régression exacte corrigée en AND-04), ou un `?: Statut.OUVERTE` ajouté « pour
simplifier » dans `statutTypé()`.

---

## I9 — Tout identifiant de boucle porte le préfixe de l'appareil qui l'a créée

**Énoncé.** Un identifiant de **boucle** créé localement s'écrit `<CODE>-<numéro>`,
où `CODE` est le code de **cet** appareil (1 à 4 lettres). Le numéro suivant est
calculé sur les seuls identifiants portant ce préfixe. L'identité de l'appareil
n'est **jamais** sauvegardée ni restaurée par le système.

**Deux exceptions, délibérées.** L'énoncé ne vaut que pour les boucles :
- Les **captures** ne portent pas de préfixe d'appareil : leur id est
  `C-<aaaaMMjj-HHmmss>-<4 hex>` (`IdentifiantCapture.kt`). Une capture n'est
  jamais désignée à l'oral et un horodatage + tirage aléatoire évite toute
  collision **sans coordination** entre appareils — donc sans dépendre du socle
  multi-appareils. Choix documenté (I14, `genererIdCaptureUnique`).
- Les **ids historiques non conformes** sont **tolérés à l'import** (`idConforme`
  ne sert qu'à signaler, jamais à rejeter ni renuméroter, cf. I8) ; ils sont
  ignorés par le décompte de `genererProchainId`, qui ne compte que le préfixe
  local.

**Pourquoi.** Le registre est destiné à vivre sur plus d'un appareil. Avec la
numérotation globale d'avant (max + 1, tous préfixes confondus), deux appareils
hors ligne créant chacun une boucle produisaient inévitablement deux `B-042`
différents : à la première fusion, l'un écrasait l'autre — perte silencieuse
dans une app dont le rôle est de ne rien perdre. Le préfixe rend la collision
**impossible sans aucune coordination** : c'est la propriété que ni un serveur,
ni une convention d'usage n'auraient garantie aussi solidement.

**Où.**
- `CodeAppareil.kt` : `normaliserCodeAppareil()` n'accepte que `^[A-Z]{1,4}$`
  (casse et espaces normalisés, tout le reste refusé) ;
  `codeAppareilSuggere()` propose le préfixe dominant des ids déjà présents.
- `Identifiants.kt` : `genererProchainId(code, ids)` ne compte **que** les ids du
  préfixe `code` ; `idConforme()` sert à *signaler*, jamais à réécrire.
- `MainActivity` : `IdentiteAppareilScreen` est **bloquant** au premier
  lancement — l'app n'est pas utilisable sans identité (donc aucune boucle ne
  peut être créée sans préfixe). `BoucleViewModel.creer()` fait
  `repository.lireCodeAppareil() ?: return@launch` : pas de code, pas de création.
- Stockage : `SharedPreferences` **`registre-appareil.xml`**, un fichier dédié à
  cela seul.

**L'exclusion des sauvegardes, et pourquoi elle a sa propre ligne.** Si le code
appareil était restauré avec les données, restaurer une sauvegarde sur un second
téléphone en ferait un clone : deux appareils portant le code `B`, émettant les
mêmes identifiants — exactement la collision que I9 est censé rendre impossible.
Le mécanisme d'exclusion d'Android est **à granularité de fichier** (l'attribut
`path` désigne un fichier ou un dossier, jamais une clé) : c'est la raison pour
laquelle le code appareil vit dans son propre fichier de préférences, et non
avec les réglages. La règle, présente dans les deux fichiers :

```xml
<exclude domain="sharedpref" path="registre-appareil.xml" />
```

dans `backup_rules.xml` (Android ≤ 11) et dans `data_extraction_rules.xml`, sous
**`<cloud-backup>` et `<device-transfer>`** — les deux, sinon un transfert
d'appareil à appareil recopierait l'identité que la sauvegarde cloud exclut.
`<exclude>` est prioritaire sur `<include>`.

**Testé par.** `CodeAppareilTest` (4 tests : normalisation, refus de tout ce qui
n'est pas 1–4 lettres, suggestion du préfixe dominant, aucune suggestion sans
matière) et `GenererIdTest` (8 tests, dont la séquence par préfixe, l'ignorance
des préfixes étrangers et la tolérance des ids historiques).

**Ce que rien n'attrape.** Le comportement **réel** d'une restauration système
n'est pas vérifiable en CI : il exige deux appareils physiques (ou un
`bmgr backupnow` / `restore` manuel). La règle est conforme à la documentation
Android, sa mise en application est prise sur parole. C'est le point le plus
fragile du lot AND-07.

**Ce qui le casserait.** Déplacer le code appareil dans `registre-prefs.xml`
(il redeviendrait couvert par la sauvegarde), retirer l'`<exclude>` d'un seul
des deux fichiers, ou faire retomber `genererProchainId` sur un décompte tous
préfixes confondus « pour éviter les trous ».

---

## I10 — Toute suppression laisse une trace, jamais d'effacement muet

**Énoncé.** Supprimer une boucle écrit une ligne dans `suppressions`
(`boucleId`, date, appareil auteur) **dans la même transaction** que la
suppression. Il n'existe pas d'état où une boucle a disparu sans trace.

**Pourquoi.** Une absence ne dit pas pourquoi elle est là. Un appareil qui
importe l'export d'un autre ne peut pas distinguer « cette boucle a été
supprimée volontairement » de « cette boucle ne m'est jamais parvenue » — donc,
sans trace, la première réimportation la **ressuscite**. La trace fait de la
suppression une décision transmissible, au même titre qu'une clôture. C'est le
pendant de I1 : l'app ne perd pas l'information qu'on a voulu perdre une donnée.

**Où.** `Suppression.kt` → `traceSuppression()`, fonction **pure**.
`BoucleDao.supprimerAvecTrace(boucle, trace)` est annotée **`@Transaction`** et
insère la trace **avant** de supprimer la boucle : l'atomicité est la garantie,
pas l'ordre. `BoucleRepository.supprimer()` est le **seul** appelant, et le seul
chemin de suppression de l'app. Les traces sont exportées et importées (racine
`suppressions`, format v3) ; « Écraser » remplace la table entière, les deux
autres modes ajoutent (`REPLACE` sur `boucleId`).

**Testé par.** `HorodatageTest.la_trace_de_suppression_porte_l_appareil_et_la_date`
et `RoundTripV3Test.aller_retour_v3_complet_sans_perte` (les tombstones
survivent à l'aller-retour JSON).

**Portée exacte, pour ne pas se tromper.** AND-07 **enregistre** les traces ; il
ne les **exploite pas encore** à la fusion. Un fichier contenant à la fois
`B-013` et sa tombstone réinsère la boucle : l'arbitrage « la tombstone gagne »
relève d'un lot de synchronisation ultérieur. L'invariant garanti aujourd'hui est
la traçabilité, pas la non-résurrection.

**Trou de couverture assumé.** L'atomicité de `@Transaction` n'est pas testée
(elle exigerait une base Room instrumentée) : elle repose sur l'annotation Room
et la revue. De même, le fait que `supprimer()` soit l'unique chemin de
suppression est garanti par lecture du code.

**Ce qui le casserait.** Un `dao.supprimer(boucle)` appelé directement depuis un
écran, le widget ou un futur import, sans passer par `supprimerAvecTrace` — la
boucle disparaîtrait alors sans laisser de trace, et reviendrait au premier
import venu.

---

## I11 — Un appareil n'écrit que son propre fichier d'état

**Énoncé.** Dans le dossier partagé, cet appareil écrit **uniquement**
`etat-<SON CODE>.json`. Il lit les fichiers des autres, il ne les modifie jamais.

**Pourquoi.** C'est la règle qui supprime le problème le plus dur de la
synchronisation par fichier : l'écriture concurrente. Deux appareils qui
écriraient un fichier commun produiraient, selon le service de synchro, un
écrasement silencieux ou un fichier « de conflit » que personne ne relit. En
donnant à chacun son fichier, il n'y a **jamais** deux écrivains pour un même
octet — la fusion redevient un problème de lecture, qui se traite par du calcul
(cf. I13) et non par de la chance.

**Où.**
- `DossierSync.kt` : `nomFichierEtat(code)` construit le seul nom que l'on écrit,
  et `DossierSyncSaf.ecrireEtat()` ne prend que le code **local** en paramètre —
  la fonction est structurellement incapable d'écrire ailleurs.
- `DossierSyncSaf.listerEtats(dossier, codeLocal)` saute notre propre fichier
  (on ne se relit pas) et ignore tout nom non conforme.
- `codeDepuisNomFichier()` refuse les copies de conflit (`etat-PRO (1).json`,
  `etat-PRO.json.conflict`) : un fichier dupliqué est un fichier dont on ne sait
  pas s'il est à jour, donc on ne le fusionne pas.
- `BoucleRepository.synchroniser()` prend le code émetteur dans le **nom du
  fichier**, pas dans son contenu : un fichier ne peut pas se prétendre émis par
  un autre appareil que celui dont il porte le nom.

**Testé par.** `FichierEtatTest` (4 tests) : nom dérivé du code, code extrait
d'un nom conforme, refus des noms non conformes, refus des copies de conflit.

**Trou de couverture assumé.** Qu'un appareil n'écrive effectivement que son
fichier n'est pas vérifié par un test (SAF exige un appareil) : c'est garanti par
la signature de `ecrireEtat()` et la lecture du code.

**Ce qui le casserait.** Un « nettoyage » du dossier qui supprimerait ou
réécrirait le fichier d'un autre appareil, ou une évolution qui prendrait le code
émetteur dans le champ `codeAppareil` du contenu au lieu du nom de fichier.

---

## I12 — Toute fusion est précédée d'un backup strict

**Énoncé.** `synchroniser()` crée une sauvegarde complète **forcée** avant de lire
le premier fichier. Si elle échoue, aucune fusion n'a lieu.

**Pourquoi.** C'est I4 étendu au canal le plus risqué. Un import, l'utilisateur le
déclenche fichier en main ; une fusion, elle, applique des décisions calculées sur
des données venues d'ailleurs. Le seul recours crédible en cas de résultat non
voulu est un état d'avant, sur disque.

**Où.** `BoucleRepository.synchroniser()` : `creerBackupStrict(forcer = true)` en
**première ligne**, avant `listerEtats()`. `forcer = true` court-circuite
l'anti-rafale de 5 minutes : une fusion a toujours son propre point de retour, même
si une clôture vient d'en créer un. L'exception remonte au `ViewModel`
(`erreurSync`) et s'affiche : jamais d'échec muet.

**Et l'atomicité.** Le plan de fusion est appliqué par
`BoucleDao.appliquerPlanSync()`, annotée `@Transaction`, qui écrit les boucles, les
mouvements, les journaux, les tombstones **et** la ligne de journal de
synchronisation en une seule transaction. Il n'existe pas d'état où une fusion
s'est appliquée sans laisser sa trace, ni l'inverse.

**Testé par.** *Aucun test unitaire* — le `Repository` dépend de Room et d'un
`Context`. Garanti par lecture du code, comme I4.

**Ce qui le casserait.** Un chemin de fusion « rapide » qui sauterait le backup
« parce que le plan est petit », ou le passage de `forcer` à `false`.

---

## I13 — Aucun écrasement silencieux

**Énoncé.** Quand une fusion remplace la valeur d'un champ, elle écrit un
mouvement qui dit quel champ, quelle ancienne valeur, quelle nouvelle valeur et
depuis quel appareil. Et quand elle ne peut pas trancher, elle n'écrase **rien** :
elle demande un arbitrage.

**Pourquoi.** Un registre-mémoire peut accepter d'être écrasé — deux appareils
finissent par devoir se mettre d'accord — mais il ne peut pas accepter de perdre
l'information *qu'il l'a été*. Sans cette règle, une phrase modifiée sur l'autre
appareil disparaîtrait sans laisser de trace, et la seule façon de s'en apercevoir
serait de se souvenir de ce qu'on avait écrit.

**Où.** `FusionSync.kt` :
- `calculerFusionSync()` est **pure** : elle ne fait rien, elle rend un
  `PlanFusion`. Chaque champ écrasé produit un `Mouvement` de type `declaration`
  au libellé figé — `titre : "ancien" remplacé par "nouveau" (sync depuis PRO)` ;
- au-delà de la tolérance de **60 s**, le plus récent gagne (avec traçage) ; en
  deçà, avec des champs divergents, c'est un `ConflitSync`
  (`HORODATAGE_TROP_PROCHE`) et le plan n'écrit rien ;
- une tombstone entrante visant une boucle vivante ici est un
  `ConflitSync.SUPPRIMEE_A_DISTANCE` : rien n'est effacé (la CASCADE emporterait
  mouvements et journaux de clôture, ce que l'app s'interdit) ;
- un côté terminal l'emporte sur un côté actif, quelles que soient les dates —
  et cet écrasement est tracé comme les autres ;
- `id`, `creee` et `source` ne sont jamais écrasés ;
- le garde-fou d'horloge (`avanceHorloge`, marge de **10 minutes**) interrompt la
  fusion plutôt que d'arbitrer sur des dates invraisemblables.

Côté arbitrage manuel, `arbitrerPrendreDistant()` trace de la même façon, et
`arbitrerGarderLocal()` ne réécrit aucun champ : il réestampille la boucle, ce qui
fait converger l'autre appareil par la règle du plus récent.

**Testé par.** `FusionSyncTest` (19 tests) — dont le libellé exact des traces,
l'absence d'écriture en cas de conflit, la préservation d'`id`/`creee`/`source`,
les deux sens de la règle terminale, le garde-fou d'horloge, et surtout
**l'idempotence** (fusionner deux fois le même fichier ne change rien la seconde
fois, ni la troisième) et la **symétrie** (A fusionne B, B fusionne A, les deux
bases convergent et il ne reste rien à échanger). `JournalSyncTest` (4 tests)
vérifie la trace consignée au journal de synchronisation.

**Comment l'idempotence tient.** Une boucle adoptée conserve le `modifieeLe` /
`modifieePar` **de l'émetteur**, et non l'heure locale. Si le repository la
réestampillait, la fusion suivante verrait un écart de quelques secondes et
inventerait un conflit. C'est écrit dans le contrat de `PlanFusion` et vérifié par
le test d'idempotence — le piège le plus facile à retomber dedans.

**Portée exacte.** La coercition des propositions IA (I6) **ne s'applique pas** au
canal de synchronisation : un fichier d'état vient d'un appareil pair, dont la
supervision vaut décision (arbitrage du commanditaire, 2026-07-25). L'insertion
reste tracée par un mouvement « Boucle IA reçue de <CODE> avec le statut "…"
(appareil pair, supervision non rejouée) ». Conséquence à connaître : un fichier
nommé `etat-XX.json` déposé dans le dossier partagé par un tiers entrerait avec
son statut déclaré. Le dossier partagé est donc un canal de confiance, au même
titre que l'appareil lui-même.

**Ce qui le casserait.** Élargir la tolérance de 60 s « pour avoir moins de
conflits » (on trancherait alors des cas indécidables), estampiller localement les
boucles adoptées (idempotence perdue), ou appliquer un plan sans ses
`mouvementsAInserer`.

---

## I14 — Une capture n'est jamais supprimée, seulement marquée

**Énoncé.** Une note capturée depuis une autre application ne peut pas être
effacée. Elle passe en `IGNOREE` (réversible), en `EXPORTEE` (réversible) ou en
`TRAITEE`, et son `contenuBrut` n'est jamais réécrit.

**Pourquoi.** C'est le prolongement direct de I1 : on ne ferme pas une boucle sans
preuve, on n'efface pas une note parce qu'elle encombre. Une boîte de réception où
l'on peut supprimer redevient une liste de tâches — et la note supprimée est
précisément celle dont on se demandera, trois mois plus tard, ce qu'elle disait.
Ignorer est une décision consultable ; supprimer n'en laisse aucune trace.

**Où.**
- `BoucleDao` : **aucune** méthode de suppression de capture. Pas de `@Delete`, pas
  de `DELETE FROM captures`, pas même une purge par ancienneté. Seuls
  `insererCapture` et `mettreAJourCapture` écrivent.
- `BoucleRepository` : `ignorerCapture`, `reactiverCapture`,
  `lierCaptureABoucle` — aucune n'efface.
- `StatutCapture` : quatre états, tous de *traitement*. Aucun ne signifie
  « effacée ».
- `versBrute()` refuse le retour depuis `TRAITEE` (la boucle existe déjà) ;
  `versTraitee(boucleId)` **exige** l'identifiant produit, donc une capture traitée
  ne peut pas perdre le lien vers sa boucle.
- `ReceptionScreen` ne propose aucune suppression, quel que soit le statut.
- `Capture.boucleLiee` est une colonne simple, **sans clé étrangère** : une clé
  étrangère imposerait un comportement en cascade (la suppression d'une boucle
  emporterait la capture) ou bloquerait la suppression des boucles. Le lien reste
  donc informatif — si la boucle disparaît, la capture garde la trace de
  l'identifiant qu'elle a produit.

**Testé par.** `CaptureTest.aucune_methode_de_suppression_n_est_exposee_pour_les_captures`
inspecte le DAO **par réflexion** : ajouter un `@Delete` « juste pour nettoyer »
fait tomber ce test. Plus les transitions :
`brute_vers_exportee_puis_retour_en_brute`, `brute_vers_ignoree_puis_retour_en_brute`,
`brute_vers_traitee_exige_la_boucle_produite`,
`une_capture_traitee_ne_revient_pas_en_brute`.

**Ce qui le casserait.** Une requête `DELETE FROM captures` ajoutée pour un
« vidage de la boîte », une purge automatique des captures anciennes, ou un
`onConflict = REPLACE` sur `insererCapture` : une collision d'identifiant
écraserait alors une capture existante. C'est pour cela que l'insertion utilise
`@Insert` sans stratégie de remplacement et que `genererIdCaptureUnique` contrôle
l'unicité au lieu de faire confiance au tirage aléatoire.

---

## I15 — Aucune analyse de contenu dans l'application

**Énoncé.** L'application ne lit jamais le texte d'une capture pour en déduire quoi
que ce soit. Pas d'extraction de mots-clés, pas de détection d'échéance, pas de
catégorisation, aucune création automatique de boucle. L'analyse est faite **hors**
de l'application, et son résultat repasse par la supervision.

**Pourquoi.** C'est le cœur du produit, pas une limitation technique. Une app qui
devine transforme une note en engagement sans que personne ne l'ait décidé — et un
registre-mémoire dont le contenu s'auto-remplit n'est plus une mémoire, c'est un
flux. L'intelligence est autorisée, mais elle est dehors, et ce qu'elle propose
entre en `proposee` (I6). La séparation « capture / boucle » est la forme concrète
de cette règle : deux tables, deux temps, une décision humaine entre les deux.

**Où.**
- `CaptureActivity` lit `EXTRA_TEXT` / `EXTRA_PROCESS_TEXT` et les stocke. Aucune
  autre lecture du texte.
- `EmpreinteCapture.kt` **mesure** le texte (normalisation, hachage, troncature)
  sans l'interpréter. La normalisation ne sert qu'à la déduplication ; le contenu
  stocké reste intact.
- `Capture.titrePropose()` / `originePropose()` **pré-remplissent un formulaire**
  avec la première ligne et le nom de l'app source : aucune information n'est
  extraite du sens du texte, et l'utilisateur valide.
- Le seul chemin capture → registre est l'action « Créer une boucle », qui ouvre le
  formulaire existant. Le repository n'a aucune méthode créant une boucle depuis une
  capture sans passage par ce formulaire.
- `LotAnalyseExporter` fait **sortir** les notes ; il ne sait pas les relire — le
  format `lot-analyse` n'est pas importable.

**Testé par.** `CaptureTest.le_titre_propose_vient_du_sujet_ou_de_la_premiere_ligne`
et `l_origine_proposee_nomme_l_app_source_sans_rien_deduire` (le pré-remplissage ne
dépend que de la structure, jamais du sens),
`PreparationCaptureTest.le_contenu_est_stocke_tel_quel_sauf_troncature` et
`une_capture_creee_arrive_en_brute_sans_boucle_liee` (aucune boucle créée à la
capture), `LotAnalyseTest.chaque_capture_exporte_exactement_les_six_champs_prevus`
(le lot ne transporte ni statut, ni empreinte, ni boucle liée).

**Ce qui le casserait.** Une expression régulière cherchant des dates dans le
contenu « pour proposer une échéance », un classement automatique par mots-clés, un
`enregistrerCapture` qui créerait aussi une boucle, ou l'ajout d'un SDK d'analyse
embarqué (que la garde CI d'I5 n'attraperait pas, faute d'être un client réseau).
C'est une règle de revue de code, comme I1.

---

## I16 — Une proposition n'est pas une décision

**Énoncé.** Importer une proposition ne change le statut d'**aucune** capture.
Les captures d'origine ne bougent qu'au moment où l'utilisateur tranche :
`TRAITEE` à l'acceptation, retour à `BRUTE` au rejet. Et le lien capture → boucle,
qui existe en deux exemplaires (table de liaison et colonne `boucleLiee`), n'est
écrit qu'en un seul endroit.

**Pourquoi.** C'est I6 poussé jusqu'à sa conséquence sur la boîte de réception. Si
un import faisait passer les captures en `TRAITEE`, il suffirait de déposer un
fichier pour vider la boîte : la supervision deviendrait contournable par le bas,
non plus sur le registre (I6 s'en charge) mais sur la matière première. Et au
rejet, remettre la note en `BRUTE` plutôt que de la laisser `EXPORTEE` traduit une
règle simple : **une analyse ratée ne consomme pas le matériau.**

**Où.**
- `BoucleRepository.importerAjouter` / `importerEcraser` / `importerFusionner`
  appellent `enregistrerLiensDeclares()`, qui écrit **uniquement** la table de
  liaison. Aucun de ces chemins ne touche à `captures.statut`.
- `SupervisionCaptures.kt` → `transitionsCapturesApresSupervision()`, fonction
  **pure**, décide des transitions ; `accepter()` et `rejeter()` l'appliquent.
- `TRAITEE` est **absorbant** : un rejet ne défait pas un aboutissement obtenu
  ailleurs. Ce n'est pas un arbitrage inventé — `Capture.versBrute()` (I14) refuse
  déjà le retour depuis `TRAITEE`. Propriété obtenue : le statut final ne dépend
  pas de l'ordre des décisions.
- `BoucleDao.lierCaptureEtBoucle()` est annotée `@Transaction` et écrit la capture
  **et** le lien ensemble. Le commanditaire a choisi de conserver `boucleLiee` en
  parallèle de la table (AND-09) : les deux représentations du même fait ne peuvent
  donc diverger que si quelqu'un contourne cette méthode.
- La trace d'acceptation d'AND-04 est **complétée**, pas remplacée : le libellé
  reste identique mot pour mot quand aucune origine n'est déclarée, et devient
  « Proposition IA acceptée (origine : C-…, C-…) » sinon.

**Testé par.** `SupervisionCapturesTest` (9 tests) : acceptation faisant aboutir,
rejet rendant la matière disponible, absence d'écriture inutile, **absorption de
`TRAITEE` dans les deux ordres de décision**, libellé de trace inchangé sans
origine et enrichi avec. `OriginesImportTest` (5 tests) : fichier sans `origines`
valide (non-régression), identifiant inconnu non bloquant, aller-retour
export/import préservant les liens.

**Trou de couverture assumé.** Que les trois imports n'écrivent effectivement
aucun statut de capture n'est pas vérifié par un test (le `Repository` dépend de
Room) : c'est garanti par lecture du code, comme I4 et I12.

**Ce qui le casserait.** Un import qui marquerait les captures « pour faire le
ménage dans la boîte », un rejet qui laisserait les captures en `EXPORTEE` (la
matière deviendrait invisible aux prochains lots), ou une écriture de `boucleLiee`
ailleurs que dans `lierCaptureEtBoucle` — les deux représentations du lien
divergeraient alors en silence.

---

## Comment savoir si un invariant est cassé

**1. Lancer les tests.** `./gradlew test` — 141 tests. Correspondance
invariant → tests qui tomberaient :

| Invariant | Tests qui tomberaient |
|---|---|
| I1 | `ClotureTest` (3), `TransitionStatutTest` (rejet sans motif / avec motif) |
| I2 | `TransitionStatutTest` (proposée non clôturable, rejet hors proposition, acceptation hors proposition) |
| I3 | `StatutTest` (exclusivité, proposée ni/ni, réversibilité) |
| I4 | `BackupRafaleTest` (6) |
| I5 | *aucun test unitaire* → le step CI échoue |
| I6 | `CoercitionTest` (5) |
| I7 | `FusionTest` (7) |
| I8 | `StatutTest` (statut inconnu), `ImportInvalideTest` (rejet à l'import) |
| I9 | `CodeAppareilTest` (4), `GenererIdTest` (8) — mais **pas** l'exclusion des sauvegardes, non testable |
| I10 | `HorodatageTest` (trace), `RoundTripV3Test` (aller-retour des tombstones) |
| I11 | `FichierEtatTest` (4) — mais pas l'écriture SAF elle-même |
| I12 | *aucun test unitaire* → lecture de code (comme I4) |
| I13 | `FusionSyncTest` (19, dont idempotence et symétrie), `JournalSyncTest` (4) |
| I14 | `CaptureTest` (14, dont l'inspection du DAO par réflexion) |
| I15 | `CaptureTest` (pré-remplissage), `PreparationCaptureTest` (5), `LotAnalyseTest` (5) |
| I16 | `SupervisionCapturesTest` (9), `OriginesImportTest` (5) |

**2. Lire le résultat de la CI.** Le step « Vérifier l'absence de réseau
(manifest mergé) » échoue en cas de régression sur I5 ; il bloque la publication
de la Release.

**3. Contrôles manuels rapides.**

```bash
grep -c "android.permission.INTERNET" app/src/main/AndroidManifest.xml   # attendu : 0
grep -rn 'statut = "fermee"\|statut = "rejetee"' app/src/main/java/       # attendu : rien (I1)
grep -rniE "okhttp|retrofit|ktor" app/src/main/java/ app/build.gradle.kts # attendu : rien (I5)
grep -c "registre-appareil.xml" app/src/main/res/xml/backup_rules.xml \
        app/src/main/res/xml/data_extraction_rules.xml                    # attendu : 1 et 2 (I9)
grep -rn "dao.supprimer(" app/src/main/java/                              # attendu : rien hors supprimerAvecTrace (I10)
grep -rn "ecrireEtat(" app/src/main/java/                                 # attendu : le seul appel passe le code LOCAL (I11)
grep -n "creerBackupStrict(forcer = true)" \
     app/src/main/java/com/pontat/registreboucles/data/BoucleRepository.kt # attendu : 4 (3 imports + sync, I12)
grep -rniE "DELETE FROM captures|@Delete" \
     app/src/main/java/com/pontat/registreboucles/data/BoucleDao.kt        # attendu : le seul @Delete concerne les boucles (I14)
```

**4. Ce que rien n'attrape.** I1 contourné par une écriture directe de statut,
I4 contourné par un nouveau chemin d'écriture sans backup, la
désynchronisation SQL/`estActive()` de I3, une suppression directe contournant
I10, et — le seul qui ne soit pas même vérifiable localement — le comportement
réel d'une restauration système pour I9. Ceux-là dépendent de la revue de code
— d'où `AGENTS.md`.
