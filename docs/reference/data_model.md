---
title: Modèle de données interne (Room v7)
type: reference
status: current
updated: 2026-07-25
source: app/src/main/java/com/pontat/registreboucles/data/{Boucle,Mouvement,Journal,Suppression,EvenementSync,Capture,Statut,StatutCapture,Milieu,SourceBoucle,JournalType,CodeAppareil,Identifiants,IdentifiantCapture,AppDatabase,BoucleDao}.kt, app/schemas/com.pontat.registreboucles.data.AppDatabase/7.json
---

# Modèle de données interne (Room v7)

Ce document décrit le modèle **interne** (tables SQLite / entités Room).
Pour la représentation **JSON** échangée à l'import/export, voir
[`../schema.md`](../schema.md) — les deux ne sont pas identiques (voir
« Écarts connus » en fin de document) et ne doivent pas être confondus.

Base : `registre-boucles.db`, version de schéma **7**, 6 tables.

---

## 1. Table `boucles` — entité `Boucle`

Une « boucle » = un engagement ouvert qui doit être clôturé avec une preuve.

| Colonne | Type SQL | Nullable | Signification métier |
|---|---|---|---|
| `id` | TEXT | non (**PK**) | Identifiant lisible, préfixé par le **code de l'appareil** qui l'a créé (`B-041`, `PRO-007`). Non auto-généré : fourni par l'appelant. Cf. §1.1 et [`../schema.md`](../schema.md) §6. |
| `type` | TEXT | non | Nature de la boucle, **texte libre** configurable dans Réglages (ex. `ACTION`, `DECISION`). Aucun enum. |
| `titre` | TEXT | non | Intitulé court affiché en tête de carte. |
| `origine` | TEXT | non | D'où vient l'engagement (conversation, réunion, analyse…). Sert à retrouver le contexte. |
| `creee` | INTEGER | non | Date de création, **epoch millis**. Jamais modifiée après création (préservée par la fusion). |
| `echeance` | INTEGER | oui | Date d'échéance, epoch millis. `null` = pas d'échéance ⇒ la boucle n'est jamais comptée « en retard » ni « ≤ 7 jours », et n'apparaît pas dans le widget (cf. §5). |
| `tiers` | TEXT | oui | Personne/entité dont dépend la boucle. **Chaîne libre** (`null` = personne). |
| `preuveAttendue` | TEXT | non | Ce qui prouvera que la boucle peut être fermée. Cœur de la doctrine : on ne ferme pas sans preuve. |
| `blocage` | TEXT | oui | Frein courant identifié. Purement descriptif : **aucun flux applicatif ne l'exploite** (cf. ADR-04). |
| `impact` | TEXT | non | Conséquence si la boucle n'est pas traitée. |
| `defaut` | TEXT | oui | Action à appliquer par défaut si rien n'est tranché. Descriptif : **aucun flux ne l'applique** (cf. ADR-04). |
| `statut` | TEXT | non | Cycle de vie. Valeurs et sémantique : §4.1. Stocké en minuscules. |
| `milieu` | TEXT | oui | Domaine de vie. `null` = non renseigné. §4.2. |
| `source` | TEXT | oui | Provenance. `null` = `user` (données antérieures à la v4). §4.3. |
| `modifieeLe` | INTEGER | oui | Date de dernière modification, epoch millis. `null` = **jamais modifiée depuis sa création** (cas des données antérieures à la v5). Lecture via `derniereModification()`, qui retombe alors sur `creee`. |
| `modifieePar` | TEXT | oui | Code de l'appareil auteur de cette dernière modification. `null` = inconnu ; `"?"` si l'écriture a eu lieu sans identité enregistrée. |

Pas d'index secondaire ni de clé étrangère sur cette table.

### 1.1 Le code appareil et le préfixe des identifiants

Chaque installation porte un **code appareil** : 1 à 4 lettres majuscules
(`^[A-Z]{1,4}$`, `normaliserCodeAppareil()`), saisi une fois au premier
lancement et stocké **hors de la base**, dans un fichier de préférences dédié
`registre-appareil.xml` (`SharedPreferences`, clé `code_appareil`).

Ce code sert deux rôles :

