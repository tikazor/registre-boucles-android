package com.pontat.registreboucles.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pontat.registreboucles.data.Boucle
import com.pontat.registreboucles.data.JournalType
import com.pontat.registreboucles.data.Milieu
import com.pontat.registreboucles.data.estActive
import com.pontat.registreboucles.ui.BoucleViewModel
import com.pontat.registreboucles.ui.FiltreStatut
import com.pontat.registreboucles.ui.couleurStatut
import com.pontat.registreboucles.ui.formaterDate
import com.pontat.registreboucles.ui.formaterDateHeure
import com.pontat.registreboucles.ui.libelleStatut
import com.pontat.registreboucles.ui.theme.Alerte
import com.pontat.registreboucles.ui.theme.Marine
import com.pontat.registreboucles.ui.theme.Teal
import com.pontat.registreboucles.ui.theme.Warn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private fun joursRestants(echeanceMillis: Long): Long {
    val ech = Instant.ofEpochMilli(echeanceMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return ChronoUnit.DAYS.between(LocalDate.now(ZoneId.systemDefault()), ech)
}

private fun correspond(b: Boucle, requete: String): Boolean {
    val q = requete.trim().lowercase()
    if (q.isEmpty()) return true
    return listOfNotNull(
        b.id, b.titre, b.type, b.origine, b.preuveAttendue,
        b.impact, b.blocage, b.defaut, b.tiers, b.statut
    ).any { it.lowercase().contains(q) }
}

private data class Compteurs(
    val ouvertes: Int, val enRetard: Int, val bientot: Int, val total: Int, val fermees: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeScreen(
    vm: BoucleViewModel,
    onOuvrirDebug: () -> Unit,
    onOuvrirConfig: () -> Unit,
    onOuvrirJournal: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    val sheetEditionState = rememberModalBottomSheetState()
    var creationOuverte by remember { mutableStateOf(false) }
    var editionCible by remember { mutableStateOf<Boucle?>(null) }
    val boucles by vm.boucles.collectAsStateWithLifecycle()
    val filtre by vm.filtreStatut.collectAsStateWithLifecycle()
    val recherche by vm.recherche.collectAsStateWithLifecycle()
    val dernieresModifs by vm.dernieresModifs.collectAsStateWithLifecycle()
    val importEnAttente by vm.importEnAttente.collectAsStateWithLifecycle()
    val erreurImport by vm.erreurImport.collectAsStateWithLifecycle()
    val cibleWidget by vm.cibleWidget.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val filtreMilieu by vm.filtreMilieu.collectAsStateWithLifecycle()

    var menuOuvert by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var mouvementCible by remember { mutableStateOf<String?>(null) }
    var clotureCible by remember { mutableStateOf<String?>(null) }

    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val contenu = try {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            } catch (e: Exception) {
                null
            }
            vm.preparerImport(contenu ?: "")
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) vm.exporter(uri) { ok -> toast(if (ok) "Export terminé" else "Échec de l'export") }
    }

    val compteurs = remember(boucles) {
        val ouv = boucles.filter { it.estActive() }
        Compteurs(
            ouvertes = ouv.size,
            enRetard = ouv.count { it.echeance != null && joursRestants(it.echeance) < 0 },
            bientot = ouv.count { it.echeance != null && joursRestants(it.echeance) in 0..7 },
            total = boucles.size,
            fermees = boucles.size - ouv.size
        )
    }
    val liste = remember(boucles, filtre, recherche, filtreMilieu) {
        boucles
            .filter {
                when (filtre) {
                    FiltreStatut.TOUTES -> true
                    FiltreStatut.OUVERTES -> it.estActive()
                    FiltreStatut.FERMEES -> !it.estActive()
                }
            }
            .filter { filtreMilieu == null || Milieu.depuis(it.milieu) == filtreMilieu }
            .filter { correspond(it, recherche) }
    }

    // Tap sur une carte du widget : déplie la boucle, ouvre le mouvement si demandé, défile.
    LaunchedEffect(cibleWidget, liste) {
        cibleWidget?.let { c ->
            expandedId = c.boucleId
            if (c.ouvrirMouvement) mouvementCible = c.boucleId
            val idx = liste.indexOfFirst { it.id == c.boucleId }
            if (idx >= 0) listState.animateScrollToItem(idx)
            vm.cibleConsommee()
        }
    }

    Scaffold(
        floatingActionButtonPosition = FabPosition.End,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Register Mnemosyne",
                        modifier = Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                val relache = withTimeoutOrNull(2000L) { waitForUpOrCancellation() }
                                if (relache == null) onOuvrirDebug()
                            }
                        }
                    )
                },
                actions = {
                    IconButton(onClick = { menuOuvert = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menuOuvert, onDismissRequest = { menuOuvert = false }) {
                        DropdownMenuItem(
                            text = { Text("Importer un JSON") },
                            onClick = { menuOuvert = false; importLauncher.launch(arrayOf("application/json")) }
                        )
                        DropdownMenuItem(
                            text = { Text("Exporter (JSON)") },
                            onClick = {
                                menuOuvert = false
                                val jour = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                                exportLauncher.launch("boucles-export-$jour.json")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Configuration") },
                            onClick = { menuOuvert = false; onOuvrirConfig() }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(if (vm.modeSombre.value) "Mode clair" else "Mode sombre") },
                            leadingIcon = { Icon(Icons.Filled.DarkMode, contentDescription = null) },
                            onClick = { menuOuvert = false; vm.basculerModeSombre() }
                        )
                    }
                },
                // En-tête marine fixe dans les deux thèmes.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Marine,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { creationOuverte = true },
                containerColor = Teal,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Créer une boucle")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // 3 tuiles stats.
            Row(
                Modifier.fillMaxWidth().padding(12.dp, 12.dp, 12.dp, 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatTile("Ouvertes", compteurs.ouvertes, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatTile("En retard", compteurs.enRetard, Alerte, Modifier.weight(1f))
                StatTile("≤ 7 jours", compteurs.bientot, Warn, Modifier.weight(1f))
            }

            // Filtres segmentés avec compteurs.
            Row(
                Modifier.fillMaxWidth().padding(12.dp, 0.dp, 12.dp, 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SegmentFiltre("Toutes", compteurs.total, filtre == FiltreStatut.TOUTES, Modifier.weight(1f)) { vm.setFiltreStatut(FiltreStatut.TOUTES) }
                SegmentFiltre("Ouvertes", compteurs.ouvertes, filtre == FiltreStatut.OUVERTES, Modifier.weight(1f)) { vm.setFiltreStatut(FiltreStatut.OUVERTES) }
                SegmentFiltre("Fermées", compteurs.fermees, filtre == FiltreStatut.FERMEES, Modifier.weight(1f)) { vm.setFiltreStatut(FiltreStatut.FERMEES) }
            }

            // Recherche.
            OutlinedTextField(
                value = recherche,
                onValueChange = { vm.setRecherche(it) },
                singleLine = true,
                placeholder = { Text("Rechercher (titre, id, origine…)") },
                trailingIcon = {
                    if (recherche.isNotEmpty()) {
                        IconButton(onClick = { vm.setRecherche("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Effacer")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(12.dp, 0.dp, 12.dp, 10.dp)
            )

            // Filtre par milieu (défilable).
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp, 0.dp, 12.dp, 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PuceMilieu("Tous", filtreMilieu == null) { vm.setFiltreMilieu(null) }
                Milieu.entries.forEach { m ->
                    PuceMilieu(m.libelle, filtreMilieu == m) { vm.setFiltreMilieu(m) }
                }
            }

            if (liste.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (recherche.isBlank()) "Aucune boucle dans ce filtre."
                        else "Aucun résultat pour « ${recherche.trim()} ».",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp, 0.dp, 14.dp, 110.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(liste, key = { it.id }) { b ->
                        CarteBoucle(
                            boucle = b,
                            expanded = expandedId == b.id,
                            derniereModif = dernieresModifs[b.id],
                            vm = vm,
                            onToggle = { expandedId = if (expandedId == b.id) null else b.id },
                            onDemandeMouvement = { mouvementCible = b.id },
                            onModifier = { editionCible = b },
                            onCloturer = { clotureCible = b.id },
                            onOuvrirJournal = { onOuvrirJournal(b.id) }
                        )
                    }
                }
            }
        }
    }

    // Dialog Ajouter un mouvement.
    mouvementCible?.let { cible ->
        DialogMouvement(
            onAnnuler = { mouvementCible = null },
            onValider = { type, contenu ->
                vm.ajouterMouvement(cible, type, contenu)
                mouvementCible = null
            }
        )
    }

    // Dialog de clôture : EXIGE une entrée journal (type + texte) avant de fermer.
    clotureCible?.let { cible ->
        DialogCloture(
            onAnnuler = { clotureCible = null },
            onValider = { type, texte ->
                vm.cloturer(cible, type, texte)
                clotureCible = null
                toast("Boucle $cible clôturée")
            }
        )
    }

    // Création en bottom sheet (déclenchée par le FAB).
    // Fermeture : clic hors sheet (scrim), swipe vers le bas, croix, ou validation.
    if (creationOuverte) {
        ModalBottomSheet(
            onDismissRequest = { creationOuverte = false },
            sheetState = sheetState
        ) {
            CreationForm(
                vm = vm,
                onFerme = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) creationOuverte = false
                    }
                }
            )
        }
    }

    // Édition d'une boucle existante en bottom sheet (icône crayon des cartes).
    editionCible?.let { cible ->
        ModalBottomSheet(
            onDismissRequest = { editionCible = null },
            sheetState = sheetEditionState
        ) {
            // En ouverture partielle, le bouton « Enregistrer » du bas est hors
            // écran : on affiche alors un raccourci dans l'en-tête, retiré dès
            // que la sheet est pleinement dépliée.
            val pleinementOuverte = sheetEditionState.currentValue == SheetValue.Expanded
            CreationForm(
                vm = vm,
                boucleAModifier = cible,
                afficherEnregistrerEnTete = !pleinementOuverte,
                onFerme = {
                    scope.launch { sheetEditionState.hide() }.invokeOnCompletion {
                        if (!sheetEditionState.isVisible) editionCible = null
                    }
                }
            )
        }
    }

    // Choix d'import Ajouter / Écraser (base non vide).
    val enAttente = importEnAttente
    val err = erreurImport
    if (enAttente != null) {
        AlertDialog(
            onDismissRequest = { vm.annulerImport() },
            title = { Text("Importer ${enAttente.boucles.size} boucle(s)") },
            text = {
                Text(
                    "La base contient déjà des données.\n\n" +
                        "• Ajouter : n'insère que les boucles absentes. Tes boucles actuelles, " +
                        "clôtures et mouvements ajoutés dans l'app sont conservés.\n\n" +
                        "• Écraser : vide tout (boucles + mouvements) et réimporte le fichier tel quel."
                )
            },
            confirmButton = { TextButton(onClick = { vm.confirmerAjout() }) { Text("Ajouter") } },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { vm.confirmerEcrasement() }) { Text("Écraser", color = Alerte) }
                    TextButton(onClick = { vm.annulerImport() }) { Text("Annuler") }
                }
            }
        )
    } else if (err != null) {
        AlertDialog(
            onDismissRequest = { vm.effacerErreurImport() },
            title = { Text("Import impossible") },
            text = { Text(err) },
            confirmButton = { TextButton(onClick = { vm.effacerErreurImport() }) { Text("OK") } }
        )
    }
}

