# Décisions d'architecture (ADR)

Registre des points ouverts. Le statut **OUVERT** signale un arbitrage qui
revient au commanditaire : le contexte et les options sont documentés, la
décision n'est pas tranchée ici.

---

## ADR-01 — Milieu : enum figé vs liste configurable

**Contexte.** `Milieu` est un enum fermé à 4 valeurs (PRO, GOUVERNANCE, PROJET,
PERSO), stocké en base sous forme de nom d'enum et toléré à l'import via
`Milieu.depuis()`. À l'inverse, `Type` et `Tiers` sont des listes librement
éditables depuis l'écran Réglages (`ListeOptions`). Il y a donc deux régimes
différents pour des champs de même nature (classification d'une boucle).

**Options.**
- **A — Garder l'enum figé.** Simple, typé, filtrable de façon fiable ; mais
  toute nouvelle catégorie exige une release.
- **B — Passer Milieu en liste configurable** (comme Type/Tiers). Souplesse
  terrain ; mais perte du typage fort, filtres et libellés à re-vérifier, et
  migration des valeurs existantes à prévoir.

**Statut : OUVERT.** Dépend du besoin réel de créer des milieux à la volée.

---

## ADR-02 — Stratégie d'identifiant en contexte multi-source

**Contexte.** Les ids sont au format `B-###`, générés par `genererProchainId()`
(max + 1, les trous ne sont pas réutilisés). Cela suppose une source unique.
Or le lot AC-BOUCLES-01 introduit une synchronisation (l'app pousse son état,
Claude le lit). Si plusieurs sources créent des boucles, deux `B-017` peuvent
entrer en collision.

**Options.**
- **A — Statu quo `B-###`.** Lisible, suffisant tant qu'il n'y a qu'une source
  d'écriture (le téléphone).
- **B — Préfixe par source** (`B-PHONE-017`, `B-WEB-003`). Simple, lisible,
  évite les collisions ; ordre global moins évident.
- **C — UUID / ULID.** Plus de collision possible, mais ids illisibles et
  perte du tri naturel par numéro.

**Statut : OUVERT.** À trancher si/quand une seconde source écrit des boucles.

---

## ADR-03 — Chiffrement de la base (SQLCipher) : seuil de déclenchement

**Contexte.** La base Room et les backups JSON contiennent des données
sensibles (mentions de jeunes suivis). Ils reposent aujourd'hui sur le
sandbox applicatif Android et le chiffrement disque de l'appareil, sans
chiffrement applicatif dédié. Les backups exportés (ADR étape 4) sortent du
sandbox en clair.

**Options.**
- **A — Rester sur le chiffrement de l'OS.** Zéro dépendance, zéro gestion de
  clé ; mais un backup exporté ou un appareil déverrouillé expose les données.
- **B — SQLCipher + chiffrement des backups.** Protection au repos réelle ;
  mais dépendance native, gestion/stockage d'une clé (Keystore Android), et
  question du chiffrement des exports partagés.

**Statut : OUVERT.** Question du seuil : dès maintenant, ou au-delà d'un
certain volume / d'un partage hors appareil ? À définir avec le commanditaire
au regard des obligations (RGPD, secret professionnel).

---

## ADR-04 — Champs `blocage`/`defaut` et statut `defaut_applique`

**Contexte.** Le modèle `Boucle` porte `blocage` (frein courant) et `defaut`
(action par défaut si rien n'est tranché), et l'enum `Statut` inclut
`DEFAUT_APPLIQUE`. Mais AUCUN flux applicatif ne fait passer une boucle à cet
état : `blocage`/`defaut` ne sont aujourd'hui que de l'affichage, et
`defaut_applique` n'est atteignable que par import. On a donc un état terminal
sans chemin pour l'atteindre depuis l'app.

**Options.**
- **A — Implémenter le flux « appliquer l'action par défaut ».** Un bouton qui,
  à échéance dépassée sans preuve, applique `defaut`, écrit une entrée de
  journal (type DEFAUT) et passe la boucle à `defaut_applique`. Donne du sens
  aux trois éléments ; c'est du développement de fonctionnalité (hors AND-02).
- **B — Retirer `blocage`/`defaut`/`defaut_applique` du modèle.** Simplifie ;
  mais migration Room et perte d'une intention métier déjà exprimée.

**Statut : OUVERT.** Décision produit : ces champs décrivent-ils un flux à
construire, ou un vestige à retirer ?