1. **Préfixer les identifiants créés localement.** `genererProchainId(code, ids)`
   ne compte que les identifiants portant *ce* préfixe : un appareil `B` qui voit
   des `PRO-###` importés continue sa propre séquence en `B-041`. Deux appareils
   ne peuvent donc pas émettre le même identifiant, sans aucune coordination
   entre eux (cf. ADR-02, tranché option B).
2. **Estampiller les écritures** (`modifieePar`) et les suppressions
   (`suppressions.supprimeePar`).

Au premier lancement d'une base déjà peuplée (restauration, import),
`codeAppareilSuggere(ids)` propose le préfixe **dominant** des identifiants
présents, afin que la séquence existante continue sans rupture. Les
identifiants historiques ne sont **jamais** réécrits : `idConforme()`
(`^[A-Z]{1,4}-\d{3,}$`) sert uniquement à *signaler* les non-conformes dans le
rapport d'import.

> **Ce fichier de préférences est exclu des sauvegardes système** — c'est
> délibéré : restaurer une sauvegarde sur un second appareil ne doit pas cloner
> son identité, sinon les deux émettraient les mêmes identifiants. Voir
> [`../explanation/invariants.md`](../explanation/invariants.md) I9 et
> `res/xml/{backup_rules,data_extraction_rules}.xml`.

## 2. Table `mouvements` — entité `Mouvement`

| Colonne | Type SQL | Nullable | Signification |
|---|---|---|---|
| `mouvementId` | INTEGER | non (**PK**, auto-généré) | Identité technique. |
| `boucleId` | TEXT | non | **FK** → `boucles.id`, `ON DELETE CASCADE`, `ON UPDATE NO ACTION`. Indexé (`index_mouvements_boucleId`). |
| `date` | INTEGER | non | Epoch millis. Le tri d'affichage est `date DESC`. |
| `type` | TEXT | non | `preuve` / `declaration` / `defaut` (**chaîne libre**, pas d'enum côté mouvement). |
| `contenu` | TEXT | non | Texte du mouvement. |

## 3. Table `journaux` — entité `Journal`

| Colonne | Type SQL | Nullable | Signification |
|---|---|---|---|
| `journalId` | INTEGER | non (**PK**, auto-généré) | Identité technique. |
| `boucleId` | TEXT | non | **FK** → `boucles.id`, `ON DELETE CASCADE`, `ON UPDATE NO ACTION`. Indexé (`index_journaux_boucleId`). |
| `date` | INTEGER | non | Epoch millis. |
| `type` | TEXT | non | Attendu : `PREUVE` / `DECLARATION` / `DEFAUT` (`JournalType.name`, **majuscules**). |
| `texte` | TEXT | non | La preuve, la déclaration, ou le motif de rejet. |

### `ON DELETE CASCADE` — ce que ça implique

Supprimer une boucle supprime **automatiquement** ses mouvements et ses
journaux. C'est pour cela que l'import « Écraser » vide explicitement les trois
tables et qu'un backup strict le précède (cf.
[`../explanation/invariants.md`](../explanation/invariants.md) I4).

## 3 bis. Table `suppressions` — entité `Suppression`

Une « pierre tombale » (*tombstone*) : la trace qu'une boucle a existé et a été
supprimée délibérément.

| Colonne | Type SQL | Nullable | Signification |
|---|---|---|---|
| `boucleId` | TEXT | non (**PK**) | Identifiant de la boucle supprimée. **Pas de clé étrangère** : la ligne référencée n'existe plus, par définition. |
| `supprimeeLe` | INTEGER | non | Date de la suppression, epoch millis. |
| `supprimeePar` | TEXT | non | Code de l'appareil qui a supprimé (`"?"` si aucune identité enregistrée). |

**Pourquoi.** Sans cette table, une suppression est indistinguable d'une
absence. Un appareil qui recevrait un export d'un autre ne pourrait pas savoir
si `B-013` a été supprimée là-bas ou n'y est jamais arrivée : la réimporter
la ferait « ressusciter ». La trace rend la suppression transmissible.

