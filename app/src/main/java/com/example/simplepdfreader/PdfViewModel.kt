package com.example.simplepdfreader

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PdfViewModel : ViewModel() {
    // ---- Tài liệu ----
    var doc by mutableStateOf<PdfDoc?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    // ---- Tìm kiếm ----
    var searchActive by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
        private set
    var searching by mutableStateOf(false)
        private set
    var searchProgress by mutableFloatStateOf(0f)
        private set
    var searchDone by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set
    var matches by mutableStateOf<List<TextMatch>>(emptyList())
        private set
    var currentIndex by mutableIntStateOf(-1)
        private set

    /** Tăng mỗi khi cần cuộn tới kết quả hiện tại. */
    var focusTick by mutableIntStateOf(0)
        private set

    private var searchJob: Job? = null

    fun open(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val newDoc = PdfDoc.open(appContext, uri)
                val old = doc
                closeSearch()
                doc = newDoc
                if (old != null) launch(Dispatchers.IO) { old.close() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                error = "File PDF có mật khẩu hoặc bị khóa (bản này chưa hỗ trợ)."
            } catch (e: Exception) {
                error = "Không đọc được file. File có thể bị hỏng hoặc không phải PDF."
            } finally {
                loading = false
            }
        }
    }

    // ---------------- Tìm kiếm ----------------

    fun openSearch() {
        searchActive = true
    }

    fun updateQuery(text: String) {
        query = text
        if (text.isBlank()) clearResults()
    }

    fun closeSearch() {
        clearResults()
        query = ""
        searchActive = false
    }

    private fun clearResults() {
        searchJob?.cancel()
        searching = false
        searchDone = false
        searchError = null
        matches = emptyList()
        currentIndex = -1
    }

    fun search() {
        val d = doc ?: return
        val text = query.trim()
        if (text.isEmpty()) return
        clearResults()
        searchJob = viewModelScope.launch {
            searching = true
            searchProgress = 0f
            val found = ArrayList<TextMatch>()
            try {
                for (p in 0 until d.pageCount) {
                    val hits = d.search(p, text)
                    if (hits.isNotEmpty()) {
                        found += hits
                        matches = found.toList()
                        if (currentIndex < 0) {
                            currentIndex = 0
                            focusTick++ // nhảy tới kết quả đầu tiên ngay khi tìm thấy
                        }
                    }
                    searchProgress = (p + 1f) / d.pageCount
                }
                searchDone = true
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                searchError = "Máy này chưa hỗ trợ tìm chữ trong PDF."
            } finally {
                if (isActive) searching = false
            }
        }
    }

    fun nextMatch() {
        if (matches.isEmpty()) return
        currentIndex = (currentIndex + 1) % matches.size
        focusTick++
    }

    fun prevMatch() {
        if (matches.isEmpty()) return
        currentIndex = (currentIndex - 1 + matches.size) % matches.size
        focusTick++
    }

    override fun onCleared() {
        val d = doc ?: return
        doc = null
        CoroutineScope(Dispatchers.IO).launch { d.close() }
    }
}
