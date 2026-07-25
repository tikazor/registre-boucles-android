# AGENTS.md — contrat de travail sur ce dépôt

À lire **avant** toute modification, que tu sois un agent ou moi-même dans six
mois. Ce fichier ne décrit pas le projet (voir [`README.md`](README.md) et
[`docs/00_INDEX.md`](docs/00_INDEX.md)) : il décrit **comment on y travaille**.

---

## 1. Protocole d'investigation — obligatoire

Ne jamais supposer l'état du dépôt. Avant d'écrire une ligne :

```bash
git log --oneline -10      # où en est-on réellement
git status                 # arbre propre ? branche ?
./gradlew test             # la base est-elle verte ?
```

Puis lire, dans cet ordre :

1. [`docs/00_INDEX.md`](docs/00_INDEX.md) — la carte de la documentation ;
2. [`docs/explanation/invariants.md`](docs/explanation/invariants.md) — **le
   document le plus important du dépôt** ;
3. le ou les documents concernés par le lot.

**Si les tests sont rouges ou l'arbre sale : s'arrêter et le signaler.** On ne
construit pas sur une base instable.

---

## 2. Les 8 invariants — liste courte

Détail, preuve et couverture de test :
[`docs/explanation/invariants.md`](docs/explanation/invariants.md).

| # | Invariant |
|---|---|
| I1 | Aucun état terminal sans entrée `Journal` — `executerTransitionTerminale` est le chemin **unique** |
| I2 | `FERMEE` seulement depuis une boucle active ; `REJETEE` seulement depuis `PROPOSEE` |
| I3 | Une seule définition d'« active » : `Statut.estActive()`, dont les requêtes DAO sont le miroir |
| I4 | Sauvegarde **avant** toute écriture destructive ; échec de sauvegarde ⇒ opération abandonnée |
| I5 | Zéro réseau : pas de permission `INTERNET`, vérifié en CI sur le manifest **mergé** |
| I6 | Supervision non contournable : `source = ia` + boucle nouvelle ⇒ forcée en `proposee` |
| I7 | Fusion additive : jamais de suppression ; `id`, `creee`, `statut`, `source` préservés |
| I8 | Aucune donnée masquée silencieusement (statut inconnu = visible + marqué) |

---

## 3. Interdits permanents

Ils ne sont pas négociables au cas par cas. Si un besoin semble les exiger,
c'est un sujet à arbitrer avec le commanditaire, pas une exception à décider
seul.

1. **Ne pas ajouter de permission réseau** (`INTERNET`), ni client HTTP, ni SDK
   de LLM, ni WorkManager de synchronisation. L'IA est hors de l'app,
   par construction.
2. **Ne pas écrire un statut terminal** (`fermee`, `rejetee`,
   `defaut_applique`) ailleurs que dans `executerTransitionTerminale()`.
3. **Ne jamais supprimer un `Mouvement` ou un `Journal`** — sauf par la
   cascade assumée d'un import « Écraser ».
4. **Ne jamais écraser sans sauvegarde préalable réussie.**
5. **Ne pas trancher un ADR** à la place du commanditaire. Les décisions
   ouvertes de [`docs/decisions.md`](docs/decisions.md) restent ouvertes ; si un
   travail bute dessus, écrire « non tranché — cf. ADR-0X » et continuer.
6. **Ne pas inventer.** Une affirmation dans le code ou la documentation doit
   être vérifiable. À défaut : écrire « à confirmer ».

---

## 4. Format de travail attendu

Le travail se fait par **lots numérotés** `AND-XX`, structurés ainsi :

- **Précontrôles** : les commandes à exécuter et à rapporter *avant* toute
  écriture, avec la condition d'arrêt.
- **Contexte** : le problème réel, pas la solution.
- **Périmètre et interdits absolus** : ce qui est hors sujet, explicitement.
- **Étapes ordonnées** : une intention par étape, vérifiable.
- **Gate** : les conditions objectives de fin.
- **Contrat de preuve** : ce qui doit être rapporté, y compris **ce qui n'a pas
  pu être vérifié**.

Règles de tenue du dépôt :

- Commits en **français**, à l'impératif, un par étape quand les fichiers le
  permettent. Si deux étapes touchent le même fichier et ne peuvent pas être
  séparées proprement, les regrouper **et le dire** dans le message.
- Aucune migration Room sans mention explicite dans le lot ; le schéma exporté
  (`app/schemas/`) est commité avec elle.
- Aucune dépendance ajoutée sans que le lot l'autorise nommément.

---

## 5. Gate standard

```bash
./gradlew test              # doit être vert
./gradlew assembleRelease   # doit compiler
```

Plus, selon le lot : le contrôle anti-réseau, la vérification qu'aucune
migration n'a été ajoutée, l'absence de fichier hors périmètre modifié.

La CI applique le même gate à chaque push sur `main`, et **bloque la Release**
si les tests ou la garde anti-réseau échouent.

---

## 6. La documentation fait partie du livrable

Un lot qui modifie le modèle de données, un invariant, un flux d'import ou la
supervision **met à jour `docs/` dans le même lot**. Une documentation en retard
d'un lot est une documentation fausse, et une documentation fausse est pire que
pas de documentation.

En pratique :

| Ce que tu changes | Ce que tu mets à jour |
|---|---|
| une entité, un enum, une migration | [`docs/reference/data_model.md`](docs/reference/data_model.md) |
| une règle structurelle | [`docs/explanation/invariants.md`](docs/explanation/invariants.md) |
| un flux d'import / la supervision | le how-to concerné + [`docs/schema.md`](docs/schema.md) si le contrat JSON bouge |
| une couche, un écran, la CI | [`docs/explanation/architecture.md`](docs/explanation/architecture.md) |
| n'importe quel lot | [`CHANGELOG.md`](CHANGELOG.md) |

Chaque document porte un frontmatter avec un champ **`source`** listant les
fichiers de code lus pour l'écrire : c'est ce qui rend la documentation
vérifiable, et re-vérifiable.
