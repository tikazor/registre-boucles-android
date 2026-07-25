---
title: Superviser les propositions d'une IA
type: how-to
status: current
updated: 2026-07-25
source: app/src/main/java/com/pontat/registreboucles/ui/screens/SupervisionScreen.kt, ui/BoucleViewModel.kt, data/{BoucleRepository,Cloture,Coercition,SupervisionCaptures,CaptureBoucle}.kt, samples/ia-propositions-exemple.json
---

# Superviser les propositions d'une IA

L'IA est **hors de l'application** : elle ne s'y connecte pas. Le cycle complet
est : *tu demandes → elle produit un JSON → tu l'importes → tu arbitres*.

Depuis AND-09, ce cycle se referme sur les notes capturées : une proposition peut
déclarer **de quelles captures elle est née**, et la boucle finale garde le lien
vers la note d'origine.

```
note partagée ──► capture BRUTE ──► lot d'analyse ──► IA (dehors) ──► propositions
                                                                          │
                     boucle + capture TRAITEE  ◄── tu acceptes ───────────┤
                     capture de nouveau BRUTE  ◄── tu rejettes ──────────┘
```

---

## 1. Faire produire un JSON conforme

Donner à l'IA le fichier [`../schema.md`](../schema.md) **tel quel** comme
contexte : il est auto-suffisant (champs, statuts, provenance, dates,
convention d'identifiants, exemple complet).

Les deux règles qui comptent pour une proposition :

```json
{ "statut": "proposee", "source": "ia", "id": "IA-001" }
```

- `source: "ia"` déclare la provenance ;
- `id` préfixé **`IA-###`** évite toute collision avec les `B-###` créés dans
  l'app ;
- pour **enrichir** une boucle existante, réutiliser son **id exact** (`B-###`)
  et importer en mode Fusionner ;
- si la proposition vient de notes capturées, ajouter **`origines`** avec les `id`
  des captures repris tels quels depuis le lot d'analyse :
  `"origines": ["C-20260725-142233-9f2b"]`. Plusieurs identifiants sont attendus
  quand plusieurs notes parlent du même engagement. Le champ est optionnel, et un
  identifiant inconnu de l'appareil ne bloque rien (il est signalé, la boucle entre
  quand même).

> Si l'IA se trompe et déclare `statut: "ouverte"`, ce n'est pas grave :
> l'application **force** toute boucle IA nouvelle en `proposee` et journalise
> la correction. La supervision ne dépend pas de la rigueur du producteur
> (invariant I6).

Un jeu d'essai complet est fourni : `samples/ia-propositions-exemple.json`
(5 propositions, une avec statut volontairement incorrect, une ciblant un id
existant pour la fusion).

## 2. Importer

**⋮ → Importer un JSON** → choisir le fichier → choisir le mode
(cf. [`importer_des_donnees.md`](importer_des_donnees.md)) :
**Ajouter** pour de nouvelles propositions, **Fusionner** si le fichier
complète des boucles existantes.

Après l'import, un **badge chiffré** apparaît en haut de l'écran Liste. Il est
**masqué s'il n'y a aucune proposition** — c'est la raison la plus fréquente de
ne pas le voir.

> Les propositions **n'apparaissent pas** dans la liste principale et ne sont
> comptées dans aucune tuile de statistiques. Elles n'existent que dans l'écran
> Supervision jusqu'à ton arbitrage. Le compteur « Toutes » ne bouge donc pas
> à cause d'elles.

> **L'import ne touche à aucune capture.** Les liens déclarés par `origines` sont
> enregistrés, mais le statut des notes ne bouge pas : une proposition n'est pas
> une décision (invariant I16). La boîte de réception reste donc telle quelle
> jusqu'à ton arbitrage.

## 3. Arbitrer

Taper le badge ouvre **Supervision** : chaque proposition est affichée avec ses
champs (origine, milieu, preuve attendue, impact, échéance, blocage) et trois
actions.