**Où.** `Suppression.kt` (`traceSuppression(boucleId, code, date)`, fonction
pure) et `BoucleDao.supprimerAvecTrace(boucle, trace)`, annotée `@Transaction` :
la trace et la suppression sont écrites **atomiquement** — il n'existe pas
d'état où la boucle a disparu sans laisser de trace. `BoucleRepository.supprimer()`
est le seul appelant.

**Cycle de vie des traces.** Elles sont exportées (racine `suppressions` du
JSON, format v3) et importées par les trois modes. « Écraser » vide d'abord la
table (`supprimerToutesSuppressions()`) puis insère celles du fichier ;
« Ajouter » et « Fusionner » les ajoutent (`REPLACE` sur `boucleId`). Aucune
purge automatique n'est implémentée : une trace reste indéfiniment.
→ *à confirmer* : faut-il purger les traces au-delà d'une certaine ancienneté ?
Non tranché.

**Ce que cette table ne fait pas (encore).** L'import ne s'en sert pas pour
*empêcher* la réapparition d'une boucle supprimée : si un fichier contient à la
fois `B-013` et sa tombstone, la boucle est réinsérée. La table constitue le
socle de données ; l'arbitrage « la tombstone gagne-t-elle sur la boucle ? »
relève d'un lot de synchronisation ultérieur (ADR-03 et suivants).

## 3 ter. Table `evenements_sync` — entité `EvenementSync`

Journal de synchronisation : une ligne par fichier d'état distant lu, échecs
compris. Écrite une fois, **jamais modifiée ni purgée**.

| Colonne | Type SQL | Nullable | Signification |
|---|---|---|---|
| `evenementId` | INTEGER | non (**PK**, auto-généré) | Identité technique. |
| `horodatage` | INTEGER | non | Heure **locale** de la fusion, epoch millis. |
| `appareilDistant` | TEXT | non | Code appareil émetteur, tel que déduit du **nom du fichier** (I11). |
| `fichierLu` | TEXT | non | `etat-<CODE>.json`. |
| `exporteLeDistant` | INTEGER | oui | `exporteLe` déclaré par l'émetteur. Comparé à `horodatage`, il révèle après coup une horloge décalée. |
| `bouclesAjoutees` | INTEGER | non | Boucles insérées. |
| `bouclesFusionnees` | INTEGER | non | Boucles existantes mises à jour. |
| `bouclesIgnorees` | INTEGER | non | Boucles entrantes écartées par une décision (version locale plus récente, tombstone gagnante). Les boucles **identiques** ne comptent nulle part : il ne s'est rien passé. |
| `mouvementsAjoutes` / `journauxAjoutes` | INTEGER | non | Union appliquée, traces de fusion incluses. |
| `conflits` | INTEGER | non | Boucles laissées à l'arbitrage manuel. |
| `resultat` | TEXT | non | `succes` / `conflits` / `echec` (`ResultatSync.valeurStockee()`). |
| `detail` | TEXT | non | Phrase courte, lisible telle quelle (« 3 ajoutée(s), 1 à arbitrer »). |

Pas de clé étrangère : l'événement survit à toute donnée qu'il décrit.

**Les conflits ne sont PAS persistés.** Ils sont recalculés à chaque
synchronisation par la fonction pure — mêmes données, mêmes conflits. Fermer
l'application au milieu d'un arbitrage ne perd donc rien.

## 3 quater. Table `captures` — entité `Capture`

Note brute venue d'une autre application (partage ou sélection de texte).
**Une capture n'est pas une boucle** : elle vit en amont du registre, et rien n'en
sort vers `boucles` sans une action explicite de l'utilisateur.

| Colonne | Type SQL | Nullable | Signification |
|---|---|---|---|
| `id` | TEXT | non (**PK**) | `C-<aaaaMMjj-HHmmss>-<4 hex>`. Opaque et sans coordination entre appareils : une capture n'est jamais désignée à l'oral. |
| `contenuBrut` | TEXT | non | Le texte **tel quel**. Jamais réécrit, jamais analysé (invariant I15). Tronqué au-delà de 100 000 caractères, en le signalant dans le contenu. |
| `titre` | TEXT | oui | `EXTRA_SUBJECT` quand l'app source le fournit. |
| `appareil` | TEXT | non | Code appareil (AND-07), sinon nom libre saisi en Réglages, sinon `LOCAL`. |
| `appSource` | TEXT | oui | Paquet de l'app émettrice (`referrer?.host`), ex. `com.miui.notes`. `null` = non communiqué. |
| `capturee` | INTEGER | non | Epoch millis. Tri d'affichage : `capturee DESC`. |
| `empreinte` | TEXT | non | SHA-256 du contenu normalisé (**indexé**). Base de la déduplication. |
| `statut` | TEXT | non | `StatutCapture.valeurStockee()` (**indexé**). §5.5. |
| `boucleLiee` | TEXT | oui | Identifiant de la boucle produite. **Pas de clé étrangère** — voir ci-dessous. |

