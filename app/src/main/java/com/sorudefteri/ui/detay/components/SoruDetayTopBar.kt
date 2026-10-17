package com.sorudefteri.ui.detay.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoruDetayTopBar(
    isFavorite: Boolean,
    onBackClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit,
    onShareExternalAiClick: () -> Unit = {},
    onTeacherShareClick: () -> Unit = {},
    isDrawingActive: Boolean = false,
    onToggleDrawing: (() -> Unit)? = null,
    onCopyImageClick: (() -> Unit)? = null,
    // TELEFON: true ise az ikon + taşma menüsü (dikey tek el kullanımı).
    // Tablet'te false geçilir, tüm ikonlar görünür (davranış korunur).
    compactActions: Boolean = false
) {
    var isShareMenuExpanded by remember { mutableStateOf(false) }
    var isOverflowExpanded by rememberSaveable { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = "Soru Detayı",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Geri"
                )
            }
        },
        actions = {
            if (compactActions) {
                // TELEFON: sadece Favori + Paylaş görünür, gerisi taşma menüsünde.
                // 360dp'de 6 ikon sığmıyordu.
                IconButton(onClick = onFavoriteToggle) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (isFavorite) "Favoriden Çıkar" else "Favoriye Ekle",
                        tint = if (isFavorite) Color(0xFFFFB800) else MaterialTheme.colorScheme.onSurface
                    )
                }
                Box {
                    IconButton(onClick = { isShareMenuExpanded = true }) {
                        Icon(Icons.Default.Share, contentDescription = "Paylaş")
                    }
                    DropdownMenu(
                        expanded = isShareMenuExpanded,
                        onDismissRequest = { isShareMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("👨‍🏫 Hocama Sor (WhatsApp)") },
                            onClick = {
                                isShareMenuExpanded = false
                                onTeacherShareClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Soruyu Paylaş") },
                            onClick = {
                                isShareMenuExpanded = false
                                onShareClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Harici AI ile Çöz & Paylaş") },
                            onClick = {
                                isShareMenuExpanded = false
                                onShareExternalAiClick()
                            }
                        )
                    }
                }
                Box {
                    IconButton(onClick = { isOverflowExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Diğer işlemler")
                    }
                    DropdownMenu(
                        expanded = isOverflowExpanded,
                        onDismissRequest = { isOverflowExpanded = false }
                    ) {
                        if (onToggleDrawing != null) {
                            DropdownMenuItem(
                                text = { Text(if (isDrawingActive) "✏️ Karalamayı Kapat" else "✏️ Karalama Modu") },
                                onClick = {
                                    isOverflowExpanded = false
                                    onToggleDrawing()
                                }
                            )
                        }
                        if (onCopyImageClick != null) {
                            DropdownMenuItem(
                                text = { Text("📋 Görseli Kopyala") },
                                onClick = {
                                    isOverflowExpanded = false
                                    onCopyImageClick()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("✏️ Düzenle") },
                            onClick = {
                                isOverflowExpanded = false
                                onEditClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("🗑 Sil") },
                            onClick = {
                                isOverflowExpanded = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            } else {
                // TABLET: mevcut 6 ikonlu düzen aynen korunur (küçük düzeltme yok)
                if (onToggleDrawing != null) {
                    IconButton(onClick = onToggleDrawing) {
                        Icon(
                            imageVector = Icons.Default.Brush,
                            contentDescription = if (isDrawingActive) "Karalama Modunu Kapat" else "Karalama Modunu Aç",
                            tint = if (isDrawingActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                if (onCopyImageClick != null) {
                    IconButton(onClick = onCopyImageClick) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Görseli Kopyala",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                IconButton(onClick = onFavoriteToggle) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (isFavorite) "Favoriden Çıkar" else "Favoriye Ekle",
                        tint = if (isFavorite) Color(0xFFFFB800) else MaterialTheme.colorScheme.onSurface
                    )
                }
                Box {
                    IconButton(onClick = { isShareMenuExpanded = true }) {
                        Icon(Icons.Default.Share, contentDescription = "Paylaş")
                    }
                    DropdownMenu(
                        expanded = isShareMenuExpanded,
                        onDismissRequest = { isShareMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("👨‍🏫 Hocama Sor (WhatsApp)") },
                            leadingIcon = { Text("👨‍🏫") },
                            onClick = {
                                isShareMenuExpanded = false
                                onTeacherShareClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Soruyu Paylaş (Genel / WhatsApp)") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                isShareMenuExpanded = false
                                onShareClick()
                            }
                        )
                        if (onCopyImageClick != null) {
                            DropdownMenuItem(
                                text = { Text("📋 Görseli Panoya Kopyala") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = {
                                    isShareMenuExpanded = false
                                    onCopyImageClick()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Harici AI ile Çöz & Paylaş") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                isShareMenuExpanded = false
                                onShareExternalAiClick()
                            }
                        )
                    }
                }
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Düzenle")
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}
