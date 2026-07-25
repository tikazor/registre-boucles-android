---
title: Capturer une note depuis une autre application
type: how-to
status: current
updated: 2026-07-25
source: app/src/main/java/com/pontat/registreboucles/capture/CaptureActivity.kt, data/{Capture,StatutCapture,EmpreinteCapture,IdentifiantCapture,PreparationCapture}.kt, ui/screens/ReceptionScreen.kt, res/xml/shortcuts.xml, AndroidManifest.xml
---

# Capturer une note depuis une autre application

## En bref

Deux gestes, deux taps, et la note est dans le registre — sans quitter ce que tu
faisais.

```
Xiaomi Notes / Keep / navigateur / n'importe quelle app
   │
   ├── Partager ──────────────► Registre ──► feuille basse ──► Enregistrer
   └── sélection de texte ────► Registre ──► feuille basse ──► Enregistrer
                                                                 │
                                                    Boîte de réception (BRUTE)
```

La note **n'est pas une boucle**. Elle atterrit dans la Boîte de réception, en
amont du registre. Rien n'en sort vers les boucles sans que tu le décides.

Et rien n'est analysé : l'application ne lit pas ta note pour en deviner une
échéance ou un mot-clé. Elle la stocke telle quelle. L'analyse, quand tu la veux,
se fait **hors de l'application** (voir §5).

---

## 1. Partager depuis Xiaomi Notes

1. Ouvre la note.
2. Menu de la note → **Partager** → **Texte** (et non « Image » : l'application
   n'accepte que du texte, voir §6).
3. Dans la feuille de partage Android, choisis **Registre**.
4. La feuille basse de Mnemosyne s'affiche avec l'aperçu → **Enregistrer**.

Tu reviens immédiatement dans Xiaomi Notes. L'application complète ne s'ouvre
pas, et la capture ne laisse pas de fenêtre dans les récents.

Si la note a un titre, il est repris tel quel (`EXTRA_SUBJECT`) : tu le retrouves
au-dessus de l'aperçu, et il servira de titre proposé si tu crées une boucle.

## 2. Capturer une sélection de texte

Dans n'importe quelle application où l'on peut sélectionner du texte (navigateur,
messagerie, PDF, page web) :

1. Sélectionne le passage.
2. Dans la barre d'actions qui apparaît, ouvre le menu **⋮** si nécessaire.
3. Choisis **Registre**.
4. **Enregistrer**.

C'est le même chemin technique (`ACTION_PROCESS_TEXT`), le même résultat.

## 3. Ce qui apparaît, et où

| Ce que tu vois | Où |
|---|---|
| Un badge chiffré dans la barre de la liste principale | Nombre de captures **BRUTE**. Masqué quand il n'y en a aucune. |
| L'entrée « Boîte de réception » dans le menu ⋮ | Toujours présente, même quand la boîte est vide. |
| La note, avec sa date, son appareil et son app source | Boîte de réception, du plus récent au plus ancien. |
| « Déjà capturé le … depuis … » | Message après avoir partagé **deux fois la même note** — voir §4. |

Dans la Boîte de réception, chaque capture propose :

- **Consulter** — le texte intégral, **non modifiable** : c'est la matière
  première, elle n'est jamais réécrite ;
- **Ignorer** (BRUTE → IGNOREE) ou **Réactiver** (IGNOREE ou EXPORTEE → BRUTE) ;
- **Créer une boucle** — ouvre le formulaire de création habituel, pré-rempli
  (titre = titre de la note ou sa première ligne ; origine = « capture
  <app source> »). À la validation, la capture passe en **TRAITEE** et garde
  l'identifiant de la boucle produite.

**Il n'y a aucune suppression.** C'est volontaire : une capture s'ignore, elle ne
s'efface pas. Ce que tu as noté un jour reste consultable, même écarté.

## 4. Doublons

Si tu partages deux fois la même note — depuis deux applications, ou après un
copier-coller reformaté — la seconde n'entre pas. L'application te dit
« Déjà capturé le 25/07/2026 à 14:22 depuis com.miui.notes ».

Sont considérés comme identiques : les mêmes mots aux espaces, tabulations,
retours à la ligne et forme Unicode près. Sont considérés comme différents : une
casse différente, une ponctuation différente. C'est délibéré — décider que
« Appeler Marie » et « appeler marie » sont la même note serait interpréter le
contenu.

## 5. Faire analyser un lot

Boîte de réception → **Exporter un lot d'analyse**. Le fichier
`lot-analyse-<aaaaMMjj-HHmm>.json` contient les captures **BRUTE** avec leur
texte, leur date et leur provenance — rien d'autre. Les captures exportées passent
en **EXPORTEE** ; si le lot n'a finalement pas servi, **Réactiver** les ramène en
BRUTE.

Ce fichier se donne à une IA, hors de l'application. Ce qu'elle en produit revient
par le chemin déjà en place : un JSON de propositions importé à la main, qui
atterrit en statut `proposee` dans l'écran Supervision, où **tu** acceptes,
amendes ou rejettes. Cf.
[`superviser_les_propositions_ia.md`](superviser_les_propositions_ia.md).

Le lot n'est **pas** réimportable dans l'application : rien ne rentre par cette
porte.

## 6. Si « Registre » n'apparaît pas dans le menu de partage

Dans l'ordre, du plus fréquent au plus rare :

1. **Tu partages une image, pas du texte.** L'application ne déclare que
   `text/plain` — aucune image, aucun OCR. Dans Xiaomi Notes, choisis explicitement
   « Texte » au moment du partage.
2. **L'application vient d'être installée ou mise à jour.** Le système met parfois
   quelques minutes à réindexer les cibles de partage. Redémarrer le téléphone
   règle le cas.
3. **La cible est là, mais tout en bas de la grille.** Le classement de la feuille
   de partage dépend de l'usage : Android remonte les cibles fréquentes. Les
   premières fois, cherche l'icône dans la liste complète ; elle remontera. Un
   raccourci **Capturer** est aussi disponible par appui long sur l'icône de
   l'application, et sert de chemin de secours.
4. **L'application source utilise son propre sélecteur de partage.** Certaines
   applications (réseaux sociaux, quelques lecteurs de PDF) affichent une liste
   maison au lieu de la feuille Android, et n'y font apparaître que quelques
   destinations. Passe par un copier-coller et le raccourci Capturer.
5. **Le texte est vide.** Une note sans contenu produit « Rien à capturer depuis
   cette application » et la feuille se referme : c'est le comportement attendu,
   pas une panne.

## Ce que la capture ne fait pas

- **Aucune analyse du contenu** : pas de mots-clés, pas d'échéance détectée, pas
  de catégorie devinée, aucune boucle créée automatiquement (invariant I15).
- **Aucun réseau** : l'application n'a pas la permission `INTERNET` (invariant I5).
- **Aucune image, aucun OCR.**
- **Aucune suppression de capture**, jamais (invariant I14).

## Voir aussi

- [`../explanation/invariants.md`](../explanation/invariants.md) — I14 et I15.
- [`../reference/data_model.md`](../reference/data_model.md) — table `captures`.
- [`../schema.md`](../schema.md) — format du lot d'analyse.
