# registre-boucles-android — contrat de session

## Avant toute chose
Protocole d'investigation obligatoire, à exécuter AVANT tout raisonnement :
`git log --oneline -10` ; `git status` ; `head -40 docs/00_INDEX.md`.
Ne jamais supposer l'état du dépôt — le lire.

## Ce qu'est cette app
Registre-mémoire **hors ligne** (Kotlin/Compose/Room/Glance) qui suit des
« boucles » jusqu'à leur clôture contre preuve. Alimenté par l'utilisateur ET par
des propositions d'IA produites **hors** de l'app, importées puis supervisées.

## Invariants — ne jamais casser
Détail, preuves et tests : `docs/explanation/invariants.md`. Les vérifier là, pas
de mémoire.
- **I1** — Aucun état terminal sans entrée Journal : garanti par
  `executerTransitionTerminale` (canal commande) OU `completerJournaux` (canaux
  réplication et import). Aucun terminal sans journal, quel que soit le chemin.
- **I2** — Gardes de transition (FERMEE depuis actif, REJETEE depuis PROPOSEE) :
  canal commande seul. La réplication applique un état déjà validé à l'origine ;
  les rejouer casserait la convergence. L'import n'est pas de la confiance (AND-10).
- **I3** — « Active » (ouverte ∪ en_cours) définie une seule fois (`estActive`) ;
  le SQL du DAO en est le miroir, jamais une définition concurrente.
- **I4** — Aucune écriture destructive sans backup strict réussi préalable ;
  échec du backup ⇒ opération abandonnée.
- **I5** — Zéro réseau par construction : pas de permission INTERNET, aucun client
  HTTP/SDK ; garde CI en liste blanche de permissions sur le manifest mergé
  (toute permission hors `.github/permissions-allowlist.txt` bloque le build ;
  INTERNET refusée explicitement).
- **I6** — Toute boucle NOUVELLE `source=ia` forcée en `proposee` à l'import,
  quel que soit le statut déclaré (supervision non contournable).
- **I7** — Fusion additive sur TOUS les canaux : jamais d'effacement,
  `id/creee/source` d'une boucle existante toujours préservés. `statut` préservé
  au seul canal import ; en réplication le moteur adopte le statut distant (I13).
- **I8** — Aucune donnée masquée : un statut inconnu reste visible dans « Toutes »
  avec marqueur ; rejeté à l'import.
- **I9** — Tout id de **boucle** porte le préfixe de l'appareil créateur ; le code
  appareil n'est jamais sauvegardé ni restauré (`registre-appareil.xml` exclu).
  Exceptions : captures (`C-<horodatage>-<hex>`, sans coordination, cf. I14) et
  ids historiques tolérés à l'import.
- **I10** — Toute suppression écrit une tombstone dans la même transaction ;
  jamais d'effacement muet.
- **I11** — Un appareil n'écrit QUE son fichier `etat-<CODE>.json` ; il lit les
  autres, ne les modifie jamais.
- **I12** — Toute fusion est précédée d'un backup strict forcé ; échec ⇒ pas de fusion.
- **I13** — Aucun écrasement silencieux : chaque champ remplacé est tracé ;
  l'indécidable devient un conflit, rien n'est écrit.
- **I14** — Une capture n'est jamais supprimée, seulement marquée ; `contenuBrut`
  immuable.
- **I15** — Aucune analyse de contenu dans l'app : pas de mots-clés, pas d'échéance
  déduite, aucune boucle créée automatiquement.
- **I16** — Une proposition n'est pas une décision : l'import ne change aucun statut
  de capture ; `TRAITEE` à l'acceptation, `BRUTE` au rejet.

## Interdits permanents
- ajouter une permission réseau ou une dépendance HTTP
- écrire un état terminal sans garantir son journal (commande :
  `executerTransitionTerminale` ; réplication/import : `completerJournaux`)
- supprimer un mouvement, un journal, une capture ou une tombstone
- écraser des données sans backup strict préalable
- analyser le contenu des captures dans l'app
- trancher un ADR à la place du commanditaire

## Règles de saisie
*Distinctes des invariants : l'app n'analyse aucun contenu (I15), elle ne peut
pas les garantir. Elles relèvent de la saisie.*
- **Aucune donnée nominative** : jamais le nom, le prénom, les initiales ni un
  élément de situation d'une personne accompagnée. Une boucle la concernant est
  nommée par l'action, pas par la personne. Contrepartie de l'absence de
  chiffrement applicatif (ADR-03). Détail : `docs/schema.md`.

## Comment travailler ici
Un LOT = une session. Format : précontrôles, périmètre avec interdits, étapes
ordonnées, gate, contrat de preuve.
Gate standard : `./gradlew test` vert + `assembleRelease` OK + INTERNET = 0.
Commits séparés par étape, messages en français, à l'impératif.
Un lot qui touche au modèle, à un invariant ou au contrat JSON met à jour `docs/`
DANS LE MÊME LOT.

## Où trouver quoi
| Sujet | Fichier |
|---|---|
| Index de toute la doc | `docs/00_INDEX.md` |
| Contrat JSON (import/export/sync) | `docs/schema.md` |
| Invariants (détail + tests) | `docs/explanation/invariants.md` |
| Modèle de données | `docs/reference/data_model.md` |
| Architecture | `docs/explanation/architecture.md` |
| Décisions ouvertes/tranchées (ADR) | `docs/decisions.md` |