Index : `index_captures_empreinte`, `index_captures_statut`. Aucune contrainte
`UNIQUE` sur `empreinte` : le contrôle de doublon se fait **en amont**
(`preparerCapture`), pour pouvoir informer (« déjà capturé le … ») au lieu de
planter sur une contrainte violée.

### Pourquoi `boucleLiee` n'est pas une clé étrangère

Une clé étrangère imposerait un comportement en cascade : soit `ON DELETE CASCADE`,
et supprimer une boucle effacerait la capture qui l'a produite — impossible, une
capture ne disparaît jamais (I14) ; soit `RESTRICT`, et une boucle issue d'une
capture deviendrait indéboulonnable. Le lien est donc **informatif** : si la boucle
est supprimée, la capture conserve la trace de l'identifiant qu'elle a produit.

### Ce que la table ne permet pas

Aucune suppression. Le DAO n'expose ni `@Delete` ni `DELETE FROM captures`, et le
repository ne propose que `ignorerCapture` / `reactiverCapture` /
`lierCaptureABoucle`. Une capture s'écarte, elle ne s'efface pas — c'est vérifié
par réflexion dans `CaptureTest`.

---

## 4. Mouvement vs Journal — la distinction centrale

Elle n'est évidente pour personne et c'est le cœur du modèle :

- **`Mouvement`** = le *suivi courant*. Tout ce qui se passe sur une boucle
  encore vivante (relance, note, information). Librement ajoutable, sans
  conséquence sur le cycle de vie.
- **`Journal`** = la *preuve de fin*. Une entrée de journal accompagne
  obligatoirement le passage à un état **terminal** (`fermee`, `rejetee`) :
  c'est la trace qui justifie la fermeture.
- Conséquence pratique : on peut avoir 10 mouvements et 0 journal (boucle
  active), mais **jamais** un état terminal écrit par l'app sans journal
  (invariant I1). L'acceptation d'une proposition, elle, n'est pas terminale :
  elle laisse un **mouvement**, pas un journal.

---

## 5. Les 5 enums

### 5.1 `Statut` — cycle de vie (source de vérité unique)

| Valeur enum | Valeur stockée | Catégorie | Sémantique |
|---|---|---|---|
| `OUVERTE` | `ouverte` | **active** | À traiter. |
| `EN_COURS` | `en_cours` | **active** | À traiter (nuance de suivi). |
| `FERMEE` | `fermee` | **terminale** | Clôturée avec preuve. |
| `DEFAUT_APPLIQUE` | `defaut_applique` | **terminale** | Action par défaut appliquée. Atteignable **uniquement par import** : aucun flux applicatif ne l'écrit (ADR-04). |
| `PROPOSEE` | `proposee` | **ni l'un ni l'autre** | Proposition en attente de supervision. Exclue de la liste et du widget. |
| `REJETEE` | `rejetee` | **terminale** | Proposition refusée, avec motif au journal. |

Prédicats (`Statut.kt`) — seule définition autorisée dans l'app :
`estActive()` = `OUVERTE ∪ EN_COURS` · `estTerminal()` = `FERMEE ∪ DEFAUT_APPLIQUE ∪ REJETEE` ·
`estProposition()` = `PROPOSEE`. `PROPOSEE` est donc le seul statut ni actif ni terminal.

Extensions sur `Boucle` : `statutTypé()`, `estActive()`, `estTerminal()`,
`estProposition()`. Une valeur non reconnue donne `statutTypé() == null` : la
boucle n'est **ni** active **ni** terminale, reste visible dans « Toutes » et
porte un marqueur « statut inconnu » (invariant I8).

