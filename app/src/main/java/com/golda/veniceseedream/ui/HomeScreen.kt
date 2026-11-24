package com.golda.veniceseedream.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.golda.veniceseedream.data.Downloader
import com.golda.veniceseedream.ui.theme.AccentYellow
import com.golda.veniceseedream.ui.theme.DarkSurface
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    hasApiKey: Boolean,
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!hasApiKey) {
            Card(colors = CardDefaults.cardColors(containerColor = DarkSurface)) {
                Text(
                    text = "API key belum diisi. Buka menu hamburger di kiri atas > Pengaturan, lalu isi API key Venice kamu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AccentYellow,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        OutlinedTextField(
            value = viewModel.prompt,
            onValueChange = { viewModel.prompt = it },
            label = { Text("Prompt") },
            placeholder = { Text("Contoh: a cute cat astronaut, cinematic lighting...") },
            minLines = 4,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "${viewModel.prompt.length} karakter",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledDropdown(
                label = "Resolusi",
                options = Options.RESOLUTIONS,
                selected = viewModel.resolution,
                onSelected = { viewModel.resolution = it },
                modifier = Modifier.weight(1f)
            )
            LabeledDropdown(
                label = "Aspect Ratio",
                options = Options.ASPECT_RATIOS,
                selected = viewModel.aspectRatio,
                onSelected = { viewModel.aspectRatio = it },
                modifier = Modifier.weight(1f)
            )
        }

        Button(
            onClick = { viewModel.generate() },
            enabled = !viewModel.isGenerating && viewModel.prompt.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentYellow,
                contentColor = androidx.compose.ui.graphics.Color.Black
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            if (viewModel.isGenerating) {
                CircularProgressIndicator(
                    color = androidx.compose.ui.graphics.Color.Black,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.size(10.dp))
                Text("Generating... ${viewModel.elapsedSeconds}s")
            } else {
                Text("Generate")
            }
        }

        viewModel.errorMessage?.let { err ->
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        val latest = viewModel.latestItem
        if (latest != null) {
            Text("Hasil terbaru", style = MaterialTheme.typography.titleMedium)
            Card(colors = CardDefaults.cardColors(containerColor = DarkSurface)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LocalPngImage(
                        file = File(viewModel.galleryDir, latest.fileName),
                        contentDescription = latest.prompt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        contentScale = ContentScale.Fit
                    )
                    Text(latest.prompt, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "${latest.resolution} • ${latest.aspectRatio}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val ok = Downloader.saveToDownloads(
                                        context,
                                        File(viewModel.galleryDir, latest.fileName),
                                        latest.fileName
                                    )
                                    Toast.makeText(
                                        context,
                                        if (ok) "Tersimpan di Download/VeniceSeedream"
                                        else "Gagal menyimpan gambar",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Download")
                        }
                        OutlinedButton(
                            onClick = { viewModel.usePrompt(latest.prompt) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Pakai Lagi")
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenGallery,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Lihat di Galeri")
                    }
                }
            }
        }
    }
}