| Action | Effet | Saisie demandée | Trace laissée |
|---|---|---|---|
| **Accepter** | statut → `ouverte` ; la boucle rejoint le registre | aucune | mouvement `declaration` : « Proposition IA acceptée » |
| **Amender** | ouvre le formulaire d'édition ; l'acceptation suit **l'enregistrement** | les champs que tu modifies | mouvement `declaration` : « Proposition IA acceptée après amendement » |
| **Rejeter** | statut → `rejetee` (état terminal) | **motif obligatoire** | entrée de **journal** `DECLARATION` contenant ton motif |

Trois choses à savoir :

1. **La provenance est conservée.** Une proposition acceptée reste
   `source = ia` : elle affiche un petit marqueur **« IA »** sur sa carte. On
   saura dans six mois qu'elle est entrée par supervision.
2. **Accepter laisse un mouvement, rejeter écrit un journal.** Ce n'est pas une
   incohérence : le rejet est un **état terminal**, qui exige une preuve
   (invariant I1) ; l'acceptation n'est qu'un passage à l'état actif.
3. **Une proposition ne peut pas être clôturée directement** : il faut d'abord
   l'accepter (invariant I2).

Chaque action est précédée d'une **sauvegarde automatique**. Si celle-ci échoue,
l'action est **abandonnée** et un message l'indique — rien n'est modifié.

### Amender : point de comportement à connaître

Dans le flux **Amender**, l'acceptation est déclenchée **par l'enregistrement**
du formulaire. Fermer la feuille sans enregistrer laisse la proposition en
attente ; enregistrer vaut « j'amende **et** j'accepte ». Il n'y a pas
aujourd'hui de « modifier sans accepter ».
→ *à confirmer* : faut-il dissocier les deux ? Non tranché.

### Ce que l'arbitrage fait aux notes d'origine

| Ta décision | La boucle | Les captures liées |
|---|---|---|
| **Accepter** | passe `ouverte`, trace « Proposition IA acceptée (origine : C-…) » | passent **`traitee`** et portent la boucle produite |
| **Amender puis accepter** | idem, trace « … après amendement » | idem |
| **Rejeter** | passe `rejetee` avec ton motif au journal | repassent **`brute`** : la proposition était mauvaise, la note reste bonne et redevient analysable |

**Une note qui a nourri deux propositions.** Si l'une est acceptée et l'autre
rejetée, la capture reste `traitee` — `traitee` est absorbant. Le résultat ne
dépend donc pas de l'ordre dans lequel tu arbitres, et un rejet ne défait jamais
un aboutissement.

## 4. Vérifier après arbitrage

- Le **badge** décroît (et disparaît à zéro).
- Une proposition **acceptée** apparaît dans la liste, statut « Ouverte »,
  marqueur « IA », et son dernier mouvement est la trace d'acceptation.
- Une proposition **rejetée** n'est plus dans Supervision ; elle est visible via
  le filtre **Fermées** (`rejetee` est un état terminal), et son motif est
  consultable par **« Journal des clôtures »** sur sa carte.

### Remonter à la note d'origine

Déplie la boucle : une section **« Origine »** liste les notes qui l'ont fait
naître (date, appareil, application source). Un appui ouvre le **texte intégral**,
non modifiable. Dans l'autre sens, la boîte de réception indique sous chaque
capture la ou les boucles qu'elle a produites.

Une origine déclarée mais absente de cet appareil s'affiche telle quelle, en
signalant l'absence : une trace incomplète reste une trace.

## 5. Ce qui n'a pas été vérifié sur appareil réel

L'ergonomie de l'écran Supervision, le rendu du badge et du marqueur « IA », et
l'affichage du message d'erreur en cas d'échec de sauvegarde n'ont pas été
observés sur un téléphone — le projet n'a pas de test d'interface. À valider à
l'usage.
