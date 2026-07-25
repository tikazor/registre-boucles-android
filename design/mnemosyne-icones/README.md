# Mnemosyne — jeu d'icônes

Marque : **double spirale**. Deux spirales de sens contraire, vues en perspective (ry = 0,4 × rx), qui se coupent à chaque demi-tour. La goutte pénètre la première onde.

Réglages validés : distance −5 · goutte 6,25 · trait 1,2 · viewBox 64×64.
Le groupe a été redescendu de 4,75 unités pour être optiquement centré dans le carré ; les proportions validées sont inchangées.

## Paliers optiques

Un seul dessin ne tient pas de 16 à 512 px. Deux versions, un seuil.

| Taille | Fichier source | Trait | Dessin |
|---|---|---|---|
| ≥ 128 px | `mnemosyne.svg` | 1,2 | Spirale complète, 4 tours |
| 48 – 64 px | `mnemosyne-48.svg` | 2,0 | Spirale complète, trait épaissi |
| ≤ 32 px | `mnemosyne-32.svg` | 5,0 | Version réduite : goutte + un seul croisement |

En dessous de 48 px, les quatre tours de spirale se remplissent et deviennent une tache. La version réduite garde les deux éléments porteurs de sens — la goutte, et le croisement — et abandonne le reste.

## Fichiers

```
mnemosyne.svg          currentColor, hérite de la couleur du texte
mnemosyne-48.svg       idem, trait épaissi
mnemosyne-32.svg       version réduite
mnemosyne-ink.svg      #0B1620 en dur
mnemosyne-stone.svg    #E4E7E2 en dur
favicon.ico            16 / 32 / 48 empilés
png/ink-*.png          marque encre, fond transparent
png/stone-*.png        marque pierre, fond transparent
png/apple-touch-180.png    fond plein, marque à 72 %
png/maskable-512.png       fond plein, marque à 62 % (zone sûre Android)
```

## Palette

| Rôle | Hex |
|---|---|
| Encre (fond sombre, marque sur clair) | `#0B1620` |
| Pierre (marque sur sombre) | `#E4E7E2` |
| Patine (accent, états actifs) | `#79B4A6` |
| Bronze (accent secondaire, alertes douces) | `#C99A5B` |
| Filet (bordures) | `#24394A` |

## Intégration web

```html
<link rel="icon" href="/favicon.ico" sizes="any">
<link rel="icon" href="/mnemosyne-32.svg" type="image/svg+xml">
<link rel="apple-touch-icon" href="/png/apple-touch-180.png">
<link rel="manifest" href="/site.webmanifest">
```

```json
{
  "name": "Mnemosyne",
  "icons": [
    { "src": "/png/stone-192.png", "sizes": "192x192", "type": "image/png" },
    { "src": "/png/stone-512.png", "sizes": "512x512", "type": "image/png" },
    { "src": "/png/maskable-512.png", "sizes": "512x512", "type": "image/png", "purpose": "maskable" }
  ],
  "theme_color": "#0B1620",
  "background_color": "#0B1620"
}
```

## En ligne dans l'interface

`mnemosyne.svg` et `mnemosyne-32.svg` utilisent `currentColor` : place-les en `<img>` non, en SVG inline, et ils prennent la couleur du texte parent. Utile pour les états survol, actif, désactivé sans dupliquer de fichier.

## Zone de protection

Réserve autour de la marque au moins la hauteur de la goutte (≈ 17 unités sur 64, soit 27 %). Ne place aucun texte ni filet à l'intérieur.

## À ne pas faire

- Refermer les spirales — l'ouverture est le sujet.
- Ajouter un contour à la goutte : elle est en aplat, les ondes en filet, le contraste des deux traitements porte la lecture.
- Étirer verticalement pour « remplir » le carré : ry = 0,4 × rx fixe l'angle de vue, le modifier casse la perspective.
