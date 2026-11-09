package app.aaps.plugins.automationstate.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.plugins.automationstate.R
import app.aaps.core.ui.R as CoreUiR

class AutomationStateComposeContent : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val viewModel: AutomationStateViewModel = metroViewModel()
        val uiState by viewModel.uiState.collectAsState()

        var showAddStateDialog by remember { mutableStateOf(false) }
        var editingStateName by remember { mutableStateOf<String?>(null) }

        val title = stringResource(R.string.automation_states)

        LaunchedEffect(Unit) {
            setToolbarConfig(
                ToolbarConfig(
                    title = title,
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreUiR.string.back))
                        }
                    },
                    actions = {}
                )
            )
        }

        Scaffold(
            floatingActionButton = {
                FloatingActionButton(onClick = { showAddStateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_automation_state))
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (uiState.states.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_automation_states),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.states) { (name, currentValue) ->
                            StateCard(
                                name = name,
                                currentValue = currentValue,
                                values = uiState.stateValues[name] ?: emptyList(),
                                onValueSelected = { viewModel.setState(name, it) },
                                onEdit = { editingStateName = name }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(72.dp)) }
                    }
                }
            }
        }

        if (showAddStateDialog) {
            AddStateDialog(
                onConfirm = { name ->
                    showAddStateDialog = false
                    editingStateName = name
                },
                onDismiss = { showAddStateDialog = false }
            )
        }

        editingStateName?.let { name ->
            EditStateValuesDialog(
                stateName = name,
                existingValues = uiState.stateValues[name] ?: emptyList(),
                onSave = { values ->
                    viewModel.setStateValues(name, values)
                    editingStateName = null
                },
                onDelete = {
                    viewModel.deleteState(name)
                    editingStateName = null
                },
                onDismiss = { editingStateName = null }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StateCard(
    name: String,
    currentValue: String,
    values: List<String>,
    onValueSelected: (String) -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = name, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                values.forEach { value ->
                    ElevatedFilterChip(
                        selected = value == currentValue,
                        onClick = { onValueSelected(value) },
                        label = { Text(value) },
                        colors = FilterChipDefaults.elevatedFilterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun AddStateDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_automation_state)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.automation_state_name)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

@Composable
private fun EditStateValuesDialog(
    stateName: String,
    existingValues: List<String>,
    onSave: (List<String>) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val values = remember { mutableStateListOf<String>().also { it.addAll(existingValues) } }
    var newValue by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_state)) },
            text = { Text(stringResource(R.string.delete_state_confirmation)) },
            confirmButton = {
                TextButton(onClick = onDelete) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$stateName") },
        text = {
            Column {
                LazyColumn(modifier = Modifier.height(180.dp)) {
                    items(values.size) { i ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(values[i], modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { if (values.size > 1) values.removeAt(i) },
                                enabled = values.size > 1
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newValue,
                        onValueChange = { newValue = it },
                        label = { Text(stringResource(R.string.add_state_value)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    IconButton(
                        onClick = {
                            val v = newValue.trim()
                            if (v.isNotEmpty() && !values.contains(v)) {
                                values.add(v)
                                newValue = ""
                            }
                        },
                        enabled = newValue.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.delete_state))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (values.isNotEmpty()) onSave(values.toList()) },
                enabled = values.isNotEmpty()
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}
