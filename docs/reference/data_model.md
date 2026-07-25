---
title: Modèle de données interne (Room v4)
type: reference
status: current
updated: 2026-07-25
source: app/src/main/java/com/pontat/registreboucles/data/{Boucle,Mouvement,Journal,Statut,Milieu,SourceBoucle,JournalType,AppDatabase,BoucleDao}.kt, app/schemas/com.pontat.registreboucles.data.AppDatabase/4.json
---

# Modèle de données interne (Room v4)

Ce document décrit le modèle **interne** (tables SQLite / entités Room).
Pour la représentation **JSON** échangée à l'import/export, voir
[`../schema.md`](../schema.md) — les deux ne sont pas identiques (voir
« Écarts connus » en fin de document) et ne doivent pas être confondus.

Base : `registre-boucles.db`, version de schéma **4**, 3 tables.

---

## 1. Table `boucles` — entité `Boucle`

Une « boucle » = un engagement ouvert qui doit être clôturé avec une preuve.

| Colonne | Type SQL | Nullable | Signification métier |
|---|---|---|---|
| `id` | TEXT | non (**PK**) | Identifiant lisible, préfixé par producteur (`B-###` = app, `IA-###` = IA). Non auto-généré : fourni par l'appelant. Cf. [`../schema.md`](../schema.md) §6. |
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

Pas d'index secondaire ni de clé étrangère sur cette table.

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

## 5. Les 4 enums

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

### Tolérance `depuis()` — récapitulatif

| Enum | Accepte | Insensible à la casse | Valeur inconnue |
|---|---|---|---|
| `Statut` | nom d'enum | oui | `null` |
| `Milieu` | nom d'enum **ou** libellé | oui | `null` |
| `SourceBoucle` | nom d'enum | oui | `null` (→ `USER` via `sourceTypee()`) |
| `JournalType` | nom d'enum exact | **non** | `DECLARATION` |

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

Le schéma est exporté et versionné dans `app/schemas/` (v3 et v4 présents)
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
| `observerDernieresModifs()` | `MAX(date) GROUP BY boucleId` sur `mouvements` → étiquette « Modifié le ». |

---

## 8. Écarts connus entre modèle interne et contrat JSON

| Sujet | Interne (ici) | JSON ([`../schema.md`](../schema.md)) |
|---|---|---|
| `tiers` | `TEXT?` | chaîne, **ou** booléen hérité (`true`→`"Oui"`, `false`→`null`) |
| Mouvement | `type` + `contenu` | `{date, note}` — le JSON ne porte pas de type ; l'import fixe `type = "declaration"` |
| Dates | epoch millis (INTEGER) | chaînes ISO-8601 |
| `source` absente | `null` (⇒ `USER`) | à l'import, devient `import` |

Ces écarts sont **voulus** : le JSON est un contrat d'échange stable, la base
un modèle interne. Toute modification de l'un doit vérifier l'autre.
