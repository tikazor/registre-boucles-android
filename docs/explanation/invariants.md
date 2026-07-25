---
title: Invariants structurels de l'application
type: explanation
status: current
updated: 2026-07-25
source: app/src/main/java/com/pontat/registreboucles/data/{Cloture,Statut,Fusion,Coercition,Backup,BoucleRepository,BoucleDao}.kt, app/src/main/AndroidManifest.xml, .github/workflows/build.yml, app/src/test/java/**
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

**Énoncé.** Une boucle ne peut pas atteindre un état terminal sans qu'une
entrée `Journal` (type + texte non vide) soit créée dans la même opération.

**Pourquoi.** C'est la doctrine de l'app : une boucle ne se ferme pas parce
qu'on s'en désintéresse, elle se ferme **contre une preuve**. Sans cette règle,
le registre deviendrait une liste de tâches ordinaire où l'on coche sans rendre
compte.

**Où.** `Cloture.kt` → `executerTransitionTerminale()` est le **chemin unique**
d'écriture d'un statut terminal :
`require(statutCible.estTerminal())`, `require(texte.isNotBlank())`, puis
`insererJournal()` **avant** `mettreAJourBoucle()`. `executerCloture()` n'est
qu'un alias vers cette fonction (`statutCible = FERMEE`) — aucune logique
dupliquée. `rejeter()` dans `BoucleRepository` passe par la même fonction.

**Testé par.** `ClotureTest` (3 tests) :
`cloture_cree_toujours_une_entree_journal_et_ferme`,
`fermeture_sans_texte_de_journal_est_impossible`,
`aucune_fermeture_ne_laisse_la_boucle_sans_journal`.
`TransitionStatutTest` : `rejeter_sans_motif_est_impossible`,
`rejeter_une_proposee_avec_motif_ecrit_journal_et_passe_a_rejetee`.

**Ce qui le casserait.** Tout `copy(statut = …)` vers un statut terminal écrit
ailleurs que dans `executerTransitionTerminale` — typiquement un
`dao.mettreAJour(boucle.copy(statut = "fermee"))` ajouté dans un écran ou le
widget. Aucun test ne détecterait ce contournement : c'est une règle à tenir en
revue de code (cf. `AGENTS.md`).

---

## I2 — Garde-fous de transition

**Énoncé.** `FERMEE` n'est atteignable que depuis une boucle **active**.
`REJETEE` n'est atteignable que depuis une boucle **`PROPOSEE`**. Une
proposition ne peut donc pas être clôturée : il faut d'abord l'accepter.

**Pourquoi.** Sans ces gardes, une proposition d'IA jamais examinée pourrait
être « clôturée » directement et rejoindre l'histoire du registre comme un
engagement réellement tenu. La supervision serait contournable par le bas.

**Où.** `Cloture.kt`, dans `executerTransitionTerminale()` : le `when` sur
`statutCible` vérifie l'état **courant** (`Statut.depuis(boucle.statut)`) avant
d'écrire. `accepterProposition()` exige symétriquement que l'état courant soit
`PROPOSEE`.

**Testé par.** `TransitionStatutTest` : `une_proposee_ne_peut_pas_etre_cloturee`,
`on_ne_peut_pas_rejeter_une_boucle_non_proposee`,
`accepter_une_boucle_non_proposee_est_refuse`.

**Ce qui le casserait.** Assouplir le `when` (par exemple accepter `REJETEE`
depuis n'importe quel statut « pour pouvoir corriger une erreur »). Si ce besoin
apparaît, il justifie un lot dédié, pas un `require` retiré.

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

## I7 — Fusion additive : on n'efface jamais

**Énoncé.** Le mode « Fusionner » n'efface aucun mouvement ni journal. Pour une
boucle existante, `id`, `creee`, `statut` et `source` sont **toujours**
préservés, même quand l'utilisateur choisit « prendre l'entrant ».

**Pourquoi.** Une IA qui enrichit une boucle existante doit pouvoir compléter la
description, jamais réécrire le cycle de vie ni s'attribuer la provenance. Et
un mouvement/journal déjà écrit est une trace historique : rien ne justifie
qu'un fichier importé la supprime.

**Où.** `Fusion.kt` → `calculerFusion()`, fonction **pure** :
- mouvements/journaux entrants ajoutés, dédupliqués sur
  `(boucleId, date, contenu)` et `(boucleId, date, texte)` ; aucune suppression ;
- id absent ⇒ boucle créée telle quelle (avec son statut d'origine) ;
- id présent **et** choisi dans `prendreEntrant` ⇒ `existante.copy(...)` sur les
  seuls champs descriptifs (`type, titre, origine, echeance, tiers,
  preuveAttendue, blocage, impact, defaut, milieu`) ;
- `calculerConflits()` produit le diff champ par champ présenté à l'arbitrage.

**Testé par.** `FusionTest` (7 tests), dont
`prendre_entrant_remplace_les_scalaires_mais_preserve_statut_source_creee`,
`mouvements_dedupliques_sur_boucleId_date_contenu`,
`aucun_journal_perdu_et_dedup`, `garder_existant_ne_touche_pas_la_boucle`.

**Ce qui le casserait.** Ajouter `statut` ou `source` à la liste des champs
copiés dans `misesAJour`. Élargir la clé de déduplication (par exemple dédupliquer
sur `(boucleId, date)` seul) écraserait des mouvements de même date au contenu
différent.

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

## Comment savoir si un invariant est cassé

**1. Lancer les tests.** `./gradlew test` — 50 tests. Correspondance
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

**2. Lire le résultat de la CI.** Le step « Vérifier l'absence de réseau
(manifest mergé) » échoue en cas de régression sur I5 ; il bloque la publication
de la Release.

**3. Contrôles manuels rapides.**

```bash
grep -c "android.permission.INTERNET" app/src/main/AndroidManifest.xml   # attendu : 0
grep -rn 'statut = "fermee"\|statut = "rejetee"' app/src/main/java/       # attendu : rien (I1)
grep -rniE "okhttp|retrofit|ktor" app/src/main/java/ app/build.gradle.kts # attendu : rien (I5)
```

**4. Ce que rien n'attrape.** I1 contourné par une écriture directe de statut,
I4 contourné par un nouveau chemin d'écriture sans backup, et la
désynchronisation SQL/`estActive()` de I3. Ces trois-là dépendent de la revue
de code — d'où `AGENTS.md`.
