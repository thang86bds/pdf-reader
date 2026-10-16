@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.simplepdfreader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val vm: PdfViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Android 15+ bắt buộc edge-to-edge, Scaffold lo phần inset
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            PdfReaderTheme {
                PdfReaderApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { vm.open(applicationContext, it) }
        }
    }
}

@Composable
fun PdfReaderApp(vm: PdfViewModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.open(context, it) }
    }
    val openPicker = { picker.launch(arrayOf("application/pdf")) }
    val doc = vm.doc

    BackHandler(enabled = vm.searchActive) { vm.closeSearch() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            if (doc != null && vm.searchActive) {
                SearchTopBar(vm)
            } else {
                TopAppBar(
                    title = {
                        Text(
                            doc?.name ?: "PDF Reader",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    actions = {
                        if (doc != null) {
                            IconButton(onClick = { vm.openSearch() }) {
                                Icon(Icons.Default.Search, contentDescription = "Tìm kiếm")
                            }
                        }
                        TextButton(onClick = { openPicker() }) { Text("Mở file") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            val errorText = vm.error
            when {
                vm.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                errorText != null -> MessageState(
                    badge = "!",
                    title = "Không mở được file",
                    body = errorText,
                    buttonText = "Chọn file khác",
                    isError = true,
                    onClick = { openPicker() },
                )

                doc == null -> MessageState(
                    badge = "PDF",
                    title = "Đọc PDF nhanh gọn",
                    body = "Chọn một file PDF để bắt đầu. Bạn có thể tìm chữ và nhảy tới trang bất kỳ.",
                    buttonText = "Mở file PDF",
                    isError = false,
                    onClick = { openPicker() },
                )

                else -> key(doc) { PdfViewer(vm, doc, padding.calculateBottomPadding()) }
            }
        }
    }
}

@Composable
private fun SearchTopBar(vm: PdfViewModel) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = { vm.closeSearch() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Đóng tìm kiếm")
            }
        },
        title = {
            TextField(
                value = vm.query,
                onValueChange = { vm.updateQuery(it) },
                placeholder = { Text("Tìm trong tài liệu") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    vm.search()
                }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        },
        actions = {
            if (vm.query.isNotEmpty()) {
                IconButton(onClick = { vm.updateQuery("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Xóa")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@Composable
private fun MessageState(
    badge: String,
    title: String,
    body: String,
    buttonText: String,
    isError: Boolean,
    onClick: () -> Unit,
) {
    val container = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val onContainer = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = container, modifier = Modifier.size(96.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    badge,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = onContainer,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClick) { Text(buttonText) }
    }
}
