package com.dev.salt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dev.salt.evaluateJexlScript

/**
 * Data class representing a pending JEXL debug request.
 * Used to communicate between ViewModel and UI for showing debug dialogs.
 */
data class JexlDebugRequest(
    val statement: String,
    val context: Map<String, Any?>,
    val scriptType: String = "JEXL", // e.g., "PreScript", "Validation", "SkipTo", "Eligibility", "LabTest"
    val onContinue: () -> Unit
)

/**
 * Interactive dialog for debugging JEXL expressions.
 * Shows the context, allows editing the statement, and displays live results.
 *
 * @param originalStatement The original JEXL expression to debug
 * @param context The evaluation context (variable names to values)
 * @param scriptType A label describing what type of script this is
 * @param onContinue Callback when user presses Continue
 */
@Composable
fun JexlDebugDialog(
    originalStatement: String,
    context: Map<String, Any?>,
    scriptType: String = "JEXL",
    onContinue: () -> Unit
) {
    var editableStatement by remember { mutableStateOf(originalStatement) }
    var result by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    // Re-evaluate when statement changes - use throwErrors=true to get actual errors
    LaunchedEffect(editableStatement) {
        if (editableStatement.isBlank()) {
            result = "(empty statement)"
            isError = false
            return@LaunchedEffect
        }

        try {
            val evalResult = evaluateJexlScript(editableStatement, context, throwErrors = true)
            result = evalResult?.toString() ?: "null"
            isError = false
        } catch (e: Exception) {
            result = "ERROR: ${e.message ?: e.javaClass.simpleName}"
            isError = true
        }
    }

    AlertDialog(
        onDismissRequest = { /* Prevent dismissing by clicking outside */ },
        title = {
            Text(
                "$scriptType Debug",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 450.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Context Section - scrollable table
                Text(
                    "Context (${context.size} variables):",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                // Table with header and scrollable body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    // Table Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Variable",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(0.4f)
                        )
                        Text(
                            text = "Value",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(0.6f)
                        )
                    }

                    // Scrollable table body
                    val sortedEntries = remember(context) {
                        context.entries.sortedBy { it.key }
                    }

                    if (sortedEntries.isEmpty()) {
                        Text(
                            text = "(empty)",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            items(sortedEntries.toList()) { (key, value) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = key,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(0.4f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formatContextValue(value),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(0.6f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                }

                // Statement Section (Editable)
                Text(
                    "Statement:",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = editableStatement,
                    onValueChange = { editableStatement = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp, max = 100.dp),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    ),
                    singleLine = false
                )

                // Result Section
                Text(
                    "Result:",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isError)
                            MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = result.ifBlank { "(evaluating...)" },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = if (isError)
                            MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                // Note about edits not being saved
                Text(
                    "Note: Edits are for testing only and will not be saved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onContinue) {
                Text("Continue")
            }
        }
    )
}

/**
 * Format a context value for display.
 * Handles null, lists, and other types appropriately.
 */
private fun formatContextValue(value: Any?): String {
    return when (value) {
        null -> "null"
        is List<*> -> "[${value.joinToString(", ")}]"
        is String -> "\"$value\""
        else -> value.toString()
    }
}
