package com.pontat.registreboucles.ui.theme

import androidx.compose.ui.graphics.Color

// ── Système de design « Registre des Boucles v2 » (marine / sarcelle) ──
// Accents fixes (identiques en clair et sombre).
val Marine = Color(0xFF1E3A5F)
val MarineFonce = Color(0xFF152B47)
val Teal = Color(0xFF2E7D74)
val Alerte = Color(0xFFB3433B)   // retard
val Warn = Color(0xFFC07A2B)     // ≤ 7 jours / défaut appliqué
val Neutre = Color(0xFF7E8A96)   // fermé
val Blanc = Color(0xFFFFFFFF)

// Thème clair.
val EncreClair = Color(0xFF1F2933)
val FondClair = Color(0xFFECEFF3)
val SurfaceClair = Color(0xFFFFFFFF)
val Surface2Clair = Color(0xFFF2F5F8)
val LigneClair = Color(0xFFDCE3EB)

// Thème sombre.
val EncreSombre = Color(0xFFE7EDF3)
val FondSombre = Color(0xFF0E1621)
val SurfaceSombre = Color(0xFF17222F)
val Surface2Sombre = Color(0xFF212F3D)
val LigneSombre = Color(0xFF2B3A48)
val BrandSombre = Color(0xFF7FB0E0)

// Fond crème des cartes du widget (charte 02, demandé explicitement).
val FillCream = Color(0xFFF3ECE3)

// Badges de statut (couleurs fixes dans les deux thèmes).
val StatutOuverte = Marine
val StatutEnCours = Teal
val StatutFermee = Neutre
val StatutDefaut = Warn
