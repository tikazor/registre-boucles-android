---
title: Importer des données (Ajouter / Fusionner / Écraser)
type: how-to
status: current
updated: 2026-07-25
source: app/src/main/java/com/pontat/registreboucles/data/BoucleRepository.kt, data/{Fusion,Coercition}.kt, ui/BoucleViewModel.kt, ui/screens/{ListeScreen,FusionScreen}.kt
---

# Importer des données

## Lancer un import

**⋮ (menu en haut à droite) → Importer un JSON**, puis choisir le fichier.
Au tout premier lancement (base vide), l'écran d'accueil propose directement
l'import.

Le fichier doit respecter [`../schema.md`](../schema.md). Si la base contient
déjà des données, l'app demande **quel mode** appliquer.

## Choisir le mode

| | **Ajouter** | **Fusionner** | **Écraser** |
|---|---|---|---|
| Boucle dont l'id est **absent** | créée | créée | créée |
| Boucle dont l'id **existe déjà** | **ignorée** | tu arbitres champ par champ | remplacée par celle du fichier |
| Mouvements / journaux existants | intacts | intacts **+ ajouts dédupliqués** | **supprimés** |
| Destruction possible | non | non | **oui, totale** |

### Ajouter — le mode prudent
N'insère que ce qui manque. Tes boucles existantes, tes clôtures et tes
mouvements ne sont pas touchés. Les boucles du fichier dont l'id existe déjà
sont **purement ignorées** (leurs mouvements aussi).
*À utiliser quand :* tu ajoutes un lot de nouvelles boucles.

### Fusionner — le mode d'enrichissement
Le seul mode qui permet à un producteur externe de **compléter** une boucle
existante.
- Mouvements et journaux entrants sont **toujours ajoutés**, dédupliqués sur
  `(boucleId, date, contenu/texte)`. Rien n'est jamais supprimé.
- Pour chaque boucle existante dont un champ diverge, un écran d'arbitrage
  affiche le diff et te propose **« Garder l'existant »** (défaut) ou
  **« Prendre l'entrant »**. Le choix est **par boucle**, pas par champ.
- Même en prenant l'entrant, `id`, date de création, **statut** et **provenance**
  de ta version sont préservés : un fichier ne peut pas réécrire le cycle de vie
  (invariant I7).

*À utiliser quand :* une IA propose des compléments sur des boucles que tu as
déjà.

### Écraser — le mode destructeur
Vide les trois tables puis réinsère le contenu du fichier. **Tout ce qui n'est
pas dans le fichier est perdu** : boucles, mouvements, journaux.
*À utiliser quand :* tu restaures un backup (voir
[`restaurer_un_backup.md`](restaurer_un_backup.md)) — c'est son usage légitime.

> Sur une base vide, l'import s'exécute directement en mode Écraser, sans
> question : il n'y a rien à préserver.

## Ce que l'app garantit dans les trois cas

1. **Un backup complet est créé avant toute écriture.** Si ce backup échoue,
   **l'import est annulé** et un message le dit — rien n'est écrasé sans filet
   (invariant I4).
2. **Les propositions IA ne peuvent pas entrer actives.** Toute boucle
   *nouvelle* avec `source: "ia"` est ramenée au statut `proposee`, même si le
   fichier déclarait `ouverte`, et un mouvement trace la correction
   (invariant I6). Elle apparaît alors dans Supervision, pas dans la liste.
3. **Un fichier invalide est rejeté en bloc**, avec un message qui nomme la
   boucle et le champ fautif : statut inconnu, date malformée, JSON invalide,
   fichier vide. Aucun import partiel.

## Où sont les backups automatiques

Dans le stockage privé de l'app :
`Android/data/com.pontat.registreboucles/files/backups/`, nommés
`boucles-backup-<horodatage>.json`.

- **Rotation : les 10 plus récents** sont conservés.
- **Anti-rafale :** un backup de moins de 5 minutes est réutilisé plutôt que
  dupliqué (sauf pour les imports, qui en forcent toujours un frais).
- Ils sont inclus dans la sauvegarde système Android, mais restent liés à
  l'app : pour les mettre à l'abri, utilise **Réglages → Exporter le dernier
  backup**.

## Vérifier après import

- Le compteur « Toutes » a augmenté du nombre de boucles réellement insérées.
- Si le fichier contenait des propositions IA, le **badge Supervision** apparaît
  en haut à gauche avec leur nombre.
- Les propositions ne sont **pas** dans la liste principale tant qu'elles ne
  sont pas acceptées : c'est normal (cf.
  [`superviser_les_propositions_ia.md`](superviser_les_propositions_ia.md)).
