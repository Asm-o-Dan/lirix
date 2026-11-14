package com.lirix.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import com.lirix.app.ui.theme.LirixThemeTokens
import com.lirix.app.R
import com.lirix.app.ui.LibrarySearchField

internal fun LibrarySearchField.labelRes(): Int = when (this) {
    LibrarySearchField.TITLE -> R.string.library_search_field_title
    LibrarySearchField.ARTIST -> R.string.library_search_field_artist
    LibrarySearchField.TEXT -> R.string.library_search_field_text
    LibrarySearchField.CHORDS -> R.string.library_search_field_chords
}

@Composable
internal fun LibrarySearchFields(selected: Set<LibrarySearchField>, onChange: (Set<LibrarySearchField>) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.widthIn(max = 96.dp).heightIn(min = 48.dp)) {
            val label = when (selected.size) {
                LibrarySearchField.entries.size -> stringResource(R.string.library_search_fields_all)
                1 -> stringResource(selected.first().labelRes())
                else -> stringResource(R.string.library_search_fields_count, selected.size)
            }
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)

        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.library_search_fields_all)) },
                onClick = { onChange(LibrarySearchField.entries.toSet()) })
            LibrarySearchField.entries.forEach { field ->
                DropdownMenuItem(
                    text = {
                        Surface(shape = RoundedCornerShape(12.dp), color = LirixThemeTokens.colors.surfaceCard) {
                            Text(stringResource(field.labelRes()), modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    leadingIcon = { Checkbox(checked = field in selected, onCheckedChange = null) },
                    enabled = selected.size > 1 || field !in selected,
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = { onChange(if (field in selected) selected - field else selected + field) }
                )
            }
        }
    }
}
