---
title: Synchroniser deux appareils
type: how-to
status: current
updated: 2026-07-25
source: app/src/main/java/com/pontat/registreboucles/data/{FusionSync,DossierSync,EvenementSync,BoucleRepository}.kt, ui/screens/SyncScreen.kt, ui/BoucleViewModel.kt
---

# Synchroniser deux appareils

## En bref

Chaque appareil dépose son état complet dans un dossier partagé, sous son propre
nom de fichier, et lit celui des autres. Une application tierce (Nextcloud,
Syncthing, Drive…) transporte les fichiers ; **Mnemosyne ne touche jamais au
réseau** — elle lit et écrit des fichiers, rien d'autre.

```
Appareil B                 dossier partagé                Appareil PRO
────────────              ─────────────────               ────────────
écrit  ──────────────────►  etat-B.json    ◄───────────────  lit
lit    ◄──────────────────  etat-PRO.json  ──────────────►  écrit
```

Un appareil **n'écrit que son propre fichier** (invariant I11). C'est cette
règle, à elle seule, qui élimine les conflits d'écriture : deux appareils ne
modifient jamais le même fichier, même en même temps.

La synchronisation est **manuelle** : un bouton. Rien ne part et rien n'arrive
sans que tu le demandes.

---

## 1. Mise en place, une fois par appareil

**a. Choisir un dossier synchronisé.** Dans l'application de ton choix
(Nextcloud, Syncthing, Drive), crée un dossier — par exemple `Mnemosyne/` — et
assure-toi qu'il est répliqué sur les deux appareils.

**b. Désigner ce dossier dans Mnemosyne.** Réglages → Synchronisation →
**Choisir le dossier**. Le sélecteur de fichiers Android s'ouvre : navigue
jusqu'au dossier et valide. L'autorisation est *persistante* — tu n'auras pas à
la redonner au redémarrage.

**c. Vérifier le code appareil.** Le même écran affiche le code de cet appareil
et le nom du fichier qu'il écrira (`etat-B.json` pour le code `B`). Deux
appareils ne doivent **jamais** porter le même code : ils écriraient le même
fichier et s'écraseraient mutuellement. Si tu vois le même code des deux côtés,
change-en un dans Réglages → Identité de cet appareil **avant** la première
synchronisation.

**d. Vérifier l'heure des deux appareils.** L'arbitrage repose sur les dates de
modification. Une horloge fausse fait prendre de mauvaises décisions ;
l'application refuse d'ailleurs de fusionner un fichier qui prétend venir du
futur (voir plus bas).

## 2. Premier appairage

1. Sur l'appareil A : **Synchroniser maintenant**. Le compte rendu dira qu'aucun
   fichier d'un autre appareil n'a été trouvé — c'est normal : A vient de déposer
   `etat-A.json`.
2. Attends que ton application de synchronisation ait répliqué le dossier (elle
   affiche généralement une coche ou « à jour »).
3. Sur l'appareil B : **Synchroniser maintenant**. B lit `etat-A.json`, fusionne,
   puis dépose `etat-B.json`.
4. Reviens sur A et synchronise une seconde fois, pour que A reçoive ce que B
   avait de son côté.

Après ce double aller-retour, les deux registres portent les mêmes données.

> **Attends la réplication entre les deux étapes.** Si tu synchronises sur B
> avant que le fichier de A ne soit arrivé, B ne verra rien : ce n'est pas une
> erreur, juste un dossier pas encore à jour. Recommence après.

## 3. Usage quotidien

Un appui sur **Synchroniser maintenant**, sur chaque appareil, quand tu y penses
— par exemple en fin de journée. L'ordre des opérations est toujours :

1. **une sauvegarde complète est créée** (invariant I12) ; si elle échoue, rien
   n'est fusionné ;
2. chaque fichier d'état des autres appareils est lu et fusionné ;
3. notre propre fichier est réécrit ;
4. une ligne est ajoutée à l'historique des synchronisations, définitivement.

Ce qui se passe pendant la fusion :

| Situation | Décision |
|---|---|
| Mouvements et journaux | **Union**, sans doublon. Rien n'est jamais supprimé. |
| Boucle absente ici | Elle entre. |
| Boucle absente ici, mais supprimée ici auparavant | La date la plus récente gagne : suppression conservée, ou boucle ressuscitée. |
| Boucle clôturée d'un seul côté | **La clôture gagne**, quelles que soient les dates. Une clôture journalisée est un fait. |
| Boucle modifiée des deux côtés, à plus d'une minute d'écart | La version la plus récente gagne, et **chaque champ écrasé laisse un mouvement** dans l'historique de la boucle : « titre : "…" remplacé par "…" (sync depuis PRO) ». |
| Boucle modifiée des deux côtés à moins d'une minute d'écart | **Conflit** : rien n'est écrit, tu arbitres (section suivante). |
| Boucle supprimée sur l'autre appareil, encore ici | **Conflit** : rien n'est supprimé. |

