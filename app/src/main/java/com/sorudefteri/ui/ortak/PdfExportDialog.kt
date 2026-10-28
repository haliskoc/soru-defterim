package com.sorudefteri.ui.ortak

import androidx.compose.runtime.Composable
import com.sorudefteri.domain.model.Soru
import java.io.File

/**
 * [com.sorudefteri.ui.kutuphane.components.PdfExportDialog] bileşeni için ortak UI köprüsü.
 */
@Composable
fun PdfExportDialog(
    sorular: List<Soru>,
    onDismiss: () -> Unit,
    onSuccess: (File) -> Unit = {}
) {
    com.sorudefteri.ui.kutuphane.components.PdfExportDialog(
        sorular = sorular,
        onDismiss = onDismiss,
        onSuccess = onSuccess
    )
}