### 5.2 `Milieu`

| Valeur enum | Libellé affiché |
|---|---|
| `PRO` | Professionnel |
| `GOUVERNANCE` | Gouvernance |
| `PROJET` | Projet |
| `PERSO` | Personnel |

Enum figé à 4 valeurs — **non tranché** : cf. ADR-01 (enum figé vs liste
configurable). `Milieu.depuis()` accepte le nom d'enum **ou** le libellé,
insensible à la casse ; sinon `null`.

### 5.3 `SourceBoucle` — provenance

| Valeur enum | Valeur stockée | Sens |
|---|---|---|
| `USER` | `user` | Saisie dans l'app. |
| `IA` | `ia` | Proposée par une IA ⇒ supervision obligatoire (invariant I6). |
| `IMPORT` | `import` | Importée sans provenance déclarée. |

`sourceTypee()` retourne `USER` si la colonne est `null` **ou** contient une
valeur inconnue. `estIA()` déclenche le marqueur « IA » sur les cartes.

### 5.4 `JournalType`

| Valeur enum | Libellé affiché |
|---|---|
| `PREUVE` | Preuve |
| `DECLARATION` | Déclaration |
| `DEFAUT` | Défaut |

Deux particularités **vérifiées dans le code**, différentes des 3 autres enums :

1. `JournalType.depuis(nom)` est **sensible à la casse** (`it.name == nom`) et
   retombe sur `DECLARATION` au lieu de `null`.
2. Cette fonction n'est **appelée nulle part** dans le code applicatif ni les
   tests : c'est actuellement du code mort. L'import écrit le type de journal
   **brut** (`type = j.type`), sans validation ni normalisation ; l'affichage
   retombe sur la chaîne telle quelle si elle n'est pas reconnue.
   → *à confirmer* : faut-il valider/rejeter un type de journal inconnu à
   l'import, comme on le fait pour `statut` ? Non tranché à ce jour.

### 5.5 `StatutCapture` — cycle de vie d'une capture

| Valeur enum | Valeur stockée | Sens | Réversible |
|---|---|---|---|
| `BRUTE` | `brute` | Capturée, pas encore exploitée. État d'arrivée. | — |
| `EXPORTEE` | `exportee` | Partie dans un lot d'analyse. | oui → `BRUTE` |
| `TRAITEE` | `traitee` | A produit une boucle ; `boucleLiee` est renseigné. | **non** |
| `IGNOREE` | `ignoree` | Écartée volontairement. | oui → `BRUTE` |

Aucun état ne signifie « supprimée » (I14). `versTraitee(boucleId)` **exige**
l'identifiant produit, et `versBrute()` refuse le retour depuis `TRAITEE` : défaire
une capture traitée serait une décision sur le registre, pas sur la boîte de
réception. Un statut non reconnu donne `statutTypé() == null` : la capture n'est pas
considérée comme brute, elle reste visible dans « Toutes ».

### Tolérance `depuis()` — récapitulatif

| Enum | Accepte | Insensible à la casse | Valeur inconnue |
|---|---|---|---|
| `Statut` | nom d'enum | oui | `null` |
| `Milieu` | nom d'enum **ou** libellé | oui | `null` |
| `SourceBoucle` | nom d'enum | oui | `null` (→ `USER` via `sourceTypee()`) |
| `JournalType` | nom d'enum exact | **non** | `DECLARATION` |
| `StatutCapture` | nom d'enum | oui | `null` |

---

## 6. Migrations

