---
title: Index de la documentation
type: reference
status: current
updated: 2026-07-25
source: docs/**, README.md, CHANGELOG.md, AGENTS.md
---

# Index de la documentation

Documentation organisée selon [Diátaxis](https://diataxis.fr/) : un document =
un seul type, jamais de mélange.

| Type | Question à laquelle il répond |
|---|---|
| **reference** | « quelle est la valeur exacte ? » — à consulter |
| **explanation** | « pourquoi c'est ainsi ? » — à comprendre |
| **how-to** | « comment je fais X ? » — à suivre pour agir |
| **tutorial** | « apprends-moi » — *aucun document de ce type à ce jour* |

---

## 🔒 Le noyau — à jour et prouvé sur le code

Ces trois documents sont la référence : toute contradiction avec le code est un
bug à signaler, pas une nuance à interpréter.

| Document | Type | Contenu |
|---|---|---|
| [`schema.md`](schema.md) | reference | **Contrat JSON** d'échange : champs, statuts, provenance, dates, modes d'import, convention d'identifiants. Auto-suffisant — donnable tel quel à une IA. |
| [`reference/data_model.md`](reference/data_model.md) | reference | **Modèle interne Room v5** : 4 entités champ par champ, 4 enums, code appareil et préfixe des identifiants, clés étrangères, migrations, requêtes DAO structurantes. |
| [`explanation/invariants.md`](explanation/invariants.md) | explanation | **Les 10 invariants structurels** : énoncé, raison d'être, point d'application, couverture de test réelle, ce qui les casserait. **À lire avant toute modification.** |

---

## Tous les documents

### Reference

| Document | Contenu |
|---|---|
| [`schema.md`](schema.md) | Contrat JSON import/export (format canonique `version: 2`, tolérance de l'ancien format). |
| [`reference/data_model.md`](reference/data_model.md) | Tables, colonnes, enums, migrations 1→2→3→4, écarts assumés entre modèle interne et contrat JSON. |
| [`00_INDEX.md`](00_INDEX.md) | Ce document. |

### Explanation

| Document | Contenu |
|---|---|
| [`explanation/invariants.md`](explanation/invariants.md) | Les 10 règles que l'app garantit par construction, et comment savoir si l'une est cassée. |
| [`explanation/architecture.md`](explanation/architecture.md) | Couches UI → ViewModel → Repository → Room, widget Glance, pourquoi la logique métier est en fonctions pures, rôle de chaque écran, **dette technique assumée**. |
| [`decisions.md`](decisions.md) | Journal des décisions d'architecture (ADR). Voir ci-dessous. |

### How-to

| Document | Contenu |
|---|---|
| [`how-to/importer_des_donnees.md`](how-to/importer_des_donnees.md) | Les 3 modes (Ajouter / Fusionner / Écraser) : lequel choisir, ce que chacun garantit et détruit, où sont les backups. |
| [`how-to/restaurer_un_backup.md`](how-to/restaurer_un_backup.md) | Restauration complète, jusque sur un appareil neuf. **Le how-to le plus important du dépôt.** |
| [`how-to/superviser_les_propositions_ia.md`](how-to/superviser_les_propositions_ia.md) | Produire un JSON conforme, l'importer, arbitrer accepter / amender / rejeter, et la trace laissée par chaque action. |
| [`how-to/build_et_release.md`](how-to/build_et_release.md) | Du `git push` à l'APK installé ; que faire quand une étape de CI échoue. |

### À la racine du dépôt

| Document | Type | Contenu |
|---|---|---|
| [`../README.md`](../README.md) | reference | Présentation du projet, garantie hors-ligne, installation. |
| [`../CHANGELOG.md`](../CHANGELOG.md) | reference | Historique par lot (Keep a Changelog / SemVer). |
| [`../AGENTS.md`](../AGENTS.md) | how-to | **Contrat de travail** : protocole d'investigation, interdits permanents, format des lots, gate standard. À lire avant de toucher au dépôt. |

---

## ⚠️ Décisions ouvertes qui bloquent des chantiers

Consignées dans [`decisions.md`](decisions.md), **non tranchées** : elles
relèvent du commanditaire. Aucun lot ne doit les arbitrer seul.

| ADR | Question ouverte | Ce qu'elle bloque |
|---|---|---|
| **ADR-01** | `milieu` : enum figé à 4 valeurs ou liste configurable comme Type/Tiers ? | Toute évolution des catégories de milieu ; incohérence assumée avec Type/Tiers. |
| **ADR-02** | Stratégie d'identifiants en contexte multi-producteurs (`B-###` / `IA-###` / UUID ?). | L'arrivée d'une seconde source d'écriture ; aujourd'hui la convention de préfixe suffit. |
| **ADR-03** | Chiffrement de la base (SQLCipher) : à partir de quel seuil ? | La protection au repos des données sensibles, et le chiffrement des backups exportés. |
| **ADR-04** | `blocage` / `defaut` / statut `defaut_applique` : implémenter le flux « appliquer l'action par défaut » ou les retirer du modèle ? | Trois champs et un statut existent sans aucun flux applicatif : `defaut_applique` n'est atteignable que par import. |

---

## Points signalés, en attente d'arbitrage

Relevés en écrivant cette documentation, vérifiés dans le code, **non tranchés** :

1. **Type de journal non validé à l'import.** `JournalType.depuis()` n'est
   appelé nulle part (code mort) ; l'import stocke le type brut sans le valider,
   contrairement au statut qui est rejeté s'il est inconnu.
   Cf. [`reference/data_model.md`](reference/data_model.md) §5.4.
2. **Portée de la garde CI anti-réseau.** Elle ne bloque que la permission
   `INTERNET`. D'autres permissions (`ACCESS_NETWORK_STATE`, `WAKE_LOCK`…)
   arrivent transitivement via Glance/WorkManager sans permettre de sortie
   réseau. Faut-il passer à une liste blanche stricte ?
   Cf. [`explanation/invariants.md`](explanation/invariants.md) I5.
3. **« Amender » vaut acceptation.** Enregistrer le formulaire depuis la
   supervision accepte la proposition ; il n'existe pas de « modifier sans
   accepter ». Cf.
   [`how-to/superviser_les_propositions_ia.md`](how-to/superviser_les_propositions_ia.md) §3.

---

## Convention

Chaque document porte un frontmatter :

```yaml
---
title: …
type: reference | how-to | explanation | tutorial
status: current
updated: AAAA-MM-JJ
source: <fichiers de code lus pour l'écrire>
---
```

Le champ **`source`** dit d'où vient la preuve : il permet de re-vérifier un
document contre le code, et de savoir quoi relire quand ce code change.
Cf. [`../AGENTS.md`](../AGENTS.md) §6.
