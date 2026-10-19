package com.lifestream.player.ui.components

/** A line from a Final Fantasy villain, shown as the Home screen title. */
data class VillainQuote(val text: String, val who: String, val game: String)

private const val I = "Final Fantasy"
private const val II = "Final Fantasy II"
private const val III = "Final Fantasy III"
private const val IV = "Final Fantasy IV"
private const val V = "Final Fantasy V"
private const val VI = "Final Fantasy VI"
private const val VII = "Final Fantasy VII"
private const val AC = "Final Fantasy VII: Advent Children"
private const val CC = "Crisis Core: Final Fantasy VII"
private const val VIII = "Final Fantasy VIII"
private const val IX = "Final Fantasy IX"
private const val X = "Final Fantasy X"
private const val X2 = "Final Fantasy X-2"
private const val XII = "Final Fantasy XII"
private const val XIII = "Final Fantasy XIII"
private const val XIII2 = "Final Fantasy XIII-2"
private const val LR = "Final Fantasy XIII: Lightning Returns"
private const val XIV = "Final Fantasy XIV"
private const val XV = "Final Fantasy XV"
private const val XVI = "Final Fantasy XVI"
private const val DC = "Dirge of Cerberus: Final Fantasy VII"
private const val CT = "Chrono Trigger"
private const val CX = "Chrono Cross"
private const val FFT = "Final Fantasy: Tactics"
private const val SOP = "Stranger of Paradise"

