---
title: Publier une version et l'installer
type: how-to
status: current
updated: 2026-07-25
source: .github/workflows/build.yml, app/build.gradle.kts
---

# Publier une version et l'installer

## En bref

Pousser sur `main` suffit. La CI teste, vérifie, construit un APK signé et
publie une Release. Il n'y a rien à lancer à la main.

```
git push origin main
   └─► GitHub Actions
         1. ./gradlew test              (bloquant)
         2. ./gradlew lintRelease        (non bloquant, rapport en artifact)
         3. ./gradlew assembleRelease    (signé)
         4. garde anti-réseau            (bloquant)
         5. Release GitHub + APK
```

## Avant de pousser

```bash
./gradlew test              # doit être vert
./gradlew assembleRelease   # doit compiler
```

Ces deux commandes sont le *gate* standard du projet. Si l'une échoue, la CI
échouera aussi.

## Numéro de version

Il n'est **pas** dans le code : la CI l'injecte depuis le numéro de run.

| | Valeur | Repli en build local |
|---|---|---|
| `versionCode` | `${{ github.run_number }}` | `1` |
| `versionName` | `1.1.${{ github.run_number }}` | `"1.0"` |

Pour reproduire un build de CI localement :

```bash
./gradlew assembleRelease -PversionCode=20 -PversionName=1.1.20
```

## Installer l'APK

1. Ouvrir la page **Releases** du dépôt (la plus récente est en haut).
2. Télécharger `app-release.apk` sur le téléphone.
3. Autoriser l'installation depuis cette source si Android le demande.
4. Installer par-dessus la version précédente : **les données sont conservées**
   (l'APK est signé avec la même clé et les migrations Room préservent la base).

Pour vérifier la version installée : Paramètres Android → Applications →
Register Mnemosyne. Le `versionName` doit correspondre à `1.1.<numéro de run>`.

## Si la CI échoue

| Étape en échec | Cause probable | Quoi faire |
|---|---|---|
| **Run unit tests** | un invariant est cassé | Lire quel test tombe, puis [`../explanation/invariants.md`](../explanation/invariants.md) § « Comment savoir si un invariant est cassé ». Ne pas désactiver le test. |
| **Lint (release)** | ne peut pas faire échouer le build (`abortOnError = false`) | Télécharger l'artifact `lint-release-report` pour consulter le rapport. |
| **Build signed release APK** | erreur de compilation, ou secret de signature absent/incorrect | Reproduire avec `./gradlew assembleRelease`. Si ça compile en local, vérifier les secrets `KEYSTORE_B64`, `KEYSTORE_PASS`, `KEY_PASS`. |
| **Vérifier l'absence de réseau** | une permission `INTERNET` ou une dépendance réseau est apparue | **Ne pas contourner.** Le message dit lequel des deux. Identifier la dépendance ajoutée et la retirer : c'est l'invariant I5. |
| **Create Release** | droits insuffisants | Le workflow demande `permissions: contents: write`. |

En cas d'échec, **aucune Release n'est publiée** : les étapes bloquantes
précèdent la publication.

## Ce que la CI ne fait pas

Elle ne lance aucun test d'instrumentation (pas d'émulateur), donc ni test
d'interface ni test de migration Room. Voir la dette assumée dans
[`../explanation/architecture.md`](../explanation/architecture.md) §9.