Aucune migration destructive ; `fallbackToDestructiveMigration` n'est **pas**
activé (une migration défaillante fait échouer l'ouverture de la base, elle
n'efface jamais les données).

| Migration | Contenu SQL | Lot |
|---|---|---|
| 1 → 2 | `ALTER TABLE boucles ADD COLUMN milieu TEXT` | AND-01 |
| 2 → 3 | `CREATE TABLE journaux (…)` + `CREATE INDEX index_journaux_boucleId` | AND-01 |
| 3 → 4 | `ALTER TABLE boucles ADD COLUMN source TEXT` | AND-03 |
| 4 → 5 | `ALTER TABLE boucles ADD COLUMN modifieeLe INTEGER` + `ALTER TABLE boucles ADD COLUMN modifieePar TEXT` + `CREATE TABLE IF NOT EXISTS suppressions (…)` | AND-07 |
| 5 → 6 | `CREATE TABLE IF NOT EXISTS evenements_sync (…)` — aucune table existante touchée | AND-08 |
| 6 → 7 | `CREATE TABLE IF NOT EXISTS captures (…)` + `index_captures_empreinte` + `index_captures_statut` | AND-06 |

Le schéma est exporté et versionné dans `app/schemas/` (v3 à v7 présents)
depuis AND-02 (`exportSchema = true`, `room.schemaLocation`). Il n'existe
**aucun test de migration** : cela exigerait un `androidTest` sur émulateur —
dette assumée, cf. [`../explanation/architecture.md`](../explanation/architecture.md).

---

## 7. Requêtes DAO structurantes

| Requête | Rôle |
|---|---|
| `observerToutes()` | Liste complète, triée `echeance IS NULL, echeance ASC` (les sans-échéance en dernier). |
| `observerParStatut(statut)` | Utilisée pour la file de supervision (`proposee`). |
| `compterActives()` | `WHERE statut IN ('ouverte','en_cours')` — **miroir SQL de `Statut.estActive()`** (invariant I3). |
| `prochainesEcheances(limite)` | `WHERE statut IN ('ouverte','en_cours') AND echeance IS NOT NULL ORDER BY echeance ASC` — source de données du widget. Une boucle **sans échéance n'apparaît pas** dans le widget. |
| `observerDernieresModifs()` | `MAX(date) GROUP BY boucleId` sur `mouvements` → étiquette « Modifié le ». Distinct de la colonne `modifieeLe`, qui date la dernière écriture *de la boucle elle-même*, mouvements exclus. |
| `supprimerAvecTrace(boucle, trace)` | `@Transaction` : insère la tombstone **puis** supprime la boucle. Chemin unique de suppression (invariant I10). |
| `toutesLesSuppressions()` / `supprimerToutesSuppressions()` | Lecture des traces pour l'export, purge totale pour l'import « Écraser ». |
| `retirerSuppression(boucleId)` | Retrait d'UNE tombstone, uniquement lors d'une résurrection décidée par le moteur de fusion. Aucune purge par ancienneté n'existe. |
| `appliquerPlanSync(…)` | `@Transaction` : applique un `PlanFusion` **et** consigne son `EvenementSync`. Aucune décision n'y est prise (invariant I12). |
| `observerEvenementsSync()` / `dernierEvenementSync()` | Historique de synchronisation, en lecture seule. |
| `insererCapture` / `mettreAJourCapture` | Les **seules** écritures sur `captures`. Aucune méthode de suppression n'existe (invariant I14). |
| `captureParEmpreinte(empreinte)` | Contrôle de doublon avant insertion (index sur `empreinte`). |
| `observerNombreCapturesBrutes()` | `WHERE statut = 'brute'` — compteur du badge de la boîte de réception. |

---

## 8. Écarts connus entre modèle interne et contrat JSON

| Sujet | Interne (ici) | JSON ([`../schema.md`](../schema.md)) |
|---|---|---|
| `tiers` | `TEXT?` | chaîne, **ou** booléen hérité (`true`→`"Oui"`, `false`→`null`) |
| Mouvement | `type` + `contenu` | `{date, note}` — le JSON ne porte pas de type ; l'import fixe `type = "declaration"` |
| Dates | epoch millis (INTEGER) | chaînes ISO-8601 |
| `source` absente | `null` (⇒ `USER`) | à l'import, devient `import` |
| Code appareil | **hors base** : `SharedPreferences registre-appareil.xml` | racine `codeAppareil` de l'export (provenance du fichier), lue en information sans écraser l'identité locale |
| Tombstones | table `suppressions` | racine `suppressions`, dates en ISO-8601 (v3+ ; absente ⇒ liste vide) |

Ces écarts sont **voulus** : le JSON est un contrat d'échange stable, la base
un modèle interne. Toute modification de l'un doit vérifier l'autre.
