package com.evsct.app.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.ui.theme.EvAccents
import com.evsct.app.util.Derived
import com.evsct.app.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    onAddSession: () -> Unit,
    onEditSession: (Long) -> Unit,
    onOpenTrips: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: SessionListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Charging log", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    IconButton(onClick = onOpenTrips) {
                        Icon(Icons.Default.Map, contentDescription = "Trips")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddSession,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add session")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            SummaryCard(state)
            if (state.sessions.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.sessions, key = { it.id }) { s ->
                        SessionRow(
                            session = s,
                            tripName = s.tripId?.let { state.tripNamesById[it] },
                            onClick = { onEditSession(s.id) },
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: SessionListUi) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Stat("Sessions", state.sessionCount.toString())
            Stat("Total cost", Format.money(state.totalCost))
            Stat("Energy", Format.kwh(state.totalKwh))
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .height(72.dp)
                    .width(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "No sessions yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Tap + to log your first charge.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SessionRow(
    session: ChargingSession,
    tripName: String?,
    onClick: () -> Unit,
) {
    val typeAccent = chargingTypeAccent(session.chargingType)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Leading colored bar by charging type
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(typeAccent.bar),
            )
            Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            session.brand ?: "Unknown",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            session.locationCity ?: "—",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            Format.money(session.totalCost, session.currency),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            Format.dateTime(session.sessionStart),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TypeBadge(session.chargingType)
                    Spacer(Modifier.width(8.dp))
                    Text(Format.kwh(session.energyKwh), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(12.dp))
                    Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(12.dp))
                    Text(Format.duration(session.durationSeconds), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(12.dp))
                    Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        Format.kw(Derived.effectiveAvgPowerKw(session)) + " avg",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val effPrice = Derived.effectiveEnergyPricePerKwh(session)
                if (effPrice != null || tripName != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (effPrice != null) {
                            Text(
                                "Eff. ${Format.rate(effPrice, "/kWh")}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (effPrice != null && tripName != null) {
                            Spacer(Modifier.width(12.dp))
                        }
                        if (tripName != null) {
                            TripPill(tripName)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeBadge(type: ChargingType) {
    val accent = chargingTypeAccent(type)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.container)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            type.shortLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = accent.onContainer,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TripPill(name: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

private data class TypeAccent(val bar: Color, val container: Color, val onContainer: Color)

private fun chargingTypeAccent(type: ChargingType): TypeAccent = when (type) {
    ChargingType.DC_FAST -> TypeAccent(EvAccents.DcFast, EvAccents.DcFastContainer, Color(0xFF3B2400))
    ChargingType.AC_L2 -> TypeAccent(EvAccents.AcL2, EvAccents.AcL2Container, Color(0xFF002B57))
    ChargingType.AC_L1 -> TypeAccent(EvAccents.AcL1, EvAccents.AcL1Container, Color(0xFF200052))
}

private fun ChargingType.shortLabel(): String = when (this) {
    ChargingType.DC_FAST -> "DC FAST"
    ChargingType.AC_L2 -> "AC L2"
    ChargingType.AC_L1 -> "AC L1"
}
