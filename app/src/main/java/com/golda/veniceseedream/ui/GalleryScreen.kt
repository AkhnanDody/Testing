package com.golda.veniceseedream.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.golda.veniceseedream.data.Downloader
import com.golda.veniceseedream.data.GalleryItem
import com.golda.veniceseedream.ui.theme.DarkSurface
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GalleryScreen(
    viewModel: MainViewModel,
    onUsePrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by remember { mutableStateOf<GalleryItem?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    if (viewModel.galleryItems.isEmpty()) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "Belum ada gambar. Generate dulu di Beranda, hasilnya bakal muncul di sini.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    } else {
        Column(modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${viewModel.galleryItems.size} gambar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { confirmClear = true }) {
                    Text("Hapus Semua", color = MaterialTheme.colorScheme.error)
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(viewModel.galleryItems, key = { it.id }) { item ->
                    GalleryCard(
                        item = item,
                        file = File(viewModel.galleryDir, item.fileName),
                        onClick = { selected = item }
                    )
                }
            }
        }
    }

    selected?.let { item ->
        GalleryDetailDialog(
            item = item,
            file = File(viewModel.galleryDir, item.fileName),
            onDismiss = { selected = null },
            onUsePrompt = {
                onUsePrompt(item.prompt)
                selected = null
            },
            onDelete = {
                viewModel.deleteGalleryItem(item)
                selected = null
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Hapus semua galeri?") },
            text = { Text("Semua gambar dan prompt di galeri bakal dihapus dari HP. Nggak bisa dibalikin.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearGallery()
                    confirmClear = false
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun GalleryCard(item: GalleryItem, file: File, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        modifier = Modifier.clickable { onClick() }
    ) {
        Column {
            LocalPngImage(
                file = file,
                contentDescription = item.prompt,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.prompt,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${item.resolution} • ${item.aspectRatio}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GalleryDetailDialog(
    item: GalleryItem,
    file: File,
    onDismiss: () -> Unit,
    onUsePrompt: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dateText = remember(item.createdAt) {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(item.createdAt))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Detail Gambar") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LocalPngImage(
                    file = file,
                    contentDescription = item.prompt,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    contentScale = ContentScale.Fit
                )
                Text(item.prompt, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${item.resolution} • ${item.aspectRatio} • $dateText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val ok = Downloader.saveToDownloads(context, file, item.fileName)
                            Toast.makeText(
                                context,
                                if (ok) "Tersimpan di Download/VeniceSeedream"
                                else "Gagal menyimpan gambar",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Download")
                }
                OutlinedButton(onClick = onUsePrompt, modifier = Modifier.fillMaxWidth()) {
                    Text("Pakai Prompt Lagi")
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}
