---
title: Restaurer un backup (jusque sur un appareil neuf)
type: how-to
status: current
updated: 2026-07-25
source: app/src/main/java/com/pontat/registreboucles/data/BoucleRepository.kt, importer/{BackupExporter,JsonExporter,JsonImporter}.kt, ui/screens/{ConfigScreen,ImportScreen,ListeScreen}.kt, app/src/main/res/xml/{backup_rules,data_extraction_rules}.xml
---

# Restaurer un backup

C'est la promesse « quoi qu'il arrive » de l'application. Ce document décrit le
chemin complet, y compris depuis un téléphone neuf.

---

## 0. À faire AVANT d'en avoir besoin (le point critique)

Les backups automatiques vivent dans le stockage privé de l'app
(`Android/data/com.pontat.registreboucles/files/backups/`). Ils sont créés à
chaque import, chaque clôture et chaque action de supervision — mais ils
**disparaissent avec l'application** si tu la désinstalles, et l'accès à ce
dossier depuis un gestionnaire de fichiers est restreint sur Android récent.

**Sors donc un backup régulièrement, et garde-le ailleurs :**

> **Réglages (⋮ → Configuration) → « Exporter le dernier backup »**
> puis choisir une destination durable (Drive, Documents, carte SD…).
> Nom proposé : `boucles-backup-AAAAMMJJ-HHmm.json`.

S'il n'existe encore aucun backup, ce bouton en crée un à la volée.
Le bouton **« Sauvegarder maintenant »**, lui, écrit seulement dans le stockage
interne de l'app — utile, mais il ne protège pas d'une désinstallation.

---

## 1. Récupérer le fichier de backup

Par ordre de fiabilité :

1. **Un export que tu as fait** (méthode ci-dessus) : le fichier est là où tu
   l'as rangé. C'est le cas nominal.
2. **La sauvegarde système Android** : la base **et** le dossier `backups/` sont
   déclarés dans `backup_rules.xml` / `data_extraction_rules.xml`, donc inclus
   dans la sauvegarde cloud et le transfert d'appareil. Si tu restaures le
   téléphone avec l'app installée, les données peuvent revenir seules — sans
   aucune manipulation. *À confirmer sur ton appareil : le déclenchement effectif
   dépend du constructeur et des réglages de sauvegarde Google.*
3. **Copie manuelle depuis l'ancien appareil**, s'il fonctionne encore :
   `Android/data/com.pontat.registreboucles/files/backups/`, prendre le fichier
   `boucles-backup-<horodatage>.json` **au plus grand horodatage** (c'est le
   plus récent). *L'accès à ce dossier via un gestionnaire de fichiers ou un
   câble USB dépend de la version d'Android — à confirmer sur ton appareil.*

Un backup est un fichier JSON au **format canonique** de l'app : il contient les
boucles, leurs mouvements **et les journaux** (les preuves de clôture). Il est
directement réimportable.

---

## 2. Installer l'application sur l'appareil neuf

1. Ouvrir la page **Releases** du dépôt, télécharger `app-release.apk`.
2. Autoriser l'installation depuis cette source si Android le demande.
3. Installer, **ne pas encore ouvrir** — ou l'ouvrir, peu importe : tant que
   rien n'est importé, la base est vide.

---

## 3. Restaurer

### Cas A — appareil neuf, base vide (le cas simple)

1. Ouvrir l'application : elle affiche l'écran d'accueil
   « Aucune donnée pour l'instant ».
2. **« Choisir un fichier JSON »** → sélectionner ton fichier de backup.
3. L'import s'exécute **directement**, sans poser de question : il n'y a rien à
   préserver.

### Cas B — l'application contient déjà des données

1. **⋮ → Importer un JSON** → sélectionner le fichier de backup.
2. L'app propose trois modes. Choisir **« Écraser »**.
3. Confirmer.

> **Pourquoi Écraser ?** Restaurer signifie « revenir exactement à l'état du
> backup ». « Ajouter » ignorerait les boucles dont l'id existe déjà (donc ne
> corrigerait rien) et « Fusionner » mélangerait l'état actuel avec le backup.
> Écraser est destructeur **par intention** — c'est ce qu'on veut ici.
>
> Rassurant : avant d'écraser, l'app crée **automatiquement un backup de l'état
> courant**. Si tu te trompes de fichier, l'état d'avant est encore récupérable
> dans le dossier `backups/`. Et si ce backup de sécurité échoue, l'import est
> **annulé** plutôt qu'exécuté sans filet.

---

## 4. Vérifier la restauration

- Le compteur **« Toutes »** correspond au nombre de boucles attendu.
- Une boucle clôturée : déplier sa carte → **« Journal des clôtures »** doit
  afficher la preuve d'origine. Si tu vois « Clôture importée (sans preuve
  d'origine) », c'est que le fichier importé ne contenait pas ce journal — signe
  que tu as restauré un **export** ancien plutôt qu'un backup complet.
- Le widget se met à jour tout seul après l'import.

---

## 5. Ce qu'un backup ne contient PAS

Vérifié dans le code : un backup sérialise les **boucles, mouvements et
journaux** — rien d'autre. Ne sont donc **pas** restaurés :

- le choix **mode clair / sombre** ;
- les **valeurs configurées** des listes Type et Tiers (écran Réglages).

Ces préférences sont stockées séparément (`SharedPreferences`). Elles font
partie de la sauvegarde système Android (domaine par défaut de l'app), mais pas
du fichier JSON. Après une restauration manuelle, il faut éventuellement les
ressaisir dans Réglages. Aucune boucle, aucun mouvement, aucune preuve n'est
concerné.

---

## 6. En cas de problème

| Symptôme | Cause | Solution |
|---|---|---|
| « Format JSON invalide… » | fichier tronqué ou pas un backup | Reprendre un autre fichier du dossier `backups/` (horodatage inférieur). |
| « Statut inconnu pour la boucle … » | fichier produit hors contrat | Cf. [`../schema.md`](../schema.md) §4 pour les valeurs autorisées. |
| « Date invalide pour la boucle … » | date non ISO-8601 | Le message nomme la boucle et le champ. |
| « Sauvegarde impossible : … » | stockage plein ou indisponible | **L'import a été annulé, rien n'a été touché.** Libérer de l'espace et recommencer. |
| Les preuves de clôture sont remplacées par un texte générique | le fichier importé n'avait pas de journaux | Utiliser un backup (qui contient `journaux`), pas un export ancien. |

---

## 7. Ce qui n'a pas été vérifié sur appareil réel

Ce document est écrit d'après le code. **Le parcours complet sur un téléphone
neuf n'a pas été exécuté** : accessibilité réelle du dossier `Android/data/…`
selon la version d'Android, comportement effectif de la sauvegarde système
Google, et rendu des écrans. À valider sur appareil, puis à corriger ici si un
écart apparaît.
