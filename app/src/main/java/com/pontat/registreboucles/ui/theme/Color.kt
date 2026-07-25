package com.pontat.registreboucles.ui.theme

import androidx.compose.ui.graphics.Color

// ══ Socle de la marque Mnemosyne (palette du jeu d'icônes) ══
val Encre = Color(0xFF0B1620)          // fond de la marque, barre en clair
val Pierre = Color(0xFFE4E7E2)         // marque sur sombre, texte sur barre
val PatineFoncee = Color(0xFF3E7A6C)   // accent sur fond clair
val PatineClaire = Color(0xFF79B4A6)   // accent sur fond sombre
val Bronze = Color(0xFFC99A5B)         // accent secondaire, alertes douces
val Filet = Color(0xFF24394A)          // bordures sur sombre

// ══ Piste 1a « Encre & Patine » — thème clair ══
val BarreClair = Encre
val LogoBarreClair = Pierre
val FondClair = Color(0xFFE9ECE8)
val SurfaceClair = Color(0xFFFFFFFF)
val Surface2Clair = Color(0xFFF3F5F1)
val LigneClair = Color(0xFFD7DDD6)
val SeparateurClair = Color(0xFFE4E8E2)
val TexteClair = Encre
val TexteDouxClair = Color(0xFF6B7570)
val AccentClair = PatineFoncee
val RetardClair = Color(0xFFB3544A)
val BientotClair = Color(0xFFA2762F)
val FabClair = Encre
val SurFabClair = Pierre

// ══ Piste 1a « Encre & Patine » — thème sombre ══
val BarreSombre = Color(0xFF0F1D2A)
val LogoBarreSombre = PatineClaire
val FondSombre = Encre
val SurfaceSombre = Color(0xFF13212E)
val Surface2Sombre = Color(0xFF182838)
val LigneSombre = Filet
val SeparateurSombre = Filet
val TexteSombre = Pierre
val TexteDouxSombre = Color(0xFF8FA0AD)
val AccentSombre = PatineClaire
val RetardSombre = Color(0xFFD08379)
val BientotSombre = Bronze
val FabSombre = PatineClaire
val SurFabSombre = Encre

// ══ Couleurs de badge de statut (lisibles avec un texte Pierre dans les deux thèmes) ══
val StatutOuverte = Encre
val StatutEnCours = PatineFoncee
val StatutFermee = Color(0xFF7E8A96)
val StatutDefaut = Bronze
val StatutProposee = Color(0xFF4A6B8A)   // en attente de supervision
val StatutRejetee = Color(0xFF8C4A42)    // proposition refusée

// Fond crème des cartes du widget (charte 02, conservé pour le widget clair).
val FillCream = Color(0xFFF3ECE3)
