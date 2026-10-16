@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.simplepdfreader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PdfViewer(vm: PdfViewModel, doc: PdfDoc, bottomInset: Dp) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var zoom by rememberSaveable { mutableStateOf(1f) }
    var showJump by remember { mutableStateOf(false) }

    // Gom kết quả tìm kiếm theo trang để mỗi trang chỉ vẽ phần của mình
    val highlights = remember(vm.matches) { vm.matches.withIndex().groupBy { it.value.page } }
    val currentPage = listState.firstVisibleItemIndex + 1

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pinchToZoom { factor -> zoom = (zoom * factor).coerceIn(1f, 3f) },
    ) {
        // Render cố định ở ~2x chiều rộng màn hình để zoom không phải render lại
        val renderWidthPx = (constraints.maxWidth * 2).coerceAtMost(2048)

        // Cuộn tới kết quả tìm kiếm hiện tại (kể cả vị trí dọc trong trang)
        LaunchedEffect(vm.focusTick) {
            val match = vm.matches.getOrNull(vm.currentIndex) ?: return@LaunchedEffect
            val rect = match.rects.firstOrNull() ?: return@LaunchedEffect
            val pageSize = doc.pageSizes[match.page]
            val pageHeightPx = constraints.maxWidth * zoom * pageSize.height / pageSize.width
            val y = rect.top / pageSize.height * pageHeightPx
            val offset = (y - constraints.maxHeight / 3f).toInt().coerceAtLeast(0)
            listState.animateScrollToItem(match.page, offset)
        }

        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            LazyColumn(
                state = listState,
                modifier = Modifier.width(maxWidth * zoom).fillMaxHeight(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = 12.dp,
                    end = 12.dp,
                    bottom = 96.dp + bottomInset,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(doc.pageCount, key = { it }) { index ->
                    PdfPage(doc, index, renderWidthPx, highlights[index].orEmpty(), vm.currentIndex)
                }
            }
        }

        if (vm.searching) {
            LinearProgressIndicator(
                progress = { vm.searchProgress },
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            )
        }

        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp + bottomInset),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val searchError = vm.searchError
            when {
                vm.matches.isNotEmpty() -> SearchNavPill(
                    index = vm.currentIndex,
                    total = vm.matches.size,
                    stillSearching = vm.searching,
                    onPrev = { vm.prevMatch() },
                    onNext = { vm.nextMatch() },
                )
                vm.searching -> InfoPill("Đang tìm…")
                searchError != null -> InfoPill(searchError)
                vm.searchDone -> InfoPill("Không tìm thấy")
            }
            PagePill(currentPage, doc.pageCount) { showJump = true }
        }
    }

    if (showJump) {
        JumpToPageDialog(
            currentPage = currentPage,
            pageCount = doc.pageCount,
            onDismiss = { showJump = false },
            onJump = { page ->
                showJump = false
                scope.launch { listState.animateScrollToItem(page - 1) }
            },
        )
    }
}

@Composable
private fun PdfPage(
    doc: PdfDoc,
    index: Int,
    renderWidthPx: Int,
    highlights: List<IndexedValue<TextMatch>>,
    currentIndex: Int,
) {
    var bitmap by remember(doc, index, renderWidthPx) { mutableStateOf<ImageBitmap?>(null) }

    // LazyColumn chỉ compose các trang đang hiện → chỉ render trang cần xem
    LaunchedEffect(doc, index, renderWidthPx) {
        bitmap = doc.render(index, renderWidthPx)?.asImageBitmap()
    }

    val pageSize = doc.pageSizes[index]
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(pageSize.width.toFloat() / pageSize.height),
        shape = RoundedCornerShape(6.dp),
        color = Color.White,
        shadowElevation = 4.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            bitmap?.let {
                Image(bitmap = it, contentDescription = "Trang ${index + 1}", modifier = Modifier.fillMaxSize())
            }
            if (highlights.isNotEmpty()) {
                Canvas(Modifier.fillMaxSize()) {
                    val scale = size.width / pageSize.width
                    for (hit in highlights) {
                        val color = if (hit.index == currentIndex) {
                            Color(0xFFFF9800).copy(alpha = 0.55f) // kết quả đang chọn: cam
                        } else {
                            Color(0xFFFFEB3B).copy(alpha = 0.40f) // kết quả khác: vàng
                        }
                        for (r in hit.value.rects) {
                            drawRect(
                                color = color,
                                topLeft = Offset(r.left * scale, r.top * scale),
                                size = Size(r.width() * scale, r.height() * scale),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PagePill(page: Int, total: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
    ) {
        Text(
            "$page / $total",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun SearchNavPill(
    index: Int,
    total: Int,
    stillSearching: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 6.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Kết quả trước")
            }
            Text(
                "${index + 1}/$total" + if (stillSearching) "+" else "",
                style = MaterialTheme.typography.labelLarge,
            )
            IconButton(onClick = onNext) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Kết quả sau")
            }
        }
    }
}

@Composable
private fun InfoPill(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 6.dp,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun JumpToPageDialog(
    currentPage: Int,
    pageCount: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
) {
    val initial = currentPage.toString()
    var field by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focusRequester = remember { FocusRequester() }
    val target = field.text.toIntOrNull()
    val valid = target != null && target in 1..pageCount

    fun submit() {
        if (target != null && target in 1..pageCount) onJump(target)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Đi tới trang") },
        text = {
            Column {
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it.copy(text = it.text.filter(Char::isDigit).take(6)) },
                    label = { Text("Trang (1–$pageCount)") },
                    singleLine = true,
                    isError = field.text.isNotEmpty() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
                if (pageCount > 1) {
                    Slider(
                        value = (target ?: 1).coerceIn(1, pageCount).toFloat(),
                        onValueChange = { v ->
                            val n = v.roundToInt().toString()
                            field = TextFieldValue(n, TextRange(n.length))
                        },
                        valueRange = 1f..pageCount.toFloat(),
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
            }
        },
        confirmButton = { TextButton(onClick = { submit() }, enabled = valid) { Text("Đi") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}

/** Chụm 2 ngón để zoom; vuốt 1 ngón vẫn cuộn bình thường. */
private fun Modifier.pinchToZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val factor = event.calculateZoom()
                if (factor != 1f) onZoom(factor)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
