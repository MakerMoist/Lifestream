package com.lifestream.player.ui.components

/** A line from a Final Fantasy villain, shown as the Home screen title. */
data class VillainQuote(val text: String, val who: String, val game: String)

val villainQuotes = listOf(
    VillainQuote("I will never be a memory.", "Sephiroth", "Final Fantasy VII: Advent Children"),
    VillainQuote("Seven seconds till the end.", "Sephiroth", "Final Fantasy VII Remake"),
    VillainQuote("I will create a monument to non-existence!", "Kefka", "Final Fantasy VI"),
    VillainQuote("Life… dreams… hope… Where do they come from? And where do they go?", "Kefka", "Final Fantasy VI"),
    VillainQuote("Son of a submariner!", "Kefka", "Final Fantasy VI"),
    VillainQuote("I, Garland, will knock you all down!", "Garland", "Final Fantasy"),
    VillainQuote("I'm a man of no consequence.", "Ardyn", "Final Fantasy XV"),
    VillainQuote(
        "Enough expository banter! Now we fight like men! And ladies! And ladies who dress like men!",
        "Gilgamesh",
        "Final Fantasy V",
    ),
    VillainQuote("Remember us. Remember that we once lived.", "Emet-Selch", "Final Fantasy XIV: Shadowbringers"),
)
