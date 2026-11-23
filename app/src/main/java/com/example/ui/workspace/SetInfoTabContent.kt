package com.example.ui.workspace

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.mse.MseSetInfo

private val BORDER_COLOR_PRESETS = listOf(
    "Black" to "rgb(0,0,0)",
    "White" to "rgb(255,255,255)",
    "Silver" to "rgb(128,128,128)",
    "Gold" to "rgb(200,180,0)",
    "Borderless" to "rgb(0,0,0,0)",
)

/**
 * Classic MSE `Set Info` Tab.
 * Reads and writes Title, Description, Artist, Copyright, Set Code, Language, and Border Color
 * losslessly directly into the `.mse-set` `set info:` block.
 */
@Composable
fun SetInfoTabContent(
    setInfo: MseSetInfo,
    onUpdateSetInfo: (MseSetInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    var title by remember(setInfo.title) { mutableStateOf(setInfo.title) }
    var description by remember(setInfo.description) { mutableStateOf(setInfo.description) }
    var artist by remember(setInfo.artist) { mutableStateOf(setInfo.artist) }
    var copyright by remember(setInfo.copyright) { mutableStateOf(setInfo.copyright) }
    var setCode by remember(setInfo.setCode) { mutableStateOf(setInfo.setCode) }
    var language by remember(setInfo.language) { mutableStateOf(setInfo.language) }
    var borderColor by remember(setInfo.borderColor) { mutableStateOf(setInfo.borderColor) }

    fun pushUpdate(
        t: String = title,
        d: String = description,
        a: String = artist,
        c: String = copyright,
        sc: String = setCode,
        lang: String = language,
        bc: String = borderColor,
    ) {
        onUpdateSetInfo(
            MseSetInfo(
                title = t,
                description = d,
                artist = a,
                copyright = c,
                setCode = sc,
                language = lang,
                borderColor = bc,
            )
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    pushUpdate(t = it)
                },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("set_info_title"),
            )
        }

        item {
            OutlinedTextField(
                value = description,
                onValueChange = {
                    description = it
                    pushUpdate(d = it)
                },
                label = { Text("Description") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("set_info_description"),
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = setCode,
                    onValueChange = {
                        setCode = it.uppercase().take(8)
                        pushUpdate(sc = setCode)
                    },
                    label = { Text("Set Code") },
                    placeholder = { Text("SET") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("set_info_code"),
                )
                OutlinedTextField(
                    value = language,
                    onValueChange = {
                        language = it.uppercase().take(6)
                        pushUpdate(lang = language)
                    },
                    label = { Text("Language") },
                    placeholder = { Text("EN") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("set_info_language"),
                )
            }
        }

        item {
            OutlinedTextField(
                value = artist,
                onValueChange = {
                    artist = it
                    pushUpdate(a = it)
                },
                label = { Text("Default Artist") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("set_info_artist"),
            )
        }

        item {
            OutlinedTextField(
                value = copyright,
                onValueChange = {
                    copyright = it
                    pushUpdate(c = it)
                },
                label = { Text("Copyright") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("set_info_copyright"),
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Border Color",
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BORDER_COLOR_PRESETS.forEach { (label, rgb) ->
                        FilterChip(
                            selected = borderColor.equals(rgb, ignoreCase = true),
                            onClick = {
                                borderColor = rgb
                                pushUpdate(bc = rgb)
                            },
                            label = { Text(label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = borderColor,
                    onValueChange = {
                        borderColor = it
                        pushUpdate(bc = it)
                    },
                    label = { Text("Custom Border Color Value") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