@Composable
private fun StatTile(label: String, valeur: Int, couleur: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(valeur.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = couleur)
        Text(
            label, fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun SegmentFiltre(
    label: String, compte: Int, actif: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit
) {
    val fond = if (actif) Marine else MaterialTheme.colorScheme.surface
    val texte = if (actif) Color.White else MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .height(40.dp)
            .background(fond, RoundedCornerShape(11.dp))
            .then(if (actif) Modifier else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(11.dp)))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = texte)
        Spacer(Modifier.width(6.dp))
        Text(compte.toString(), fontSize = 12.sp, color = texte.copy(alpha = 0.65f))
    }
}

@Composable
private fun CarteBoucle(
    boucle: Boucle,
    expanded: Boolean,
    derniereModif: Long?,
    vm: BoucleViewModel,
    onToggle: () -> Unit,
    onDemandeMouvement: () -> Unit,
    onModifier: () -> Unit,
    onCloturer: () -> Unit,
    onOuvrirJournal: () -> Unit
) {
    val ouverte = boucle.estActive()
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
    ) {
        // En-tête (toujours visible, clic = plier/déplier).
        Column(Modifier.clickable(onClick = onToggle).padding(18.dp, 17.dp, 18.dp, 17.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${boucle.id} · ${boucle.type}",
                        fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        boucle.titre,
                        fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                BadgeStatut(boucle.statut)
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Modifié le ${formaterDate(derniereModif ?: boucle.creee)}",
                    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TexteEcheance(boucle, ouverte)
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RondIcone(Icons.Filled.Add, MaterialTheme.colorScheme.primary, "Ajouter un mouvement", onDemandeMouvement)
                    RondIcone(Icons.Filled.Edit, Marine, "Modifier", onModifier)
                    if (ouverte) RondIcone(Icons.Filled.Check, Teal, "Clôturer", onCloturer)
                }
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Replier" else "Déplier",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(rotation)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(300)) + fadeIn(tween(300)),
            exit = shrinkVertically(tween(300)) + fadeOut(tween(200))
        ) {
            ContenuDeplie(boucle, ouverte, vm, onDemandeMouvement, onCloturer, onOuvrirJournal)
        }
    }
}

