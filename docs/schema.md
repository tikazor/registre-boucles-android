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
  "version": 2,
  "boucles": [ /* obligatoire, au moins une */ ],
  "journaux": [ /* optionnel */ ]
}
```

| Champ      | Type          | Obligatoire | Rôle |
|------------|---------------|-------------|------|
| `version`  | entier        | non         | `2` = format courant. Absent ou `1` = format hérité (toléré, sans `journaux`). |
| `boucles`  | tableau       | **oui**     | Les boucles. La liste ne doit pas être vide. |
| `journaux` | tableau       | non         | Le journal (preuves de clôture / rejet). Absent = aucun. |

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

| Préfixe   | Producteur |
|-----------|------------|
| `B-###`   | Boucles créées **dans l'application**. |
| `IA-###`  | Boucles **proposées par une IA**. |

L'application n'émet QUE des `B-###` et ignore les autres préfixes dans le calcul
du prochain numéro : une IA qui numérote en `IA-###` ne provoquera jamais de
collision. Un producteur tiers doit choisir un préfixe distinct.

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

## 9. Exemple complet et minimal valide

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
  "journaux": []
}
```
