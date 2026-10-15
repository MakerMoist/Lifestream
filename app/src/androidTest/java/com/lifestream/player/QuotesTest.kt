package com.lifestream.player

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifestream.player.ui.components.VillainQuotes
import com.lifestream.player.ui.components.villainQuotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Home's quotes: every one shows before any repeats, and that carries on after a restart. */
@RunWith(AndroidJUnit4::class)
class QuotesTest {
    @Test
    fun everyQuoteShowsBeforeAnyRepeats() {
        val n = villainQuotes.size
        assertTrue("plenty of quotes: $n", n >= 100)
        assertEquals("no duplicate quotes", n, villainQuotes.map { it.text }.toSet().size)
        // Across round boundaries too, the same quote never shows twice in a row.
        var previous = VillainQuotes.next()
        repeat(n * 3) {
            val next = VillainQuotes.next()
            assertNotEquals("never the same quote twice in a row", previous, next)
            previous = next
        }
    }

    @Test
    fun aFreshRoundHasNoRepeatsAndSurvivesRestart() {
        val n = villainQuotes.size
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Start a clean round.
        context.getSharedPreferences("quotes", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        VillainQuotes.init(context)
        val seen = LinkedHashSet<Int>()
        repeat(n / 2) { seen += VillainQuotes.next() }
        // "Restart": the app saves its place when it's left, then reloads it and finishes the round.
        VillainQuotes.save()
        Thread.sleep(300)
        VillainQuotes.init(context)
        repeat(n - n / 2) { seen += VillainQuotes.next() }
        assertEquals("all $n quotes shown once each", n, seen.size)
        Log.i("LSTEST", "Quotes: all $n shown once each before any repeat, across restarts")
    }
}
