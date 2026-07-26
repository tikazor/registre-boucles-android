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

## 2. Les 16 invariants — liste courte

Détail, preuve et couverture de test :
[`docs/explanation/invariants.md`](docs/explanation/invariants.md). Version
condensée identique à celle de [`CLAUDE.md`](CLAUDE.md) — la même vérité, pas une
troisième formulation.

| # | Invariant |
|---|---|
| I1 | Aucun état terminal sans entrée `Journal` : garanti par `executerTransitionTerminale` (canal commande) OU `completerJournaux` (canaux réplication et import). Aucun terminal sans journal, quel que soit le chemin |
| I2 | Gardes de transition (`FERMEE` depuis actif, `REJETEE` depuis `PROPOSEE`) : canal commande seul. La réplication applique un état déjà validé à l'origine ; les rejouer casserait la convergence. L'import n'est pas de la confiance (AND-10) |
| I3 | « Active » (ouverte ∪ en_cours) définie une seule fois (`estActive`) ; le SQL du DAO en est le miroir, jamais une définition concurrente |
| I4 | Aucune écriture destructive sans backup strict réussi préalable ; échec du backup ⇒ opération abandonnée |
| I5 | Zéro réseau par construction : pas de permission `INTERNET`, aucun client HTTP/SDK ; garde CI bloquante sur le manifest mergé |
| I6 | Toute boucle NOUVELLE `source=ia` forcée en `proposee` à l'import, quel que soit le statut déclaré (supervision non contournable) |
| I7 | Fusion additive sur TOUS les canaux : jamais d'effacement, `id/creee/source` d'une boucle existante toujours préservés. `statut` préservé au seul canal import ; en réplication le moteur adopte le statut distant (I13) |
| I8 | Aucune donnée masquée : un statut inconnu reste visible dans « Toutes » avec marqueur ; rejeté à l'import |
| I9 | Tout id de **boucle** porte le préfixe de l'appareil créateur ; le code appareil n'est jamais sauvegardé ni restauré (`registre-appareil.xml` exclu). Exceptions : captures (`C-<horodatage>-<hex>`) et ids historiques tolérés à l'import |
| I10 | Toute suppression écrit une tombstone dans la même transaction ; jamais d'effacement muet |
| I11 | Un appareil n'écrit QUE son fichier `etat-<CODE>.json` ; il lit les autres, ne les modifie jamais |
| I12 | Toute fusion est précédée d'un backup strict forcé ; échec ⇒ pas de fusion |
| I13 | Aucun écrasement silencieux : chaque champ remplacé est tracé ; l'indécidable devient un conflit, rien n'est écrit |
| I14 | Une capture n'est jamais supprimée, seulement marquée ; `contenuBrut` immuable |
| I15 | Aucune analyse de contenu dans l'app : pas de mots-clés, pas d'échéance déduite, aucune boucle créée automatiquement |
| I16 | Une proposition n'est pas une décision : l'import ne change aucun statut de capture ; `TRAITEE` à l'acceptation, `BRUTE` au rejet |

---

## 3. Interdits permanents

Ils ne sont pas négociables au cas par cas. Si un besoin semble les exiger,
c'est un sujet à arbitrer avec le commanditaire, pas une exception à décider
seul.

1. **Ne pas ajouter de permission réseau** (`INTERNET`), ni client HTTP, ni SDK
   de LLM, ni WorkManager de synchronisation. L'IA est hors de l'app,
   par construction.
2. **Ne pas écrire un statut terminal** (`fermee`, `rejetee`,
   `defaut_applique`) sans garantir son `Journal` : au canal commande via
   `executerTransitionTerminale()`, aux canaux réplication et import via
   `completerJournaux()`.
3. **Ne jamais supprimer un `Mouvement`, un `Journal`, une capture ou une
   tombstone.**
4. **Ne jamais écraser sans sauvegarde préalable réussie.**
5. **Ne pas analyser le contenu des captures dans l'app** (pas de mots-clés,
   pas d'échéance déduite, aucune boucle créée automatiquement).
6. **Ne pas trancher un ADR** à la place du commanditaire. Les décisions
   ouvertes de [`docs/decisions.md`](docs/decisions.md) restent ouvertes ; si un
   travail bute dessus, écrire « non tranché — cf. ADR-0X » et continuer.
7. **Ne pas inventer.** Une affirmation dans le code ou la documentation doit
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
