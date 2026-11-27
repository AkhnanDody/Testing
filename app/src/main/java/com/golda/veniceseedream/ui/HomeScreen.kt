package com.golda.veniceseedream.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.golda.veniceseedream.ui.theme.AccentYellow
import com.golda.veniceseedream.ui.theme.DarkSurface

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    hasApiKey: Boolean,
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isImg2Img = viewModel.mode == GenerateMode.IMG2IMG

    // Begitu Generate ditekan, langsung pindah ke Galeri — prosesnya jalan
    // sendiri di latar, hasilnya (atau gagalnya) muncul di sana.
    LaunchedEffect(viewModel.navigateToGallery) {
        if (viewModel.navigateToGallery) {
            viewModel.consumeNavigateToGallery()
            onOpenGallery()
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.setReferenceImage(uri)
    }

    val canGenerate = viewModel.prompt.isNotBlank() &&
        (!isImg2Img || viewModel.referenceImage != null)

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!hasApiKey) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "API key belum diisi. Buka menu hamburger di kiri atas > Pengaturan, lalu isi API key Venice kamu.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AccentYellow,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Tab switch: Text2Image / Img2Img
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModeTabButton(
                    text = "Text2Image",
                    selected = !isImg2Img,
                    onClick = { viewModel.selectMode(GenerateMode.TEXT2IMAGE) },
                    modifier = Modifier.weight(1f)
                )
                ModeTabButton(
                    text = "Img2Img",
                    selected = isImg2Img,
                    onClick = { viewModel.selectMode(GenerateMode.IMG2IMG) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (isImg2Img) {
                val ref = viewModel.referenceImage
                if (ref == null) {
                    OutlinedButton(
                        onClick = { pickImage.launch("image/*") },
                        enabled = !viewModel.isCompressing,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    ) {
                        if (viewModel.isCompressing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.size(10.dp))
                            Text("Mengompres gambar...")
                        } else {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Pilih Gambar Referensi")
                        }
                    }
                    Text(
                        text = "Gambar otomatis dikompresi (maks 1024px) sebelum dikirim ke Venice.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Preview dibatasi tingginya (maks 260dp, dipotong rapi) —
                            // gambar tinggi nggak lagi mendorong prompt & tombol jauh ke bawah.
                            ByteArrayImage(
                                bytes = ref.jpegBytes,
                                contentDescription = "Gambar referensi",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 260.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Text(
                                text = "${ref.dimensionsText} • ${ref.sizeText}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { pickImage.launch("image/*") },
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Ganti Gambar")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.clearReferenceImage() },
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = viewModel.prompt,
                onValueChange = { viewModel.prompt = it },
                label = { Text("Prompt") },
                placeholder = {
                    Text(
                        if (isImg2Img) "Contoh: ganti langit jadi sunset, hapus pohon itu..."
                        else "Contoh: a cute cat astronaut, cinematic lighting..."
                    )
                },
                minLines = 4,
                maxLines = 6,
                shape = RoundedCornerShape(20.dp),
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
                if (isImg2Img) {
                    LabeledDropdown(
                        label = "Resolusi",
                        options = Options.EDIT_RESOLUTIONS,
                        selected = viewModel.editResolution,
                        onSelected = { viewModel.editResolution = it },
                        modifier = Modifier.weight(1f)
                    )
                    LabeledDropdown(
                        label = "Aspect Ratio",
                        options = Options.EDIT_ASPECT_RATIOS,
                        selected = viewModel.editAspectRatio,
                        onSelected = { viewModel.editAspectRatio = it },
                        modifier = Modifier.weight(1f)
                    )
                } else {
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
            }

            viewModel.errorMessage?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Beranda sengaja tanpa gambar hasil — semua hasil ada di Galeri.
            // Ruang kosong di bawah isi form memang disisakan; yang menempel
            // di bawah layar adalah tombol Generate di luar area scroll ini.
        }

        // Tombol Generate menempel di bawah (di atas navbar), selalu terlihat
        // tanpa perlu scroll. Nonaktif = kuning redup, bukan abu-abu mati.
        Surface(color = MaterialTheme.colorScheme.background) {
            Button(
                onClick = { viewModel.generate() },
                enabled = canGenerate,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentYellow,
                    contentColor = Color.Black,
                    disabledContainerColor = AccentYellow.copy(alpha = 0.35f),
                    disabledContentColor = Color.Black.copy(alpha = 0.65f)
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(56.dp)
            ) {
                Text(if (isImg2Img) "Edit Gambar" else "Generate")
            }
        }
    }
}

@Composable
private fun ModeTabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentYellow,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = modifier.height(48.dp)
        ) {
            Text(text)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(24.dp),
            modifier = modifier.height(48.dp)
        ) {
            Text(text)
        }
    }
}
