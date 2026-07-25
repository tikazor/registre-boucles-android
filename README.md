# Register Mnemosyne

Registre-mémoire **hors ligne** des « boucles » : engagements ouverts à clôturer
avec preuve, sur plusieurs milieux (pro, gouvernance, projet, perso). L'app est
alimentée par l'utilisateur **et** par des propositions produites par une IA
externe — mais toute proposition IA passe par une **file de supervision**
(accepter / amender / rejeter) : rien n'entre dans le registre actif sans une
action explicite.

L'IA reste **hors de l'application** : celle-ci ne fait aucun appel réseau. Les
propositions arrivent sous forme d'un fichier JSON importé à la main.

## Aucun réseau

Pas de permission `INTERNET`, aucun client HTTP, aucun SDK tiers. C'est
**vérifié en CI** à chaque build, sur le manifest mergé (app + dépendances).

## Documentation

**Point d'entrée : [`docs/00_INDEX.md`](docs/00_INDEX.md)** — tous les documents,
classés par type (Diátaxis).

Le noyau :

- [`docs/schema.md`](docs/schema.md) — contrat JSON complet (champs, statuts,
  provenance, modes d'import). Auto-suffisant : donnable tel quel à une IA.
- [`docs/reference/data_model.md`](docs/reference/data_model.md) — modèle Room v4.
- [`docs/explanation/invariants.md`](docs/explanation/invariants.md) — les 8
  règles garanties par construction. À lire avant toute modification.
- [`docs/decisions.md`](docs/decisions.md) — décisions d'architecture (ADR).

Guides pratiques : [importer des données](docs/how-to/importer_des_donnees.md) ·
[restaurer un backup](docs/how-to/restaurer_un_backup.md) ·
[superviser les propositions IA](docs/how-to/superviser_les_propositions_ia.md) ·
[publier et installer](docs/how-to/build_et_release.md).

Contribuer (agent ou humain) : [`AGENTS.md`](AGENTS.md) ·
historique : [`CHANGELOG.md`](CHANGELOG.md).

## Installation

Télécharger le dernier APK signé depuis les
[Releases GitHub](../../releases) et l'installer sur un appareil Android
(minSdk 26). Chaque push sur `main` déclenche tests → lint → build signé.

## Développement

```bash
./gradlew test            # tests unitaires JVM
./gradlew assembleRelease # APK release
```

Kotlin · Jetpack Compose · Room · Glance (widget).
