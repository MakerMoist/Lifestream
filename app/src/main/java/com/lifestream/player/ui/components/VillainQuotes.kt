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
    VillainQuote("Starkle, starkle little twink… Now it's time that you guys SINK!!", "Gilgamesh", V),
    VillainQuote("I was wrong, I cannot fight four people…", "Gilgamesh", V),

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
    VillainQuote("Feeble creatures of flesh, your time is nigh!", "Ultima Weapon", VI),
    VillainQuote(
        "Oh dear… Well, I guess I was a bit hasty in calling you a useless old man before… NOW you're useless!",
        "Kefka", VI,
    ),
    VillainQuote("Celes, child… You alone are special.", "Emperor Gestahl", VI),
    VillainQuote("I am the dealer of destruction… I am the font from which fear springs…", "Kaiser Dragon", VI),
    VillainQuote("Aha! The ox bellows! Allow me to introduce my blade!", "Siegfried", VI),
    VillainQuote("G'fu, fu, fu… Who're these numbskulls?", "Chadarnook", VI),
    VillainQuote(
        "Good day, fine sirs, how may I be of service? I hate fighting so I better let you pass!",
        "Dadaluma", VI,
    ),

    // VII and friends
    VillainQuote("What I have shown you is reality. What you remember, that is the illusion.", "Sephiroth", VII),
    VillainQuote("Because, Cloud… you are a puppet.", "Jenova", VII),
    VillainQuote(
        "Power is truth. Shinra is the future. Real happiness can be found in obedience to the company.",
        "President Shinra", VII,
    ),
    VillainQuote("People are ignorant. They'll feel better as long as someone is punished.", "Rufus Shinra", VII),
    VillainQuote(
        "Only death awaits you all, but do not fear. For it is through death that a new spirit energy is born.",
        "Sephiroth", VII,
    ),
    VillainQuote("Stop acting as if you were sad. There's no need to act as though you're angry either.", "Sephiroth", VII),
    VillainQuote("Out of my way. I'm going to see my mother.", "Sephiroth", VII),
    VillainQuote("Do not worry. Soon the girl will become part of the Planet's energy.", "Sephiroth", VII),
    VillainQuote("You are just a puppet… You have no heart… and cannot feel any pain…", "Sephiroth", VII),
    VillainQuote("These days, all it takes for your dreams to come true is money and power.", "President Shinra", VII),
    VillainQuote("I'll control the world with fear.", "Rufus Shinra", VII),
    VillainQuote("This is the Promised Land the President has been searching for!!", "Heidegger", VII),
    VillainQuote("No one gets in the way of Reno of the Turks…", "Reno", VII),
    VillainQuote("Two!!! Number Two!!!", "Don Corneo", VII),
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
    VillainQuote("I shall junction myself… unto Griever.", "Ultimecia", VIII),
    VillainQuote("I love battles. I fear nothing.", "Seifer", VIII),
    VillainQuote("One of these days I'm gonna tell ya 'bout my ROMANTIC dream!", "Seifer", VIII),
    VillainQuote("Listen up! Teamwork means staying out of my way. It's a Squad B rule.", "Seifer", VIII),

    // IX
    VillainQuote("Why should the world exist without me? That wouldn't be fair. If I die, we all die!", "Kuja", IX),
    VillainQuote("Allow me to shatter your delusions of grandeur.", "Kuja", IX),
    VillainQuote("I commend your courage, but I shall show you no mercy.", "Kuja", IX),
    VillainQuote("I am the darkness of eternity…", "Necron", IX),
    VillainQuote("All life fears death from birth. Life fears death, but lives only to die.", "Necron", IX),
    VillainQuote("In a world of nothing, fear does not exist.", "Necron", IX),
    VillainQuote("We are in trouble! Trouble we are in!", "Zorn & Thorn", IX),
    VillainQuote(
        "I once killed a hundred knights single-handedly… To me, you two are nothing more than insects.",
        "Kuja", IX,
    ),
    VillainQuote("I won't let this world exist without me!", "Kuja", IX),
    VillainQuote("I will kill my nemesis… with my own hand! Hahaha! Everything is according to plan.", "Kuja", IX),
    VillainQuote("You are destined to live among the stars for all eternity.", "Garland", IX),
    VillainQuote("Regrettable… I thought your soul would be perfect for a new angel of death…", "Garland", IX),
    VillainQuote("Why defy your fate? Is the will to live that powerful…?", "Necron", IX),
    VillainQuote("Come. Enter the Zero World you so desire.", "Necron", IX),

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
    VillainQuote("But there is no salvation for the damned! Rest in peace in eternal darkness.", "Seymour", X),
    VillainQuote("Unpleasant. Very well. I will give you your death. You seem to want it so.", "Seymour", X),
    VillainQuote("Feel my pain! Come, Anima!", "Seymour", X),
    VillainQuote("Death awaits you!", "Seymour", X),
    VillainQuote("Why do today what you can leave for tomorrow?", "Jecht", X),
    VillainQuote("Resisting its power is futile.", "Maester Mika", X),
    VillainQuote("Being a Maester has its privileges. I can just sit here and watch.", "Maester Kinoc", X),

    // XII
    VillainQuote("My hands are stained with blood. I see little reason to stay them now.", "Vayne", XII),
    VillainQuote("I slew your king. I slew your country. Do these deeds not demand vengeance?", "Gabranth", XII),
    VillainQuote(
        "Too late and to their sorrow do those who misplace their trust in gods learn their fate.",
        "Judge Bergan", XII,
    ),
    VillainQuote("Blood alone does not an emperor make, Vayne.", "Judge Ghis", XII),
    VillainQuote("What am I to do with those who would oppose me… but show them death?", "Vayne", XII),
    VillainQuote("The gods are toying with us.", "Vayne", XII),
    VillainQuote("Attain it I shall. For too long have my deeds gone unrewarded.", "Vayne", XII),
    VillainQuote(
        "Futile, Basch! Long have I walked in hatred's company. As long as I can curse your name, I shall not be defeated!",
        "Gabranth", XII,
    ),
    VillainQuote(
        "Preen and strut as you like! In the end, we are the same. Blood-thirsting carrion birds, hell-bent on revenge!",
        "Gabranth", XII,
    ),
    VillainQuote(
        "Spend your pity elsewhere. If you are so set on running, hadn't you best be off, fool of a pirate?",
        "Doctor Cid", XII,
    ),
    VillainQuote(
        "The Cryst is sundered, Age of Stones, complete. From the Undying ones the world is freed.",
        "Venat", XII,
    ),
    VillainQuote("The new Ivalice holds no place for the name Dalmasca.", "Judge Bergan", XII),

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
    VillainQuote("If you truly seek salvation, you will obey!", "Barthandelus", XIII),
    VillainQuote("You struggle in vain!", "Barthandelus", XIII),
    VillainQuote("Yet again. How many times must you fail?", "Orphan", XIII),
    VillainQuote("I put you on the path. That was my Focus.", "Cid Raines", XIII),
    VillainQuote("Strike down the phantoms before their eyes, and they sleep like babes.", "Cid Raines", XIII),

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
    VillainQuote("Don't blame us. Blame yourself or God.", "Delita", FFT),
    VillainQuote(
        "Most people have to act the roles given to them. Then again, most of them haven't even noticed they're acting.",
        "Delita", FFT,
    ),
    VillainQuote("You can't fulfill your dreams without power!", "Wiegraf", FFT),
    VillainQuote("Never believe in \"never\".", "Algus", FFT),
    VillainQuote(
        "Unless you make it to the top you're just going to get used, so you're better off dead!",
        "Gafgarion", FFT,
    ),
    VillainQuote("I don't know what's going on, but it's in the contract!", "Gafgarion", FFT),
    VillainQuote("'Justice'? I'm embarrassed to think you even said that!", "Dycedarg", FFT),
    VillainQuote("Don't underestimate us. Killing you is easy…", "Vormav", FFT),
)