/** Checked against Wikiquote's game pages and the Final Fantasy Wiki; a few well-known lines from memory. */
val villainQuotes = listOf(
    // Final Fantasy
    VillainQuote("I, Garland, will knock you all down!", "Garland", I),
    VillainQuote("No one touches my princess!! LIGHT WARRIORS?? You impertinent fools.", "Garland", I),
    VillainQuote(
        "Oh, you did defeat me. But the four great forces saved me by sending me back through time!",
        "Garland", I,
    ),
    VillainQuote(
        "So even as you die again and again, I shall return! Born again into this endless circle I have created!",
        "Garland", I,
    ),
    VillainQuote(
        "In two thousand years, I will remember none of this. But I will be reborn again here.",
        "Garland", I,
    ),

    // II
    VillainQuote(
        "But the hand of man, which deals in false justice and forsaken love, can never hope to defeat the lord master of Hell!",
        "Dark Emperor", II,
    ),
    VillainQuote("Reluctance to forgive has long been a human failing.", "Emperor Mateus", II),
    VillainQuote(
        "It is man's nature to sin, yet you answer sin with violence upon violence. Is that not itself a sin?",
        "Emperor Mateus", II,
    ),
    VillainQuote("By imperial order, all who defy us shall be put to death.", "Imperial Soldier", II),
    VillainQuote(
        "Well, if you think you're getting Mythril, you're right. My Mythril blade through your heart!",
        "Imperial Sergeant", II,
    ),

    // III
    VillainQuote(
        "You've come far. But the darkness is three steps ahead of you. You're too late! Perish!!",
        "Xande", III,
    ),
    VillainQuote(
        "We are the Cloud of Darkness… None shall stand before the flood of darkness.",
        "Cloud of Darkness", III,
    ),
    VillainQuote(
        "You may have destroyed the dark crystals… but that is of no consequence.",
        "Cloud of Darkness", III,
    ),
    VillainQuote(
        "We shall devour your light, and use it to return this world to the Void!",
        "Cloud of Darkness", III,
    ),

    // IV
    VillainQuote("Allow me to give you something—a gift, to remember our meeting by.", "Golbez", IV),
    VillainQuote("Oh, you care for this one, do you? Then let me take her along as well.", "Golbez", IV),

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
    VillainQuote("For Gilgamesh… IT IS MORPHING TIME!", "Gilgamesh", V),
    VillainQuote("Then I, too, can disappear… Forever!", "Neo Exdeath", V),

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
    VillainQuote("Oh? Then… welcome to my barbecue! Hee-hee-hee!", "Kefka", VI),
    VillainQuote("\"Wait,\" he says… Do I look like a waiter?", "Kefka", VI),
    VillainQuote(
        "Hee-hee… Nothing beats the sweet music of hundreds of voices screaming in unison! Uwee-hee-hee!",
        "Kefka", VI,
    ),
    VillainQuote(
        "Read my lips — mercy is for wimps! There's a reason \"oppose\" rhymes with \"dispose\"…",
        "Kefka", VI,
    ),
    VillainQuote(
        "This little hamlet has too much boring and not enough burning… TORCH EVERYTHING!",
        "Kefka", VI,
    ),
    VillainQuote(
        "Why do you build, knowing destruction is inevitable? Why do you yearn to live, knowing all things must die?",
        "Kefka", VI,
    ),
    VillainQuote("Think how meaningless each of your lives is!", "Kefka", VI),
    VillainQuote("Now, for my next trick, I will make you all… disappear!", "Kefka", VI),
    VillainQuote("Such magnificent power! You are like insects to me!", "Kefka", VI),
    VillainQuote("There's sand on my boots!", "Kefka", VI),
    VillainQuote("Gah! How dare they put me in a place like this!", "Kefka", VI),
    VillainQuote("Ouch! B-blood… Blood! Blood!!! You vicious brat!", "Kefka", VI),
    VillainQuote(
        "You really are a slow one. And always, always… ALWAYS such a little goody two-shoes!!!",
        "Kefka", VI,
    ),
    VillainQuote(
        "I'm simply going to put you to sleep using the very power you unleashed! What's so funny?",
        "Emperor Gestahl", VI,
    ),
    VillainQuote("Gwee-hee-hee… You're up the creek without a paddle!", "Ultros", VI),
    VillainQuote("Yaaaouch! Seafood soup!", "Ultros", VI),
    VillainQuote("Muscle heads? Hate 'em!", "Ultros", VI),
    VillainQuote("Uh, well, Mom always said I was a slow learner… but I eat FAST!!", "Ultros", VI),
    VillainQuote("I'm nothing more than a stupid octopus!", "Ultros", VI),
    VillainQuote("Long time no see! You've changed! Did ya miss me?", "Ultros", VI),
    VillainQuote(
        "Thwarted again! I feel like such a sucker. Well, kids, hate to ink and run… but I AM an octopus!",
        "Ultros", VI,
    ),
    VillainQuote("Look at me! I'm a receptionist! G'fa, ha, ha!", "Ultros", VI),
    VillainQuote("Don't tease the octopus, kids!", "Ultros", VI),
    VillainQuote("I'd try not to make him mad… He gets hungry when he's irritated.", "Ultros", VI),
    VillainQuote(
        "In the name of all dragonkind, I shall grant you the death you so desire.",
        "Kaiser Dragon", VI,
    ),
    VillainQuote("I do not bleed, for I am but strength given form…", "Ultima Weapon", VI),
    VillainQuote(
        "Long have I pondered what I should do… Long, long have I pondered… But now it seems I have an answer…",
        "Ultima Buster", VI,
    ),
    VillainQuote("Ha, ha, ha. Give up?", "Siegfried", VI),

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
    VillainQuote("Shall I give you despair?", "Sephiroth", AC),
    VillainQuote("On your knees. I want you to beg for forgiveness.", "Sephiroth", AC),
    VillainQuote("I've thought of a wonderful present for you.", "Sephiroth", AC),
    VillainQuote("Boy, do I hate liars!", "Kadaj", AC),
    VillainQuote("Why does humankind even bother?", "Kadaj", AC),
    VillainQuote("Look what Brother was hiding. Powers forged in the Lifestream.", "Kadaj", AC),
    VillainQuote("Seven seconds till the end.", "Sephiroth", "Final Fantasy VII: Remake"),
    VillainQuote("Even if the morrow is barren of promises, nothing shall forestall my return.", "Genesis", CC),
    VillainQuote(
        "My friend, the fates are cruel. There are no dreams, no honor remains. The arrow has left the bow of the goddess.",
        "Genesis", CC,
    ),
    VillainQuote("Melding with the Planet… I will cease to exist as I am now…", "Sephiroth", VII),
    VillainQuote(
        "Forgive me for asking, but I can't be expected to remember each person's name.",
        "President Shinra", VII,
    ),
    VillainQuote(
        "And such a waste of good fireworks, just to get rid of vermin like you…",
        "President Shinra", VII,
    ),
    VillainQuote(
        "You are beginning to bore me. I'm a very busy man, so if you'll excuse me…",
        "President Shinra", VII,
    ),
    VillainQuote(
        "Is that all you can do…? Give one word answers and apologize for everything?",
        "Rufus Shinra", VII,
    ),
    VillainQuote("Would you like to be my guinea pig?", "Hojo", VII),
    VillainQuote("Why can't these people just keep quiet?", "Hojo", DC),
    VillainQuote("That's right, boy. It's me… Hojo!", "Weiss", DC),

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
    VillainQuote(
        "This is great… I have a Chicken-wuss and a guy who just reached puberty in my squad…",
        "Seifer", VIII,
    ),
    VillainQuote("What's the matter, Zell? Swatting flies?", "Seifer", VIII),
    VillainQuote("Instructor Trepe, I'm still one of your dearest students, aren't I?", "Seifer", VIII),
    VillainQuote("SHOCK!", "Fujin", VIII),
    VillainQuote("…Lowlifes. …Shameless filthy wretches.", "President Deling", VIII),
    VillainQuote("Insolent fools! Your vain krusade ends here, SeeDs.", "Ultimecia", VIII),
    VillainQuote("Whom shall I exterminate first!? I'll start with you three!", "Ultimecia", VIII),
    VillainQuote("Kurse all SeeDs. Swarming like lokusts akross generations.", "Ultimecia", VIII),
    VillainQuote("Reflect on your… Childhood…", "Ultimecia", VIII),
    VillainQuote("The most powerful GF… You shall… SUFFER…! HAHAHA…", "Ultimecia", VIII),
    VillainQuote("The GF's true power… Allow me… To show you…!", "Ultimecia", VIII),

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
    VillainQuote(
        "Just you wait, Garland! And you too, Zidane! I'll exact sweet revenge upon you both for insulting me!",
        "Kuja", IX,
    ),
    VillainQuote("What comedy! Zidane, isn't it hilarious!?", "Kuja", IX),
    VillainQuote(
        "I single-handedly brought chaos unto Gaia, but in the end, I'm nothing but a worthless doll!",
        "Kuja", IX,
    ),
    VillainQuote("Spare me the lecture. Lives come and go all the time. What's the big deal?", "Kuja", IX),
    VillainQuote("The weak lose their freedom to the strong. Such is the way of the strong.", "Kuja", IX),
    VillainQuote(
        "I'm gonna die anyway. I won't have to be afraid anymore. But I'm not gonna die alone. You're all coming with me!",
        "Kuja", IX,
    ),
    VillainQuote("A veritable emergency of terrible urgency!", "Zorn", IX),

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
    VillainQuote("Only the power of death truly commands in Spira. Resisting its power is futile.", "Maester Mika", X),
    VillainQuote("Being a Maester has its privileges. I can just sit here and watch.", "Maester Kinoc", X),
    VillainQuote("Well, if you're offering your lives, I will have to take them.", "Seymour", X),
    VillainQuote(
        "You see… now he has no worries; he has been granted sleep eternal. Death is a sweet slumber.",
        "Seymour", X,
    ),
    VillainQuote(
        "Don't you see… if all life were to end in Spira, all suffering would end. Don't you see? Do you not agree?",
        "Seymour", X,
    ),
    VillainQuote("You would oppose me as well? So be it!", "Seymour", X),
    VillainQuote(
        "By all means, try! You should thank me! Your death means your father's life!",
        "Seymour", X,
    ),
    VillainQuote("It is better to die in hope than live in despair.", "Lady Yunalesca", X),
    VillainQuote(
        "Hope is comforting. It allows us to accept fate, no matter how tragic it might be.",
        "Lady Yunalesca", X,
    ),
    VillainQuote("Poor creature. You would throw away hope?", "Lady Yunalesca", X),
    VillainQuote("They chose to die, because they had hope.", "Lady Yunalesca", X),
    VillainQuote("Ever the Ronso. Hard-headed, hardly useful.", "Maester Mika", X),

    // X-2
    VillainQuote("A thousand years… and this moment is all we get?!", "Shuyin", X2),
    VillainQuote("Vegnagun will make that all go away.", "Shuyin", X2),
    VillainQuote("You are NOT Lenne!", "Shuyin", X2),
    VillainQuote("Vanish with the rest of Spira!", "Shuyin", X2),
    VillainQuote("All this ugliness must end!", "Shuyin", X2),
    VillainQuote("I am the MIGHTY Leblanc!", "Leblanc", X2),
    VillainQuote("The power of love conquers all!", "Leblanc", X2),
    VillainQuote("Laugh while you can!", "Leblanc", X2),
    VillainQuote("Let me blow you a kiss!", "Leblanc", X2),
    VillainQuote("You're too slow, little girl.", "Logos", X2),
    VillainQuote("Show's over! Bwahahaha!", "Ormi", X2),
    VillainQuote("This puppet show is over!", "Nooj", X2),

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
    VillainQuote(
        "Archadia's Emperor is freely chosen by Her people. I am but an elected official and nothing more.",
        "Vayne", XII,
    ),
    VillainQuote(
        "Your lives are forfeit, and your insurgence with them. Dalmasca will again know order.",
        "Vayne", XII,
    ),
    VillainQuote("Even a stray has pride! Here I pay my debt.", "Gabranth", XII),
    VillainQuote("No! The power of manufacted nethicite is the power of man.", "Judge Bergan", XII),
    VillainQuote("The Empire requires you no more!", "Judge Ghis", XII),
    VillainQuote("Ah, how I have enjoyed these six years.", "Doctor Cid", XII),
    VillainQuote(
        "The whole affair has the smell of money. I may have to wet my beak a little.",
        "Ba'Gamnan", XII,
    ),
    VillainQuote("We need Balthier alive! His corpse fetches but half the bounty!", "Ba'Gamnan", XII),
    VillainQuote("Sky pirates, the future of Dalmasca will not be stolen!", "Vossler", XII),

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
    VillainQuote(
        "l'Cie? You mean me? Oh child, perish the thought. I am more than that!",
        "Barthandelus", XIII,
    ),
    VillainQuote(
        "Cocoon is a factory, built by fal'Cie. A factory for the mass production of human thralls.",
        "Galenth Dysley", XIII,
    ),
    VillainQuote("Without our help, death is all of which you're capable.", "Galenth Dysley", XIII),
    VillainQuote("A mindless mob drunk on fear of a few l'Cie.", "Galenth Dysley", XIII),
    VillainQuote("Now you spurn our counsel? You must learn your place!", "Barthandelus", XIII),
    VillainQuote("You are ignorant of your place.", "Barthandelus", XIII),
    VillainQuote("I am most disappointed.", "Barthandelus", XIII),
    VillainQuote("You will know true terror!", "Barthandelus", XIII),
    VillainQuote("Now you will bow to the fal'Cie!", "Barthandelus", XIII),
    VillainQuote("Ease is not the issue. You have not fought to win.", "Galenth Dysley", XIII),
    VillainQuote(
        "I shall savor the demise of Cocoon from atop the highest seat in all of Eden.",
        "Galenth Dysley", XIII,
    ),
    VillainQuote("You betray your fal'Cie to chase after dreams and shadows.", "Galenth Dysley", XIII),
    VillainQuote("To save a people beyond salvation, there is only Ragnarok.", "Galenth Dysley", XIII),
    VillainQuote(
        "Life's spark shines on, once freed from its fleshy shroud. Dreams, meanwhile, shatter in a flash.",
        "Galenth Dysley", XIII,
    ),
    VillainQuote("A haven, yea. Yet it must fall ere we be saved.", "Orphan", XIII),
    VillainQuote("We are the Abandoned One, born but now to die.", "Orphan", XIII),
    VillainQuote("Salvation is born of sacrifice—miracles, of misery.", "Orphan", XIII),
    VillainQuote("From shattered shards, a new crystal legend will arise.", "Orphan", XIII),
    VillainQuote("Lo! The day of Wrath has come!", "Orphan", XIII),
    VillainQuote("Yes! Let anger be your strength!", "Orphan", XIII),
    VillainQuote("You overreach yourselves.", "Orphan", XIII),
    VillainQuote(
        "We fal'Cie are crafted for a single purpose and granted finite power to that end.",
        "Orphan", XIII,
    ),
    VillainQuote(
        "Tell me, do you really think your life is worth more than the lives of millions of Cocoon citizens?",
        "Yaag Rosch", XIII,
    ),
    VillainQuote("It's not Sanctum. It's not us. It's the people.", "Yaag Rosch", XIII),
    VillainQuote("Under fal'Cie order, I've orchestrated murder.", "Yaag Rosch", XIII),
    VillainQuote("The capture of undesirables. It seems that was your son's Focus.", "Jihl Nabaat", XIII),
    VillainQuote("You should be proud. Your son's a hero. The boy who saved Cocoon.", "Jihl Nabaat", XIII),
    VillainQuote("Make it rain!", "Jihl Nabaat", XIII),
    VillainQuote("A l'Cie tied to an inescapable Focus—a slave of destiny.", "Cid Raines", XIII),
    VillainQuote(
        "I gained all the power I could hope for, but was a puppet with no will to wield it.",
        "Cid Raines", XIII,
    ),
    VillainQuote("My will is my own!", "Cid Raines", XIII),
    VillainQuote("The public fears what it doesn't know…", "Cid Raines", XIII),

    // XIII-2, Lightning Returns
    VillainQuote("Oblivion is the fate of all things.", "Caius", XIII2),
    VillainQuote("I shall remember your pain. It will be carved in my heart.", "Caius", XIII2),
    VillainQuote("Lose yourself in battle, and rejoice!", "Caius", XIII2),
    VillainQuote("Embrace the terror.", "Caius", XIII2),
    VillainQuote("Dusk of the wandering comet… I summon thee!", "Caius", XIII2),
    VillainQuote("Cast your shadow over all. Wings of night!", "Caius", XIII2),
    VillainQuote("To change history is a sin.", "Caius", XIII2),
    VillainQuote("Change is the constant.", "Caius", XIII2),
    VillainQuote("Face your fear! You shall pay for your crimes in blood.", "Caius", XIII2),
    VillainQuote("I must thank you. In these final moments, I have met my finest opponent.", "Caius", XIII2),
    VillainQuote("Times have changed. I am not the Caius you once knew.", "Caius", XIII2),
    VillainQuote("Noel. If you wish to succeed me… you must kill me.", "Caius", XIII2),
    VillainQuote("The goddess made a wise choice. Power of chaos, bend to my will!", "Caius", XIII2),
    VillainQuote("How many times must I see this?", "Caius", XIII2),
    VillainQuote("I shall go to Valhalla. I will kill the goddess, and set Yeul free.", "Caius", XIII2),
    VillainQuote("Warrior goddess. Now, we cross swords.", "Caius", XIII2),
    VillainQuote("In this dying world, nothing is more precious than time.", "Caius", LR),

    // XIV, XV
    VillainQuote("Remember us. Remember that we once lived.", "Emet-Selch", "Final Fantasy XIV: Shadowbringers"),
    VillainQuote("I'm a man of no consequence.", "Ardyn", "Final Fantasy XV"),
    VillainQuote("Man should fight for the joy of it.", "Zenos yae Galvus", XIV),
    VillainQuote(
        "Only man has the wisdom—and the clarity—to embrace violence for its own sake.",
        "Zenos yae Galvus", XIV,
    ),
    VillainQuote(
        "For a moment I felt death's chill wind, such is the might of the gods… But then, I remembered I'm immortal.",
        "Ardyn", XV,
    ),
    VillainQuote("Your attack hurt me, nevertheless… My feelings at least.", "Ardyn", XV),
    VillainQuote("Does it hurt? The pain is proof you're mortal!", "Ardyn", XV),
    VillainQuote("Testing, 1-2-3. Is this thing on?", "Ardyn", XV),
    VillainQuote(
        "Do forgive me for interrupting the festivities… but I must tell you this day of rapturous revelry shall be your last.",
        "Ardyn", XV,
    ),
    VillainQuote("False kings and fraudulent nations are fated to perish.", "Ardyn", XV),
    VillainQuote(
        "Resist, if you wish, but know that your fight against fate is a foolish and futile one.",
        "Ardyn", XV,
    ),
    VillainQuote("Come back soon. I shall keep your friends company until you are ready.", "Ardyn", XV),
    VillainQuote("Noct, killing you as a mortal will bring me scant satisfaction.", "Ardyn", XV),

    // XVI
    VillainQuote(
        "Mine is the altar at which you pray! Mine are the eyes that look down upon you all.",
        "Ultima", XVI,
    ),
    VillainQuote("Know your insignificance.", "Ultima", XVI),
    VillainQuote("Prostrate yourselves.", "Ultima", XVI),
    VillainQuote("We will unmake you!", "Ultima", XVI),
    VillainQuote("You know nothing of magic.", "Ultima", XVI),
    VillainQuote("Bow to your god!", "Ultima", XVI),
    VillainQuote("This is the work of my will.", "Ultima", XVI),

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
    VillainQuote(
        "Only those close to the top are rewarded without trying. It's the way of the world.",
        "Delita", FFT,
    ),
    VillainQuote("You and I are the same… miserable people forced to live false lives.", "Delita", FFT),
    VillainQuote("Treason's a serious crime, minister.", "Delita", FFT),
    VillainQuote(
        "A small stone may only make a small ripple at first, but someday it will be a wave.",
        "Wiegraf", FFT,
    ),
    VillainQuote("It is the Will of Heaven!", "Algus", FFT),
    VillainQuote("Absurd! Why they're nothing more than knaves, I say!", "Algus", FFT),
    VillainQuote(
        "If only you hadn't interfered… Ivalice would've been ours, the Beoulve's… You fools…",
        "Dycedarg", FFT,
    ),
    VillainQuote("Your brother has the devil's own luck.", "Vormav", FFT),
    VillainQuote("Bloody Angel!! I offer… my own life… for your resurrection!!", "Vormav", FFT),
    VillainQuote(
        "This castle's built for war. It's far more beautiful than one built for government.",
        "Barinten", FFT,
    ),
    VillainQuote("Opposing us is the same as blasphemy!", "Zalmo", FFT),
    VillainQuote("Repent! And your life will be saved!", "Zalmo", FFT),
    VillainQuote("You heretics don't fear God! I'll fix you!", "Zalmo", FFT),

    // Stranger of Paradise
    VillainQuote("CHAOS!!", "Jack Garland", SOP),
    VillainQuote("Bullshit.", "Jack Garland", SOP),
    VillainQuote("I'm here to kill Chaos.", "Jack Garland", SOP),
    VillainQuote("It's a mercy to forget.", "Jack Garland", SOP),

    // Chrono Trigger, Chrono Cross
    VillainQuote("The black wind howls… One among you will shortly perish…", "Magus", CT),
    VillainQuote(
        "If history is to change, let it change! If the world is to be destroyed, so be it! If my fate is to be destroyed, I must simply laugh!",
        "Magus", CT,
    ),
    VillainQuote(
        "Male… female… what's the difference? Power is beautiful, and I've got the power!",
        "Flea", CT,
    ),
    VillainQuote("For you there will be no tomorrow!", "Queen Zeal", CT),
    VillainQuote("There's nothing in the world as ruthless or impartial as death.", "Miguel", CX),
    VillainQuote("A future denied of all existence because of a change in the past…", "Miguel", CX),
    VillainQuote(
        "The sinners who are judged, and the victims that do the judging. What meaning is there to such a world?",
        "Time Devourer", CX,
    ),
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
        dirty = true
        pick
    }

    private var dirty = false

    /**
     * Writes the place in the deck to storage. Called when Home goes out of sight rather than on
     * every quote (one every 7.5 seconds), so the phone's storage isn't written that often.
     */
    fun save() = synchronized(this) {
        if (!dirty) return@synchronized
        dirty = false
        prefs?.edit()
            ?.putString(KEY_DECK, deck.joinToString(","))
            ?.putInt(KEY_LAST, last)
            ?.putInt(KEY_SIZE, villainQuotes.size)
            ?.apply()
    }
}