@Composable
private fun TexteEcheance(boucle: Boucle, ouverte: Boolean) {
    val j = boucle.echeance?.let { joursRestants(it) }
    val couleur = when {
        ouverte && j != null && j < 0 -> Alerte
        ouverte && j != null && j <= 7 -> Warn
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val gras = ouverte && j != null && j <= 7
    Text(
        "Échéance ${formaterDate(boucle.echeance)}",
        fontSize = 12.sp,
        maxLines = 1,
        color = couleur,
        fontWeight = if (gras) FontWeight.SemiBold else FontWeight.Normal
    )
}

@Composable
private fun ContenuDeplie(
    boucle: Boucle,
    ouverte: Boolean,
    vm: BoucleViewModel,
    onDemandeMouvement: () -> Unit,
    onCloturer: () -> Unit,
    onOuvrirJournal: () -> Unit
) {
    val mouvements by vm.observerMouvements(boucle.id).collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        Modifier.padding(18.dp, 0.dp, 18.dp, 17.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ChampInfo("Origine", boucle.origine, Modifier.weight(1f))
            ChampInfo("Tiers", boucle.tiers ?: "—", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ChampInfo("Créée le", formaterDate(boucle.creee), Modifier.weight(1f))
            ChampInfo("Échéance", formaterDate(boucle.echeance), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ChampInfo("Milieu", Milieu.depuis(boucle.milieu)?.libelle ?: "—", Modifier.weight(1f))
            ChampInfo("Statut", libelleStatut(boucle.statut), Modifier.weight(1f))
        }

        Encart("Preuve attendue", boucle.preuveAttendue, Teal, Teal.copy(alpha = 0.08f))
        boucle.blocage?.let { Encart("Blocage", it, Warn, Warn.copy(alpha = 0.09f)) }

        ChampInfo("Impact", boucle.impact)
        boucle.defaut?.let { ChampInfo("Action par défaut", it) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onDemandeMouvement,
                shape = RoundedCornerShape(19.dp),
                modifier = Modifier.weight(1f)
            ) { Text("Ajouter un mouvement", fontSize = 12.5.sp) }
            if (ouverte) {
                Button(
                    onClick = onCloturer,
                    shape = RoundedCornerShape(19.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Marine, contentColor = Color.White),
                    modifier = Modifier.weight(1f)
                ) { Text("Clôturer", fontSize = 12.5.sp) }
            }
        }

        if (mouvements.isNotEmpty()) {
            val dernier = mouvements.first() // trié date DESC par le DAO
            Column {
                Text(
                    "Dernier mouvement",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                        .padding(12.dp, 10.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            dernier.type.replaceFirstChar { it.uppercase() },
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = couleurMouvement(dernier.type)
                        )
                        Text(
                            formaterDateHeure(dernier.date),
                            fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        dernier.contenu,
                        fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (mouvements.size > 1) {
                        Text(
                            "+ ${mouvements.size - 1} mouvement(s) antérieur(s)",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }

        // Accès à l'historique des clôtures (journal) de cette boucle.
        TextButton(onClick = onOuvrirJournal) { Text("Journal des clôtures") }
    }
}

private fun couleurMouvement(type: String): Color = when (type) {
    "preuve" -> Teal
    "defaut" -> Warn
    else -> Marine
}

@Composable
private fun ChampInfo(label: String, valeur: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label.uppercase(),
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            fontWeight = FontWeight.Medium
        )
        Text(
            valeur, fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun Encart(label: String, valeur: String, couleurLabel: Color, fond: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(fond, RoundedCornerShape(10.dp))
            .padding(12.dp, 10.dp)
    ) {
        Text(label.uppercase(), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = couleurLabel)
        Text(
            valeur, fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

@Composable
private fun RondIcone(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    couleur: Color,
    description: String,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(34.dp)
            .border(1.5.dp, couleur, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icone, contentDescription = description, tint = couleur, modifier = Modifier.size(17.dp))
    }
}

@Composable
fun BadgeStatut(statut: String) {
    Surface(color = couleurStatut(statut), shape = RoundedCornerShape(50), contentColor = Color.White) {
        Text(
            libelleStatut(statut),
            fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun DialogMouvement(
    onAnnuler: () -> Unit,
    onValider: (type: String, contenu: String) -> Unit
) {
    val types = listOf("preuve" to "Preuve", "declaration" to "Déclaration", "defaut" to "Défaut")
    var type by remember { mutableStateOf("preuve") }
    var contenu by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text("Ajouter un mouvement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.forEach { (valeur, libelle) ->
                        val actif = type == valeur
                        Box(
                            Modifier
                                .weight(1f)
                                .height(32.dp)
                                .background(if (actif) Teal else Color.Transparent, RoundedCornerShape(16.dp))
                                .then(if (actif) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)))
                                .clickable { type = valeur },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                libelle, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                                color = if (actif) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = contenu,
                    onValueChange = { contenu = it },
                    placeholder = { Text("Contenu") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onValider(type, contenu.trim()) }, enabled = contenu.isNotBlank()) {
                Text("Valider")
            }
        },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Annuler") } }
    )
}

@Composable
private fun PuceMilieu(label: String, actif: Boolean, onClick: () -> Unit) {
    val fond = if (actif) Marine else MaterialTheme.colorScheme.surface
    val texte = if (actif) Color.White else MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .height(34.dp)
            .background(fond, RoundedCornerShape(17.dp))
            .then(if (actif) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(17.dp)))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = texte)
    }
}

/** Clôture : choix du type de journal (Preuve/Déclaration/Défaut) + texte OBLIGATOIRE. */
@Composable
private fun DialogCloture(
    onAnnuler: () -> Unit,
    onValider: (type: JournalType, texte: String) -> Unit
) {
    var type by remember { mutableStateOf(JournalType.PREUVE) }
    var texte by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text("Clôturer — journal requis") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Une clôture exige une entrée de journal.",
                    fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    JournalType.entries.forEach { t ->
                        val actif = type == t
                        Box(
                            Modifier
                                .weight(1f)
                                .height(32.dp)
                                .background(if (actif) Teal else Color.Transparent, RoundedCornerShape(16.dp))
                                .then(if (actif) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)))
                                .clickable { type = t },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                t.libelle, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                                color = if (actif) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = texte,
                    onValueChange = { texte = it },
                    placeholder = { Text("Texte du journal (preuve, déclaration…)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onValider(type, texte.trim()) }, enabled = texte.isNotBlank()) {
                Text("Clôturer")
            }
        },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Annuler") } }
    )
}
