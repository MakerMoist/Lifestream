package com.lifestream.player.ui.components

/** A line from a Final Fantasy villain, shown as the Home screen title. */
data class VillainQuote(val text: String, val who: String, val game: String)

private const val I = "Final Fantasy"
private const val V = "Final Fantasy V"
private const val VI = "Final Fantasy VI"
private const val VII = "Final Fantasy VII"
private const val AC = "Final Fantasy VII: Advent Children"
private const val CC = "Crisis Core: Final Fantasy VII"
private const val VIII = "Final Fantasy VIII"
private const val IX = "Final Fantasy IX"
private const val X = "Final Fantasy X"
private const val XII = "Final Fantasy XII"
private const val XIII = "Final Fantasy XIII"
private const val FFT = "Final Fantasy Tactics"

/** Mostly checked against Wikiquote's game pages; a few well-known lines from memory. */
val villainQuotes = listOf(
    // Final Fantasy
    VillainQuote("I, Garland, will knock you all down!", "Garland", I),

    // V
    VillainQuote("I cannot be defeated by anger or hatred!", "Exdeath", V),
    VillainQuote("Turtle!", "Exdeath", V),
    VillainQuote(
        "I am Neo Exdeath… All memory, existence, and dimensions… All that is shall be returned to nothing.",
        "Neo Exdeath", V,
    ),
    VillainQuote(
        "Enough expository banter. It's time we fight like men. And ladies. And ladies who dress like men.",
        "Gilgamesh", V,
    ),
    VillainQuote("HA! Did you really believe me?", "Gilgamesh", V),

    // VI
    VillainQuote("I will destroy everything… I will create a monument to non-existence!", "Kefka", VI),
    VillainQuote("Life… dreams… hope… Where do they come from? And where do they go?", "Kefka", VI),
    VillainQuote("Why do people insist on creating things that will inevitably be destroyed?", "Kefka", VI),
    VillainQuote("Son of a submariner!", "Kefka", VI),
    VillainQuote("But what fun is destruction if no \"precious\" lives are lost?", "Kefka", VI),
    VillainQuote("Run! Run! Or you'll be well done!", "Kefka", VI),
    VillainQuote("How 'bout a little Magitek mayhem?", "Kefka", VI),
    VillainQuote("Bleh! You people make me sick!", "Kefka", VI),
    VillainQuote("The end draws near…", "Kefka", VI),
    VillainQuote("Soldiers of the Empire! We stand at the dawn of a new age!", "Emperor Gestahl", VI),
    VillainQuote("I have more lives than I do arms!", "Ultros", VI),
    VillainQuote("Silence, knave! You are in the presence of octopus royalty!", "Ultros", VI),
    VillainQuote("My name is Ultima… I am power both ancient and unrivaled…", "Ultima Weapon", VI),

    // VII and friends
    VillainQuote("What I have shown you is reality. What you remember, that is the illusion.", "Sephiroth", VII),
    VillainQuote("Because, Cloud… you are a puppet.", "Jenova", VII),
    VillainQuote(
        "Power is truth. Shinra is the future. Real happiness can be found in obedience to the company.",
        "President Shinra", VII,
    ),
    VillainQuote("People are ignorant. They'll feel better as long as someone is punished.", "Rufus Shinra", VII),
    VillainQuote("I will never be a memory.", "Sephiroth", AC),
    VillainQuote("Tell me what you cherish most. Give me the pleasure of taking it away.", "Sephiroth", AC),
    VillainQuote("Seven seconds till the end.", "Sephiroth", "Final Fantasy VII Remake"),
    VillainQuote("Even if the morrow is barren of promises, nothing shall forestall my return.", "Genesis", CC),
    VillainQuote(
        "My friend, the fates are cruel. There are no dreams, no honor remains. The arrow has left the bow of the goddess.",
        "Genesis", CC,
    ),

    // VIII
    VillainQuote("I am Ultimecia. Time shall compress… All existence denied.", "Ultimecia", VIII),
    VillainQuote("Time, it will not wait…", "Ultimecia", VIII),
    VillainQuote("Griever! Make them bleed!", "Ultimecia", VIII),
    VillainQuote("I prefer to be called her knight.", "Seifer", VIII),
    VillainQuote("This is the scene where you swear your undying hatred for me!", "Seifer", VIII),
    VillainQuote("RAGE!", "Fujin", VIII),

    // IX
    VillainQuote("Why should the world exist without me? That wouldn't be fair. If I die, we all die!", "Kuja", IX),
    VillainQuote("Allow me to shatter your delusions of grandeur.", "Kuja", IX),
    VillainQuote("I commend your courage, but I shall show you no mercy.", "Kuja", IX),
    VillainQuote("I am the darkness of eternity…", "Necron", IX),
    VillainQuote("All life fears death from birth. Life fears death, but lives only to die.", "Necron", IX),
    VillainQuote("In a world of nothing, fear does not exist.", "Necron", IX),
    VillainQuote("We are in trouble! Trouble we are in!", "Zorn & Thorn", IX),

    // X
    VillainQuote("Life is but a passing dream, but the death that follows is eternal.", "Seymour", X),
    VillainQuote("Pitiful mortal… Your hope ends here!", "Seymour", X),
    VillainQuote("Let darkness take you!", "Seymour", X),
    VillainQuote("Even after I am gone, Spira's sorrow will prevail…", "Seymour", X),
    VillainQuote("Men die. Beasts die. Trees die. Even continents perish.", "Maester Mika", X),
    VillainQuote(
        "Enlightened rule by the dead is preferable to the misguided failures of the living.",
        "Maester Kinoc", X,
    ),
    VillainQuote("You'll cry, you're gonna cry, you always cry. See? You're crying.", "Jecht", X),

    // XII
    VillainQuote("My hands are stained with blood. I see little reason to stay them now.", "Vayne", XII),
    VillainQuote("I slew your king. I slew your country. Do these deeds not demand vengeance?", "Gabranth", XII),
    VillainQuote(
        "Too late and to their sorrow do those who misplace their trust in gods learn their fate.",
        "Judge Bergan", XII,
    ),
    VillainQuote("Blood alone does not an emperor make, Vayne.", "Judge Ghis", XII),

    // XIII
    VillainQuote("Your kind feared the darkness, so we gave you light.", "Barthandelus", XIII),
    VillainQuote("What else does one do with tools?", "Barthandelus", XIII),
    VillainQuote("I am the harbinger of death!", "Barthandelus", XIII),
    VillainQuote("Our name is Orphan. By our hand, the world shall know redemption.", "Orphan", XIII),
    VillainQuote(
        "Ragnarok. The will to guide a world unto oblivion. Can you bear the sin of our salvation?",
        "Orphan", XIII,
    ),
    VillainQuote("For every task, there's a perfect tool.", "Jihl Nabaat", XIII),
    VillainQuote("These are l'Cie. Show no mercy. They aren't people. They're targets.", "Yaag Rosch", XIII),

    // XIV, XV
    VillainQuote("Remember us. Remember that we once lived.", "Emet-Selch", "Final Fantasy XIV: Shadowbringers"),
    VillainQuote("I'm a man of no consequence.", "Ardyn", "Final Fantasy XV"),

    // Tactics
    VillainQuote("Me and you are caught up in the same flow… I'm just going against it.", "Delita", FFT),
    VillainQuote("I won't be used. I'll be the one using!", "Delita", FFT),
    VillainQuote("'God' is an image created out of man's insecurities.", "Wiegraf", FFT),
    VillainQuote("'Reality' is far harsher than you can possibly imagine!", "Wiegraf", FFT),
    VillainQuote("Animals have no God!!", "Algus", FFT),
    VillainQuote("Betraying you? C'mon, this is business!", "Gafgarion", FFT),
)

/** Picks quotes at random, never the same one twice in a row. */
object VillainQuotes {
    private var last = -1

    fun next(): Int {
        val pick = (villainQuotes.indices - last).random()
        last = pick
        return pick
    }
}
