package com.pontat.registreboucles.data

/** Nom de fichier d'un backup à partir d'un horodatage epoch millis. */
fun nomBackup(horodatageMillis: Long): String = "boucles-backup-$horodatageMillis.json"

/** Âge (millis) d'un backup d'après l'horodatage encodé dans son nom, ou null si illisible. */
fun ageBackupDepuisNom(nom: String, maintenant: Long): Long? {
    val ts = nom.removePrefix("boucles-backup-").removeSuffix(".json").toLongOrNull() ?: return null
    return maintenant - ts
}

/**
 * Décision d'anti-rafale (PURE) : faut-il RÉUTILISER le dernier backup au lieu
 * d'en écrire un nouveau ? Vrai seulement si non forcé, qu'un backup récent
 * existe, et qu'il date de moins de [seuilMs]. Un nom illisible = on n'ose pas
 * réutiliser (on réécrit).
 */
fun doitReutiliserBackup(
    dernierNom: String?,
    maintenant: Long,
    forcer: Boolean,
    seuilMs: Long
): Boolean {
    if (forcer || dernierNom == null) return false
    val age = ageBackupDepuisNom(dernierNom, maintenant) ?: return false
    return age in 0 until seuilMs
}
