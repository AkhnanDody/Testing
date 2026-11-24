package com.golda.veniceseedream.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.golda.veniceseedream.ui.theme.AccentYellow

/**
 * Isi drawer hamburger: Pengaturan API key Venice.
 * Key disimpan lokal di HP lewat DataStore (lihat SettingsStore).
 */
@Composable
fun SettingsDrawerContent(
    savedApiKey: String,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var fieldValue by remember { mutableStateOf(savedApiKey) }
    var visible by remember { mutableStateOf(false) }

    // Sinkronkan kolom saat key tersimpan berubah / drawer dibuka lagi
    LaunchedEffect(savedApiKey) { fieldValue = savedApiKey }

    ModalDrawerSheet {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Pengaturan", style = MaterialTheme.typography.titleLarge)
            Text(
                "API Key Venice",
                style = MaterialTheme.typography.titleMedium,
                color = AccentYellow
            )
            Text(
                "Dapatin key-nya di venice.ai/settings/api. Key cuma disimpan di HP ini, nggak dikirim ke mana-mana selain ke Venice pas generate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = fieldValue,
                onValueChange = { fieldValue = it },
                label = { Text("API Key") },
                placeholder = { Text("Paste API key Venice di sini") },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            imageVector = if (visible) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = if (visible) "Sembunyikan" else "Tampilkan"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    onSave(fieldValue)
                    Toast.makeText(context, "API key disimpan", Toast.LENGTH_SHORT).show()
                    onSaved()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentYellow,
                    contentColor = Color.Black
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Simpan")
            }

            OutlinedButton(
                onClick = {
                    onClear()
                    fieldValue = ""
                    Toast.makeText(context, "API key dihapus", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Hapus Key", color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.size(4.dp))
            androidx.compose.material3.TextButton(
                onClick = { uriHandler.openUri("https://venice.ai/settings/api") }
            ) {
                Text("Buka venice.ai/settings/api")
            }
        }
    }
}
