package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.data.DocumentRepository
import com.example.model.ScanSource
import com.example.model.ScannedDocument
import com.example.ui.crop.CropScreen
import com.example.ui.home.HomeScreen
import com.example.ui.preview.PdfPreviewScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.ImageProcessing
import kotlinx.coroutines.launch
import java.io.File

enum class AppScreen {
    HOME,
    CROP,
    PREVIEW
}

class MainActivity : ComponentActivity() {

    private lateinit var documentRepository: DocumentRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        documentRepository = DocumentRepository(applicationContext)

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScannerApp(repository = documentRepository)
                }
            }
        }
    }
}

@Composable
fun ScannerApp(repository: DocumentRepository) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    val documents by repository.documents.collectAsState()

    // Page bitmaps list for the currently active document
    val scannedPages = remember { mutableStateListOf<Bitmap>() }
    var editingPageIndex by remember { mutableIntStateOf(-1) }
    var isAddingNewPage by remember { mutableStateOf(false) }
    var imageToCrop by remember { mutableStateOf<Bitmap?>(null) }

    // Remembers whether the user started scanning via Camera or Gallery
    var lastUsedSource by remember { mutableStateOf(ScanSource.CAMERA) }

    // Initial load of documents
    LaunchedEffect(Unit) {
        repository.loadDocuments()
    }

    // Temporary photo file & Uri for camera capture
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun createTempImageUri(): Uri {
        val tempFile = File.createTempFile("scan_camera_", ".jpg", context.cacheDir).apply {
            createNewFile()
            deleteOnExit()
        }
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, tempFile)
    }

    // Camera Launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            val bitmap = ImageProcessing.loadBitmapFromUri(context, tempCameraUri!!)
            if (bitmap != null) {
                imageToCrop = bitmap
                currentScreen = AppScreen.CROP
            } else {
                Toast.makeText(context, "Could not load captured photo", Toast.LENGTH_SHORT).show()
                if (scannedPages.isNotEmpty()) {
                    currentScreen = AppScreen.PREVIEW
                }
            }
        } else {
            // Camera was cancelled or closed
            if (scannedPages.isNotEmpty()) {
                currentScreen = AppScreen.PREVIEW
            }
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val uri = createTempImageUri()
            tempCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            Toast.makeText(
                context,
                "Camera permission is required to scan documents directly",
                Toast.LENGTH_LONG
            ).show()
            if (scannedPages.isNotEmpty()) {
                currentScreen = AppScreen.PREVIEW
            }
        }
    }

    fun launchCamera() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            val uri = createTempImageUri()
            tempCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Gallery Picker Launcher
    val pickVisualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bitmap = ImageProcessing.loadBitmapFromUri(context, uri)
            if (bitmap != null) {
                imageToCrop = bitmap
                currentScreen = AppScreen.CROP
            } else {
                Toast.makeText(context, "Failed to load selected image", Toast.LENGTH_SHORT).show()
                if (scannedPages.isNotEmpty()) {
                    currentScreen = AppScreen.PREVIEW
                }
            }
        } else {
            // Gallery was cancelled or closed
            if (scannedPages.isNotEmpty()) {
                currentScreen = AppScreen.PREVIEW
            }
        }
    }

    fun launchGallery() {
        pickVisualMediaLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    // Reset all document states when explicitly returning to Home
    fun returnToHome() {
        scannedPages.clear()
        editingPageIndex = -1
        isAddingNewPage = false
        imageToCrop = null
        currentScreen = AppScreen.HOME
    }

    // UI screen rendering
    when (currentScreen) {
        AppScreen.HOME -> {
            HomeScreen(
                documents = documents,
                onScanClick = {
                    scannedPages.clear()
                    editingPageIndex = -1
                    isAddingNewPage = false
                    lastUsedSource = ScanSource.CAMERA
                    launchCamera()
                },
                onGalleryClick = {
                    scannedPages.clear()
                    editingPageIndex = -1
                    isAddingNewPage = false
                    lastUsedSource = ScanSource.GALLERY
                    launchGallery()
                },
                onSampleClick = {
                    scannedPages.clear()
                    editingPageIndex = -1
                    isAddingNewPage = false
                    lastUsedSource = ScanSource.CAMERA
                    val sampleBitmap = ImageProcessing.createSampleDocumentBitmap()
                    imageToCrop = sampleBitmap
                    currentScreen = AppScreen.CROP
                },
                onDeleteDocument = { doc ->
                    coroutineScope.launch {
                        repository.deleteDocument(doc)
                    }
                }
            )
        }

        AppScreen.CROP -> {
            if (imageToCrop != null) {
                val currentPgNum = if (editingPageIndex >= 0) editingPageIndex + 1 else scannedPages.size + 1
                CropScreen(
                    initialBitmap = imageToCrop!!,
                    pageNumber = currentPgNum,
                    lastUsedSource = lastUsedSource,
                    onBack = {
                        if (scannedPages.isNotEmpty()) {
                            // If previous pages exist, preserve them safely and return to Preview
                            editingPageIndex = -1
                            isAddingNewPage = false
                            currentScreen = AppScreen.PREVIEW
                        } else {
                            // First page cancelled from Home
                            returnToHome()
                        }
                    },
                    onProceedToPreview = { croppedBitmap ->
                        // Save/add current page and proceed to PDF Preview
                        if (editingPageIndex in scannedPages.indices) {
                            scannedPages[editingPageIndex] = croppedBitmap
                            editingPageIndex = -1
                        } else {
                            scannedPages.add(croppedBitmap)
                        }
                        isAddingNewPage = false
                        currentScreen = AppScreen.PREVIEW
                    },
                    onScanNextPage = { croppedBitmap ->
                        // 1. ALWAYS ADD current page to scannedPages!
                        if (editingPageIndex in scannedPages.indices) {
                            scannedPages[editingPageIndex] = croppedBitmap
                            editingPageIndex = -1
                        } else {
                            scannedPages.add(croppedBitmap)
                        }
                        isAddingNewPage = true
                        // 2. Set currentScreen to PREVIEW first as a safe fallback
                        currentScreen = AppScreen.PREVIEW
                        // 3. Immediately open camera or gallery for the next photo!
                        if (lastUsedSource == ScanSource.CAMERA) {
                            launchCamera()
                        } else {
                            launchGallery()
                        }
                    }
                )
            } else {
                if (scannedPages.isNotEmpty()) {
                    currentScreen = AppScreen.PREVIEW
                } else {
                    returnToHome()
                }
            }
        }

        AppScreen.PREVIEW -> {
            PdfPreviewScreen(
                pages = scannedPages,
                lastUsedSource = lastUsedSource,
                onBackToHome = {
                    returnToHome()
                },
                onEditPage = { pageIndex ->
                    if (pageIndex in scannedPages.indices) {
                        editingPageIndex = pageIndex
                        isAddingNewPage = false
                        imageToCrop = scannedPages[pageIndex]
                        currentScreen = AppScreen.CROP
                    }
                },
                onAddNewPage = {
                    // DIRECT LAUNCH: Immediately open camera or gallery for the next page!
                    editingPageIndex = -1
                    isAddingNewPage = true
                    if (lastUsedSource == ScanSource.CAMERA) {
                        launchCamera()
                    } else {
                        launchGallery()
                    }
                },
                onDeletePage = { pageIndex ->
                    if (scannedPages.size > 1 && pageIndex in scannedPages.indices) {
                        scannedPages.removeAt(pageIndex)
                    }
                },
                onSavedSuccessfully = { savedDoc ->
                    coroutineScope.launch {
                        repository.addDocument(savedDoc)
                    }
                }
            )
        }
    }
}
