package com.example.ui.preview

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PaperSize
import com.example.model.ScanSource
import com.example.model.ScannedDocument
import com.example.util.PdfGenerator
import com.example.util.SharingHelper
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Brand green color for WhatsApp
val WhatsAppGreen = Color(0xFF25D366)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewScreen(
    pages: List<Bitmap>,
    lastUsedSource: ScanSource = ScanSource.CAMERA,
    onBackToHome: () -> Unit,
    onEditPage: (pageIndex: Int) -> Unit,
    onAddNewPage: () -> Unit, // Direct 1-tap launch of the previously chosen source!
    onDeletePage: ((pageIndex: Int) -> Unit)? = null,
    onSavedSuccessfully: (ScannedDocument) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentPageIndex by remember { mutableIntStateOf(0) }
    var selectedPaperSize by remember { mutableStateOf(PaperSize.A4) }
    var documentTitle by remember {
        mutableStateOf("Scan_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}")
    }
    var isSaving by remember { mutableStateOf(false) }
    var savedDocResult by remember { mutableStateOf<ScannedDocument?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }

    val safePageIndex = currentPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val currentBitmap = if (pages.isNotEmpty()) pages[safePageIndex] else null

    // Back handling: If already saved, go directly Home. If not saved, ask confirmation.
    fun handleBack() {
        if (savedDocResult != null) {
            onBackToHome()
        } else {
            showExitConfirmDialog = true
        }
    }

    BackHandler {
        handleBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier
                            .clickable { showRenameDialog = true }
                            .padding(vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$documentTitle.pdf",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Title",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "Page ${safePageIndex + 1} of ${pages.size} • ${selectedPaperSize.displayName} (${selectedPaperSize.description})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { handleBack() },
                        modifier = Modifier.testTag("preview_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home"
                        )
                    }
                },
                actions = {
                    // + Add Page button: DIRECT 1-TAP LAUNCH of previously chosen source (Camera or Gallery)
                    FilledTonalButton(
                        onClick = onAddNewPage,
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("preview_add_page_button")
                    ) {
                        Icon(
                            imageVector = if (lastUsedSource == ScanSource.CAMERA) Icons.Default.CameraAlt else Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (lastUsedSource == ScanSource.CAMERA) "+ Camera" else "+ Gallery",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Direct Home button if saved
                    if (savedDocResult != null) {
                        IconButton(
                            onClick = onBackToHome,
                            modifier = Modifier.testTag("preview_home_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Go to Home",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // 1. PDF Size Selector Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PDF Output Size:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Segmented buttons for paper size
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PaperSize.values().forEach { size ->
                                val isSelected = selectedPaperSize == size
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedPaperSize = size },
                                    label = {
                                        Text(
                                            text = size.displayName,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                    },
                                    leadingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("paper_size_chip_${size.name.lowercase()}")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Action buttons: Edit, Save, WhatsApp Share
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Edit Button (routes back to CropScreen for current page)
                        OutlinedButton(
                            onClick = { onEditPage(safePageIndex) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("preview_edit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit", fontWeight = FontWeight.SemiBold)
                        }

                        // Save Button
                        Button(
                            onClick = {
                                if (!isSaving && pages.isNotEmpty()) {
                                    isSaving = true
                                    coroutineScope.launch {
                                        val result = PdfGenerator.generatePdf(
                                            context = context,
                                            pages = pages,
                                            paperSize = selectedPaperSize,
                                            customTitle = documentTitle
                                        )
                                        isSaving = false
                                        result.onSuccess { doc ->
                                            savedDocResult = doc
                                            showSuccessDialog = true
                                            onSavedSuccessfully(doc)
                                        }.onFailure { err ->
                                            Toast.makeText(
                                                context,
                                                "Failed to save PDF: ${err.message}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            },
                            enabled = !isSaving,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(48.dp)
                                .testTag("preview_save_button")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save PDF", fontWeight = FontWeight.Bold)
                            }
                        }

                        // WhatsApp Share Button
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val targetDoc = savedDocResult
                                    if (targetDoc != null && File(targetDoc.filePath).exists()) {
                                        SharingHelper.shareToWhatsApp(context, File(targetDoc.filePath))
                                    } else {
                                        // Auto-generate before sharing
                                        isSaving = true
                                        val result = PdfGenerator.generatePdf(
                                            context = context,
                                            pages = pages,
                                            paperSize = selectedPaperSize,
                                            customTitle = documentTitle
                                        )
                                        isSaving = false
                                        result.onSuccess { doc ->
                                            savedDocResult = doc
                                            onSavedSuccessfully(doc)
                                            SharingHelper.shareToWhatsApp(context, File(doc.filePath))
                                        }.onFailure { err ->
                                            Toast.makeText(
                                                context,
                                                "Failed to generate PDF for WhatsApp: ${err.message}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WhatsAppGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(48.dp)
                                .testTag("whatsapp_share_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "WhatsApp",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0F172A))
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Multi-page thumbnail selector strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(pages) { index, bitmap ->
                        val isSelected = index == safePageIndex
                        val thumbBitmap = remember(bitmap) { bitmap.asImageBitmap() }
                        Box(
                            modifier = Modifier
                                .size(56.dp, 76.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { currentPageIndex = index }
                        ) {
                            Image(
                                bitmap = thumbBitmap,
                                contentDescription = "Page ${index + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(Color(0xCC000000))
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = "P.${index + 1}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Direct Add Page Tile in the row
                    item {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E293B),
                            modifier = Modifier
                                .size(56.dp, 76.dp)
                                .border(1.dp, Color(0xFF475569), RoundedCornerShape(6.dp))
                                .clickable { onAddNewPage() }
                                .testTag("strip_add_page_button")
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (lastUsedSource == ScanSource.CAMERA) Icons.Default.CameraAlt else Icons.Default.PhotoLibrary,
                                    contentDescription = "Add Page",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (lastUsedSource == ScanSource.CAMERA) "+ Camera" else "+ Gallery",
                                    fontSize = 9.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Delete current page if multi-page
                if (pages.size > 1 && onDeletePage != null) {
                    IconButton(
                        onClick = {
                            onDeletePage(safePageIndex)
                            if (currentPageIndex >= pages.size - 1) {
                                currentPageIndex = (pages.size - 2).coerceAtLeast(0)
                            }
                        },
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Page",
                            tint = Color(0xFFEF4444)
                        )
                    }
                }
            }

            // Realistic PDF Paper Preview Canvas Container
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .aspectRatio(selectedPaperSize.aspectRatio)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentBitmap != null) {
                            val imgBitmap = remember(currentBitmap) { currentBitmap.asImageBitmap() }
                            Image(
                                bitmap = imgBitmap,
                                contentDescription = "Scanned Page Document",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = "No page content",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // Paper watermark/corner badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .background(Color(0x1A000000), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = selectedPaperSize.displayName,
                                fontSize = 10.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Document Info Summary Strip
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E293B)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Dimensions", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Text(selectedPaperSize.description, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                    VerticalDivider(modifier = Modifier.height(28.dp), color = Color(0xFF334155))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Pages", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Text("${pages.size} page(s)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                    VerticalDivider(modifier = Modifier.height(28.dp), color = Color(0xFF334155))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Resolution", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Text(
                            "${currentBitmap?.width ?: 0}×${currentBitmap?.height ?: 0}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Exit Confirmation Dialog
    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Exit to Home?") },
            text = { Text("Are you sure you want to exit to the home screen? Any unsaved edits will be discarded.") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmDialog = false
                        onBackToHome()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_exit_home_button")
                ) {
                    Text("Exit to Home")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmDialog = false }) {
                    Text("Stay Here")
                }
            }
        )
    }

    // Rename Dialog
    if (showRenameDialog) {
        var tempTitle by remember { mutableStateOf(documentTitle) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename PDF Document") },
            text = {
                OutlinedTextField(
                    value = tempTitle,
                    onValueChange = { tempTitle = it },
                    label = { Text("Document File Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_text_field")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (tempTitle.isNotBlank()) {
                            documentTitle = tempTitle.trim()
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Save Success Dialog
    if (showSuccessDialog && savedDocResult != null) {
        val doc = savedDocResult!!
        val file = File(doc.filePath)
        val sizeKb = doc.fileSizeBytes / 1024

        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text("PDF Saved Successfully!", textAlign = TextAlign.Center)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${doc.title}.pdf",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Size: ${if (sizeKb > 1024) String.format("%.1f MB", sizeKb / 1024f) else "$sizeKb KB"} • Format: ${doc.paperSize.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Saved in app storage and public Downloads/ScanCraft folder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        SharingHelper.shareToWhatsApp(context, file)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WhatsAppGreen
                    ),
                    modifier = Modifier.testTag("success_dialog_whatsapp")
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share to WhatsApp", color = Color.White)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            showSuccessDialog = false
                            SharingHelper.viewPdf(context, file)
                        }
                    ) {
                        Text("Open")
                    }
                    Button(
                        onClick = {
                            showSuccessDialog = false
                            onBackToHome()
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Home")
                    }
                }
            }
        )
    }
}