`id`, date de création et provenance (`source`) ne sont jamais écrasés.

## 4. Que faire en cas de conflit

Les conflits apparaissent dans l'écran Synchronisation, sous « boucle(s) à
arbitrer », avec le détail champ par champ (le même affichage que l'arbitrage du
mode « Fusionner »).

**Conflit de modification** — les deux appareils ont touché la boucle presque en
même temps :
- **Garder le local** : ta version d'ici est conservée et redatée. À la
  prochaine synchronisation, l'autre appareil l'adoptera.
- **Prendre le distant** : la version de l'autre appareil est adoptée ici, et
  **chaque champ remplacé laisse un mouvement** dans l'historique de la boucle.

**Conflit de suppression** — la boucle a été supprimée là-bas et vit encore ici :
- **Garder ici** : la boucle est redatée ; l'autre appareil la ressuscitera à sa
  prochaine synchronisation.
- **Supprimer ici aussi** : suppression normale, avec sa trace.

Dans les deux cas, arbitrer revient à dire « c'est cette version qui fait foi » —
l'appareil d'en face suivra tout seul.

> Rien ne t'oblige à arbitrer tout de suite. Un conflit non arbitré réapparaît à
> la synchronisation suivante : il n'est pas perdu, et aucune donnée ne bouge
> entretemps. Les conflits ne sont pas stockés en base, ils sont **recalculés**.

## 5. « L'autre appareil semble en avance de X »

La fusion s'est interrompue : le fichier lu déclare avoir été exporté dans le
futur, de plus de 10 minutes. Comme l'arbitrage « le plus récent gagne » repose
sur les dates, une horloge fausse peut faire écraser une version pourtant plus
récente.

Ce qu'il faut faire, dans l'ordre :
1. vérifier l'heure des deux appareils (fuseau et réglage automatique) ;
2. corriger l'appareil fautif et **y** relancer une synchronisation, pour qu'il
   réécrive son fichier avec une date correcte ;
3. resynchroniser ici.

Si tu es certain de ce que tu fais, « Fusionner quand même » applique la fusion
avec les dates telles quelles. L'événement reste consigné à l'historique avec
l'écart constaté.

## 6. Si un appareil est perdu, volé ou remplacé

Rien à faire côté protocole : les données sont complètes sur l'appareil restant
et dans le dossier partagé.

1. **Ne réutilise pas son code.** Un appareil neuf choisit un code **nouveau**
   (`C`, `TEL2`…). Réutiliser l'ancien ferait réémettre des identifiants déjà
   attribués. L'identité n'est d'ailleurs jamais restaurée par une sauvegarde
   système (invariant I9) : l'appareil neuf te demandera un code.
2. **Récupère les données** en synchronisant : le nouvel appareil lit les
   fichiers d'état présents dans le dossier et repart de l'état commun.
3. **Le fichier de l'appareil perdu** (`etat-<CODE>.json`) peut rester dans le
   dossier : il sera relu à chaque synchronisation, sans effet une fois fusionné
   (la fusion est idempotente). Tu peux aussi le supprimer une fois que ses
   données sont bien arrivées — c'est un fichier, pas une source de vérité.
4. **Appareil volé** : le dossier partagé reste accessible depuis cet appareil
   tant que son accès n'est pas révoqué. Coupe le partage côté service de
   synchronisation (c'est lui qui détient les droits, pas Mnemosyne).

## Ce que cette synchronisation ne fait pas

- **Rien d'automatique.** Pas de tâche de fond, pas de réveil périodique : la
  synchronisation est un geste explicite.
- **Aucun réseau.** L'application n'a pas la permission `INTERNET` et n'embarque
  aucun client HTTP (invariant I5). Le transport est le problème de ton
  application de synchronisation.
- **Aucune fusion partielle.** Un fichier tronqué ou illisible est refusé en
  bloc et consigné en échec ; il n'entre jamais à moitié.
- **Aucune suppression propagée d'office.** Une suppression faite ailleurs
  n'efface rien ici sans ton arbitrage, parce qu'effacer une boucle emporterait
  aussi ses mouvements et ses journaux de clôture.

## Voir aussi

- [`../explanation/invariants.md`](../explanation/invariants.md) — I11, I12, I13
  (et I9, I10 pour l'identité et les traces de suppression).
- [`../schema.md`](../schema.md) — format des fichiers d'état (v3) et protocole.
- [`restaurer_un_backup.md`](restaurer_un_backup.md) — revenir en arrière si une
  fusion a produit un résultat non voulu.
