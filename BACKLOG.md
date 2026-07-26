# BACKLOG — travaux différés

Recense les chantiers reconnus mais volontairement remis à plus tard : décisions
tranchées dont l'exécution attend une condition, dettes techniques identifiées,
questions produit en suspens. Chaque ligne porte sa **condition de
déclenchement** — tant qu'elle n'est pas remplie, le travail n'est pas ouvert.

Ce fichier ne remplace pas les ADR (`docs/decisions.md`) : un ADR tranche un
choix, le backlog suit ce qu'il reste à faire ensuite.

| Réf | Sujet | Origine | Condition de déclenchement |
|---|---|---|---|
| B-01 | Flux « action par défaut » (`defaut` → journal `DEFAUT` → `defaut_applique`) | ADR-04 | preuve d'usage : des boucles réellement bloquées à échéance |
| B-02 | Découpage de `ListeScreen.kt` (~1086 l.) | dette technique | lot dédié |
| B-03 | Synchronisation des captures entre appareils | AND-06 / AND-08 | mise en service d'un second appareil |
| B-04 | Déclencheur `pull_request` sur la CI | AND-10 | avant la prochaine modification de workflow |
| B-05 | « Amender vaut acceptation » | point signalé | question produit à arbitrer |