/**
 * Deals quotes like a shuffled deck: every quote is shown once, in random order, before any comes
 * round again (and the last one of a round never opens the next). Where you are in the deck is
 * kept on the phone, so reopening the app carries on instead of starting over with repeats.
 */
object VillainQuotes {
    private const val PREFS = "quotes"
    private const val KEY_DECK = "deck"
    private const val KEY_LAST = "last"
    private const val KEY_SIZE = "size"

    private var prefs: android.content.SharedPreferences? = null
    private val deck = ArrayDeque<Int>()
    private var last = -1

    fun init(context: android.content.Context) {
        val p = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        prefs = p
        synchronized(this) {
            // A deck saved for a different set of quotes (an update added some) is dealt afresh.
            if (p.getInt(KEY_SIZE, -1) != villainQuotes.size) {
                deck.clear()
                return
            }
            last = p.getInt(KEY_LAST, -1).takeIf { it in villainQuotes.indices } ?: -1
            val saved = p.getString(KEY_DECK, "").orEmpty().split(',')
                .mapNotNull { it.toIntOrNull() }
                .filter { it in villainQuotes.indices }
                .distinct()
            deck.clear()
            deck.addAll(saved)
        }
    }

    fun next(): Int = synchronized(this) {
        if (deck.isEmpty()) {
            val fresh = villainQuotes.indices.shuffled().toMutableList()
            // Don't open the new round with the quote that just ended the last one.
            if (fresh.size > 1 && fresh.first() == last) fresh.add(fresh.removeAt(0))
            deck.addAll(fresh)
        }
        val pick = deck.removeFirst()
        last = pick
        prefs?.edit()
            ?.putString(KEY_DECK, deck.joinToString(","))
            ?.putInt(KEY_LAST, pick)
            ?.putInt(KEY_SIZE, villainQuotes.size)
            ?.apply()
        pick
    }
}
