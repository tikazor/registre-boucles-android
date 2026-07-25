# Contrat JSON — Register Mnemosyne

Ce fichier est le **contrat** entre l'application et tout producteur de données
(l'utilisateur, une IA, un script). Un fichier conforme à ce document peut être
importé sans perte. Il est auto-suffisant : donné tel quel à une IA, il permet
de produire un JSON valide sans autre explication.

> ⚠️ L'application est **hors ligne** : elle ne fait aucun appel réseau. Un
> producteur externe (IA incluse) produit un fichier JSON ; l'utilisateur
> l'importe manuellement et le supervise. Rien n'entre dans le registre actif
> sans une action explicite de l'utilisateur.

---

## 1. Structure racine

```json
{
  "version": 3,
  "codeAppareil": "B",
  "exporteLe": 1769337600000,
  "boucles": [ /* obligatoire, au moins une */ ],
  "journaux": [ /* optionnel */ ],
  "suppressions": [ /* optionnel */ ]
}
```

| Champ          | Type          | Obligatoire | Rôle |
|----------------|---------------|-------------|------|
| `version`      | entier        | non         | `3` = format courant. Absent, `1` ou `2` = format hérité, toléré (cf. §9). |
| `codeAppareil` | chaîne        | non (v3+)   | Appareil émetteur du fichier (cf. §6). Signalé à l'import. |
| `exporteLe`    | entier        | non (v3+)   | Date d'export en **epoch millis** — métadonnée machine, seule date non ISO du document. |
| `boucles`      | tableau       | **oui**     | Les boucles. La liste ne doit pas être vide. |
| `journaux`     | tableau       | non         | Le journal (preuves de clôture / rejet). Absent = aucun. |
| `suppressions` | tableau       | non (v3+)   | Traces de suppression (cf. §7 ter). Absent = aucune. |

---

## 2. Objet `boucle`

| Champ            | Type            | Obligatoire | Notes |
|------------------|-----------------|-------------|-------|
| `id`             | chaîne          | **oui**     | Identifiant unique. Voir §6 (préfixe par producteur). |
| `type`           | chaîne libre    | **oui**     | Ex. `ACTION`, `DECISION`. |
| `titre`          | chaîne          | **oui**     | Titre court. |
| `origine`        | chaîne          | **oui**     | D'où vient la boucle. |
| `creee`          | date ISO-8601   | **oui**     | Voir §3. |
| `echeance`       | date ISO-8601   | non         | `null`/absent = pas d'échéance. |
| `tiers`          | chaîne \| bool  | non         | Chaîne libre (ex. `"Cadre référent"`). Booléen hérité toléré : `true`→`"Oui"`, `false`→`null`. Absent = `null`. |
| `preuveAttendue` | chaîne          | **oui**     | Ce qui prouvera la clôture. |
| `blocage`        | chaîne          | non         | Frein courant. |
| `impact`         | chaîne          | **oui**     | Conséquence si non traité. |
| `defaut`         | chaîne          | non         | Action par défaut si rien n'est tranché. |
| `statut`         | énum (voir §4)  | **oui**     | Valeur inconnue = import **rejeté**. |
| `milieu`         | énum (voir §5)  | non         | `null`/absent accepté. |
| `source`         | énum (voir §5)  | non         | Provenance. Absent à l'import = `import`. |
| `modifieeLe`     | date ISO-8601   | non (v3+)   | Dernière modification. Absent = jamais modifiée depuis `creee`. |
| `modifieePar`    | chaîne          | non (v3+)   | Code de l'appareil auteur de la dernière modification. |
| `mouvements`     | tableau         | non         | Voir §7. |

---

## 3. Dates

Format **ISO-8601**, trois formes acceptées :

- instant : `2026-04-12T10:00:00Z`
- date-heure locale (interprétée en UTC) : `2026-04-12T10:00:00`
- date seule (début de journée UTC) : `2026-04-12`

Une date malformée fait **échouer l'import** avec un message citant la boucle et
le champ.

---

## 4. `statut` — valeurs autorisées

| Valeur            | Catégorie              | Ce que ça implique |
|-------------------|------------------------|--------------------|
| `ouverte`         | **active**             | À traiter. Visible dans la liste et le widget. |
| `en_cours`        | **active**             | Idem `ouverte`. |
| `fermee`          | **terminale**          | Clôturée. Exige une entrée de journal. |
| `defaut_applique` | **terminale**          | Action par défaut appliquée. Exige un journal. |
| `proposee`        | **en supervision**     | Proposition (typiquement IA). **Absente** de la liste et du widget ; n'apparaît que dans l'écran Supervision. |
| `rejetee`         | **terminale**          | Proposition refusée. Exige un journal (motif). |

Invariant : une boucle ne devient **terminale** que via une entrée de journal.
Une boucle `proposee` ne peut pas être clôturée ; elle doit d'abord être
acceptée (→ `ouverte`) ou rejetée (→ `rejetee`).

### Coercition des propositions IA (la supervision est une propriété de l'app)

La supervision ne dépend PAS de la bonne volonté du producteur. Le producteur
**doit** émettre `statut: "proposee"` pour une boucle `source: "ia"` ; **s'il ne
le fait pas, l'application corrige et trace** :

- Toute boucle **nouvelle** (id absent de la base au moment de l'écriture)
  avec `source: "ia"` est **forcée en `proposee`**, quel que soit le statut
  déclaré. Elle passe donc obligatoirement par l'écran Supervision.
- L'intention du producteur n'est pas perdue : si le statut déclaré différait,
  un **mouvement `declaration`** est ajouté :
  « Statut déclaré "<valeur>" ramené à "proposee" (source IA, supervision
  obligatoire) ».
- La règle vaut pour les **trois modes** d'import (Ajouter / Écraser /
  Fusionner), pour les boucles **nouvelles** uniquement. Une boucle
  **existante** enrichie par Fusionner conserve son statut (cf. §8).

Autrement dit : `source: "ia"` + `statut: "ouverte"` dans un fichier n'entre
jamais directement dans le registre actif — l'app le ramène en `proposee`.

---

## 5. `milieu` et `source`

**`milieu`** (insensible à la casse ; nom ou libellé) :
`pro` (Professionnel), `gouvernance` (Gouvernance), `projet` (Projet),
`perso` (Personnel).

**`source`** (provenance ; insensible à la casse) :

| Valeur   | Sens |
|----------|------|
| `user`   | Saisie dans l'app. |
| `ia`     | Proposée par une IA (supervision requise). |
| `import` | Entrée par import sans provenance déclarée (défaut si `source` absent). |

---

## 6. Convention d'identifiant (anti-collision)

Chaque **producteur** utilise son propre préfixe, pour qu'aucun ne réutilise le
numéro d'un autre :

| Préfixe    | Producteur |
|------------|------------|
| `B-###`    | Boucles créées sur l'appareil dont le **code appareil** est `B`. |
| `PRO-###`  | Boucles créées sur l'appareil dont le code est `PRO`. |
| `IA-###`   | Boucles **proposées par une IA**. |

**Chaque appareil porte un code de 1 à 4 lettres majuscules** (ADR-02, tranché le
25/07/2026), choisi au premier lancement et immuable ensuite. Un appareil n'émet
que des ids `<SON CODE>-###` et **ne compte que les siens** pour calculer le
suivant : deux appareils qui créent des boucles en parallèle ne peuvent donc pas
produire le même identifiant, sans aucune coordination entre eux (invariant I9).

Format attendu : `^[A-Z]{1,4}-\d{3,}$`. Un identifiant hors convention (données
historiques) est **toléré** à l'import — jamais rejeté, jamais renuméroté — et
seulement signalé dans le rapport d'import.

Pour **enrichir** une boucle existante (mode Fusionner, §8), le producteur
réutilise l'**id exact** de la boucle cible (typiquement un `B-###`).

---

## 7. Objet `mouvement`

Dans `boucle.mouvements` :

```json
{ "date": "2026-07-20T09:00:00Z", "note": "Texte libre du mouvement" }
```

| Champ  | Type          | Obligatoire |
|--------|---------------|-------------|
| `date` | date ISO-8601 | **oui**     |
| `note` | chaîne        | **oui**     |

## 7 bis. Objet `journal` (racine `journaux`)

```json
{ "boucleId": "B-003", "date": "2026-07-21T15:30:00Z", "type": "DECLARATION", "texte": "Motif / preuve" }
```

| Champ      | Type          | Obligatoire | Notes |
|------------|---------------|-------------|-------|
| `boucleId` | chaîne        | **oui**     | Id de la boucle concernée. |
| `date`     | date ISO-8601 | **oui**     | |
| `type`     | énum          | **oui**     | `PREUVE`, `DECLARATION`, `DEFAUT`. |
| `texte`    | chaîne        | **oui**     | Preuve ou motif. |

---

## 7 ter. Objet `suppression` (racine `suppressions`, v3+)

```json
{ "boucleId": "B-013", "supprimeeLe": "2026-07-25T09:12:00Z", "supprimeePar": "B" }
```

| Champ          | Type          | Obligatoire | Notes |
|----------------|---------------|-------------|-------|
| `boucleId`     | chaîne        | **oui**     | Id de la boucle supprimée. |
| `supprimeeLe`  | date ISO-8601 | **oui**     | Quand. |
| `supprimeePar` | chaîne        | **oui**     | Code de l'appareil qui a supprimé. |

Une suppression laisse **toujours** cette trace (invariant I10) : sans elle, une
boucle effacée sur un appareil serait ressuscitée par le premier import venant de
l'autre. Ces traces ne sont jamais purgées.

> **État actuel :** les traces sont **enregistrées et échangées**, mais pas encore
> exploitées à l'import. En **synchronisation** (AND-08), elles le sont : une
> tombstone plus récente que la modification entrante empêche la résurrection, et
> une tombstone visant une boucle encore vivante ici ouvre un arbitrage manuel —
> jamais une suppression d'office (cf. §10).

---

## 8. Modes d'import

À l'import d'un fichier alors que la base contient déjà des données,
l'utilisateur choisit un mode. **Un backup automatique est créé avant toute
écriture** dans les trois cas ; si le backup échoue, l'import est annulé.

| Mode           | Boucles                                   | Mouvements / journaux | Destruction |
|----------------|-------------------------------------------|-----------------------|-------------|
| **Ajouter**    | insère seulement les **ids absents**      | ajoutés pour les nouvelles boucles | aucune |
| **Fusionner**  | ids absents créés (avec leur statut d'origine) ; ids existants : arbitrage **par boucle** « garder l'existant / prendre l'entrant » sur les champs scalaires (id, `creee`, `statut`, `source` **toujours préservés**) | **toujours ajoutés**, dédupliqués sur `(boucleId, date, note/texte)` ; jamais supprimés | aucune |
| **Écraser**    | remplace **tout** par le fichier          | remplacés par le fichier | **totale** |

Sur base vide, l'import se comporte comme « Écraser » (rien à préserver).

Une boucle entrante en `proposee` (source `ia`) dont l'id est absent est créée
telle quelle et n'apparaît **que** dans l'écran Supervision jusqu'à acceptation.

---

## 9. Compatibilité des versions

| | v1 (hérité) | v2 | v3 (courant) |
|---|---|---|---|
| `version` | absent | `2` | `3` |
| `boucles` | ✅ | ✅ | ✅ |
| `journaux` | absent | ✅ | ✅ |
| `tiers` | booléen | chaîne (booléen toléré) | chaîne (booléen toléré) |
| `codeAppareil`, `exporteLe` | absent | absent | ✅ |
| `modifieeLe`, `modifieePar` | absent | absent | ✅ |
| `suppressions` | absent | absent | ✅ |

**Les trois versions restent importables.** Tout champ ajouté depuis a une
valeur par défaut : `version` absent = 1, `journaux`/`suppressions` absents =
listes vides, `modifieeLe` absent = la boucle se lit comme « jamais modifiée
depuis `creee` ». L'export écrit toujours la version courante.

---

## 10. Protocole de synchronisation (AND-08)

Le même format v3 sert de **format d'échange entre appareils**. La
synchronisation est manuelle et passe par un dossier partagé qu'une application
tierce réplique ; l'application, elle, n'accède jamais au réseau.

### 10.1 Nommage et propriété des fichiers

| Règle | Détail |
|---|---|
| Nom | `etat-<CODE>.json`, où `<CODE>` est le code appareil (`^[A-Z]{1,4}$`) |
| Écriture | Un appareil n'écrit **que** son propre fichier (invariant I11) |
| Émetteur | Déterminé par le **nom du fichier**, pas par le champ `codeAppareil` du contenu |
| Contenu | Format v3 **complet** : `boucles` (avec leurs `mouvements`), `journaux`, `suppressions`, plus `codeAppareil` et `exporteLe` |
| Fichiers ignorés | Tout nom non conforme, et les copies de conflit des applis de synchro (`etat-PRO (1).json`, `etat-PRO.json.conflict`) |

Un fichier illisible ou tronqué est **refusé en bloc** et consigné en échec au
journal de synchronisation : il n'est jamais fusionné à moitié.

### 10.2 Règles de résolution

Appliquées dans cet ordre par `calculerFusionSync()` (fonction pure) :

| # | Cas | Décision |
|---|---|---|
| 0 | `exporteLe` en avance de plus de **10 min** sur l'heure locale | Fusion **interrompue**, confirmation explicite demandée |
| 1 | Mouvements et journaux | **Union** dédupliquée sur `(boucleId, date, contenu)` et `(boucleId, date, texte)` ; aucune suppression |
| 2 | Boucle inconnue localement, sans tombstone | Insérée telle quelle |
| 3 | Boucle inconnue localement, avec tombstone | `supprimeeLe >= derniereModification` → reste supprimée ; sinon **ressuscitée**, tombstone retirée |
| 4a | Un seul côté **terminal** | Le côté terminal l'emporte sur tous les champs scalaires, **quelles que soient les dates** |
| 4b | Écart des `derniereModification` **> 60 s** | Le plus récent gagne ; **chaque champ écrasé produit un mouvement** de traçage |
| 4b | Écart **≤ 60 s** avec champs divergents | **Conflit** : rien n'est écrit, arbitrage manuel |
| 4c | `id`, `creee`, `source` | **Jamais** écrasés |
| 5 | Tombstone entrante visant une boucle vivante ici | **Conflit** : rien n'est supprimé (la CASCADE emporterait mouvements et journaux) |

`derniereModification()` = `modifieeLe` s'il est présent, sinon `creee`. Un
fichier v1/v2 (sans `modifieeLe`) est donc arbitré sur sa date de création.

### 10.3 Traçage des écrasements

Chaque champ remplacé écrit un mouvement `declaration` au libellé figé :

```
titre : "ancien titre" remplacé par "nouveau titre" (sync depuis PRO)
```

Après un arbitrage manuel, le libellé devient
`… (arbitrage d'un conflit avec PRO)`. Une boucle `source: "ia"` reçue avec un
statut actif est tracée par
`Boucle IA reçue de PRO avec le statut "ouverte" (appareil pair, supervision non rejouée)`.

### 10.4 Propriétés garanties

- **Idempotence** : fusionner deux fois le même fichier ne change rien la seconde
  fois. Une boucle adoptée conserve le `modifieeLe` / `modifieePar` de
  l'**émetteur** — l'estampiller localement casserait cette propriété.
- **Symétrie** : A fusionne le fichier de B, puis B celui de A → les deux bases
  convergent, et il ne reste plus rien à échanger.
- **Aucune perte** : mouvements et journaux ne sont jamais supprimés, et aucun
  champ n'est écrasé sans trace.

### 10.5 Ce que le protocole ne fait pas

Aucune propagation automatique des suppressions, aucune fusion automatique
(pas de tâche de fond), aucune coercition des propositions IA sur ce canal —
le dossier partagé est un canal de confiance, comme l'appareil lui-même.

---

## 10 bis. Format « lot d'analyse » (sortie de la capture)

Format de **SORTIE**, distinct du contrat d'échange des boucles. Il transporte des
notes brutes capturées depuis d'autres applications, pour être analysées
**hors de l'application**.

```json
{
  "version": 1,
  "type": "lot-analyse",
  "exporteLe": 1784982153000,
  "captures": [
    {
      "id": "C-20260725-142233-9f2b",
      "contenuBrut": "Rappeler Marie au sujet du dossier\nvoir aussi la convention",
      "titre": "Dossier Marie",
      "appareil": "B",
      "appSource": "com.miui.notes",
      "capturee": 1784982153000
    }
  ]
}
```

| Champ | Type | Rôle |
|---|---|---|
| `version` | entier | `1`. Numérotation propre au lot, **indépendante** de la version du contrat des boucles. |
| `type` | chaîne | `"lot-analyse"`. Permet de distinguer ce fichier d'un export de registre au premier coup d'œil. |
| `exporteLe` | entier | Epoch millis. |
| `captures[].id` | chaîne | `C-<aaaaMMjj-HHmmss>-<4 hex>`. |
| `captures[].contenuBrut` | chaîne | Le texte **tel quel** (tronqué au-delà de 100 000 caractères, la troncature étant écrite dans le contenu). |
| `captures[].titre` | chaîne | Sujet fourni par l'app source, ou absent. |
| `captures[].appareil` | chaîne | Code appareil, nom libre, ou `LOCAL`. |
| `captures[].appSource` | chaîne | Paquet de l'app émettrice, ou absent. |
| `captures[].capturee` | entier | Epoch millis. **Seules dates en millis du document** : ce sont des métadonnées machine. |

Nom de fichier : `lot-analyse-<aaaaMMjj-HHmm>.json`.

### Ce que le lot ne contient pas, et pourquoi

Ni statut de capture, ni empreinte, ni boucle liée, ni le moindre champ de
registre. Le lot est de la **matière première**, pas un état : ce qui relève du
suivi reste dans l'application.

### L'analyse est externe — comment le résultat revient

Le lot **n'est pas réimportable** : rien ne rentre par cette porte. L'application
ne contient aucune analyse de contenu — pas de mots-clés, pas de détection
d'échéance, aucune création automatique de boucle (invariant I15).

Le chemin de retour est celui qui existe déjà : un producteur externe (IA incluse)
lit le lot, produit un JSON de **propositions** conforme au contrat ci-dessus
(`source: "ia"`), l'utilisateur l'importe à la main, et ces boucles entrent en
`proposee` — donc dans l'écran Supervision, où elles sont acceptées, amendées ou
rejetées. Une capture reste liée à la boucle qu'elle a produite (`boucleLiee`)
seulement si la boucle a été créée **depuis la boîte de réception**, à la main.

---

## 11. Exemple complet et minimal valide

```json
{
  "version": 2,
  "boucles": [
    {
      "id": "IA-001",
      "type": "ACTION",
      "titre": "Vérifier la cohérence des sauvegardes hebdomadaires",
      "origine": "Analyse automatique des journaux d'export",
      "creee": "2026-07-20",
      "echeance": "2026-08-03",
      "tiers": "Équipe technique",
      "preuveAttendue": "Rapport de restauration testée sur un jeu récent",
      "impact": "Sauvegardes potentiellement non restaurables sans que ce soit détecté",
      "statut": "proposee",
      "milieu": "pro",
      "source": "ia",
      "mouvements": [
        { "date": "2026-07-20T08:00:00Z", "note": "Écart détecté sur la rotation des fichiers" }
      ]
    }
  ],
  "journaux": [],
  "suppressions": []
}
```
