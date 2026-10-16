package com.example.simplepdfreader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/** Kích thước trang theo đơn vị point của PDF. */
data class PageSize(val width: Int, val height: Int)

/** Một kết quả tìm kiếm: nằm ở trang [page], gồm 1+ hình chữ nhật (toạ độ point của trang). */
data class TextMatch(val page: Int, val rects: List<RectF>)

/**
 * Bọc PdfRenderer của Android.
 * PdfRenderer chỉ cho phép mở 1 trang tại một thời điểm và không thread-safe,
 * nên mọi thao tác (render, tìm chữ) đều đi qua Mutex.
 */
class PdfDoc private constructor(
    private val pfd: ParcelFileDescriptor,
    val name: String?,
) {
    private val renderer = PdfRenderer(pfd)
    private val mutex = Mutex()
    private var closed = false

    val pageCount: Int = renderer.pageCount

    val pageSizes: List<PageSize> = List(pageCount) { i ->
        renderer.openPage(i).use { PageSize(it.width, it.height) }
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (closed) return@withLock null
            renderer.openPage(index).use { page ->
                val heightPx = (widthPx * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    /**
     * Tìm chữ trong 1 trang bằng PdfRenderer.Page.searchText (API 35+).
     * Lưu ý: chỉ tìm được trong PDF có lớp chữ; PDF scan thành ảnh thì không có kết quả.
     */
    suspend fun search(pageIndex: Int, query: String): List<TextMatch> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (closed) {
                emptyList<TextMatch>()
            } else {
                renderer.openPage(pageIndex).use { page ->
                    page.searchText(query).map { m ->
                        TextMatch(pageIndex, m.bounds.map { RectF(it) })
                    }
                }
            }
        }
    }

    suspend fun close() = mutex.withLock {
        if (!closed) {
            closed = true
            renderer.close()
            pfd.close()
        }
    }

    companion object {
        suspend fun open(context: Context, uri: Uri): PdfDoc = withContext(Dispatchers.IO) {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IOException("Không mở được file")
            val name = context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            try {
                PdfDoc(pfd, name)
            } catch (t: Throwable) {
                pfd.close()
                throw t
            }
        }
    }
}
