package com.lifestream.player.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifestream.player.data.PagedSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Observable, incrementally-loaded list backed by a [PagedSource]. */
class PagedList<T>(
    private val scope: CoroutineScope,
    private val source: PagedSource<T>,
    /** A second value that must also be unique (e.g. the recording's id). */
    private val alsoUnique: ((T) -> String)? = null,
    private val key: (T) -> String,
) {
    var items by mutableStateOf<List<T>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var endReached by mutableStateOf(false)
        private set

    /** Counts finished loads, so the footer asks again even when a load added nothing new. */
    var loads by mutableIntStateOf(0)
        private set

    private var next: Any? = null
    private var emptyStreak = 0

    fun loadMore() {
        if (loading || endReached) return
        loading = true
        error = null
        scope.launch {
            try {
                // Filtering can leave a page empty; keep going (a few pages max) so the
                // list never sits on a spinner with nothing new to show.
                val seen = items.mapTo(HashSet<String>(), key)
                val seenAlso = alsoUnique?.let { f -> items.mapTo(HashSet<String>(), f) }
                var added = 0
                var attempts = 0
                do {
                    val page = source.load(next)
                    val fresh = page.items.filter { item ->
                        val k = key(item)
                        val a = alsoUnique?.invoke(item)
                        if (k in seen || (a != null && seenAlso != null && a in seenAlso)) {
                            false
                        } else {
                            seen += k
                            if (a != null) seenAlso?.add(a)
                            true
                        }
                    }
                    items = items + fresh
                    added += fresh.size
                    next = page.next
                    attempts++
                } while (added == 0 && next != null && attempts < 6)
                // Give up after many pages in a row with nothing new, rather than spin forever.
                emptyStreak = if (added == 0) emptyStreak + 1 else 0
                endReached = next == null || emptyStreak >= 5
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message?.take(160) ?: e.javaClass.simpleName
            } finally {
                loading = false
                loads++
            }
        }
    }
}

/** Adds the list's items followed by a footer that loads the next page when scrolled into view. */
fun <T> LazyListScope.pagedItems(
    list: PagedList<T>,
    footerKey: String,
    key: (T) -> String,
    emptyText: String = "Nothing found.",
    itemContent: @Composable LazyItemScope.(index: Int, item: T) -> Unit,
) {
    itemsIndexed(list.items, key = { _, item -> key(item) }) { index, item -> itemContent(index, item) }
    item(key = footerKey) { PagedFooter(list, emptyText) }
}

@Composable
private fun <T> PagedFooter(list: PagedList<T>, emptyText: String) {
    val error = list.error
    Box(
        Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            error != null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Couldn't reach YouTube: $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(onClick = { list.loadMore() }) { Text("Try again") }
            }

            !list.endReached -> {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                LaunchedEffect(list.items.size, list.loads) { list.loadMore() }
            }

            list.items.isEmpty() -> Text(
                emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
