import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// Kreat's test arena (kreat.puregames.app): the browser build of PureGames with Kreat alone.
// It shares the app's source and the web build's browser layer, but compiles only Kreat's game code and the
// screens around it, so it builds in a fraction of the time. Its game list (KreatGames.kt) holds Kreat only,
// and its page sets window.PG_ONLY so the app opens straight into Kreat with no hub and no two-player or
// online play (see AppViewModel.soloGame).
//
//     cd kreat-web && ../gradlew -p . wasmJsBrowserDistribution
//     then deploy build/dist/wasmJs/productionExecutable to the Vercel project "kreat"
plugins {
    kotlin("multiplatform") version "2.1.21"
    kotlin("plugin.compose") version "2.1.21"
    id("org.jetbrains.compose") version "1.8.2"
}

val appSrc = file("../app/src/main/java")
val appRes = file("../app/src/main/res")
val webSrc = file("../web/src/wasmJsMain/kotlin")

/** Other games' code: their rules and screens. Kreat needs none of it. */
val otherGames = listOf(
    // rules
    "CageMath.kt", "Hashi.kt", "Kakuro.kt", "LogicGrader.kt", "Nonogram.kt", "QuickGames.kt", "SudokuGenerator.kt", "SudokuSolver.kt",
).map { "com/puresudoku/core/$it" } + listOf("cards/**", "city/**", "learn/**", "words/**").map { "com/puresudoku/core/$it" } +
    listOf(
        "AegConquest.kt", "AegStewardship.kt", "Aegwaspell.kt", "Backgammon.kt", "Chess.kt", "Cratebarge.kt", "CratebargeVoyage.kt",
        "Deeds.kt", "EastBoards.kt", "GoGame.kt", "Kreashun.kt", "Mahjong.kt", "Realm.kt", "RealmAi.kt", "SeaBattle.kt",
        "SmallGames.kt", "Veilguard.kt", "Wisteria.kt",
    ).map { "com/puresudoku/core/board/$it" } +
    // screens
    listOf("cagemath", "cards", "city", "code", "crossword", "haiku", "hashi", "kakuro", "learn", "lumen", "picture", "quiz", "realm",
        "recall", "sliding", "sudoku", "wordplay", "wordsearch").map { "com/puresudoku/ui/games/$it/**" } +
    listOf("ChessGames.kt", "ClassicBoards.kt", "CratebargeGame.kt", "GoAndMore.kt", "SeaBattleGame.kt").map { "com/puresudoku/ui/games/board/$it" } +
    listOf("com/puresudoku/ui/games/DigitPlay.kt", "com/puresudoku/ui/games/GameRegistry.kt",
        "com/puresudoku/ui/components/PlayingCard.kt", "com/puresudoku/ui/training/CardScripts.kt",
        // online play: this site is you against the computer only (StubsOnline.kt)
        "com/puresudoku/online/**", "com/puresudoku/ui/screens/OnlineScreen.kt", "platform/WebOnline.kt")

// The app's art, gathered into the layout Compose Multiplatform resources expect
val gatherArt by tasks.registering(Sync::class) {
    from(appRes.resolve("drawable-nodpi")) { into("drawable") }
    from(appRes.resolve("drawable")) { into("drawable") }
    from(appRes.resolve("font")) { into("font") }
    from(file("../web/fonts")) { into("font"); include("*.ttf") }
    into(layout.buildDirectory.dir("appArt"))
}

// The site's own page, name and icons, plus the shared AI worker and legal pages
val gatherSite by tasks.registering(Sync::class) {
    from(file("../web/src/wasmJsMain/resources")) { include("ai-worker.js", "vercel.json", "privacy.html", "terms.html") }
    from(file("site"))
    into(layout.buildDirectory.dir("site"))
}

// Android-style ids for the art (R.drawable.x), as in the web build
val generateR by tasks.registering {
    val dirs = listOf(appRes.resolve("drawable-nodpi"), appRes.resolve("drawable"))
    val out = layout.buildDirectory.dir("generated/r")
    inputs.files(dirs)
    outputs.dir(out)
    doLast {
        val files = dirs.flatMap { d -> d.listFiles().orEmpty().toList() }.associateBy { it.nameWithoutExtension }
        val names = files.keys.sorted()
        val root = out.get().asFile
        root.deleteRecursively()
        root.resolve("com/puresudoku").mkdirs()
        root.resolve("com/puresudoku/R.kt").writeText(buildString {
            appendLine("package com.puresudoku")
            appendLine("object R {")
            appendLine("    object drawable {")
            names.forEachIndexed { i, n -> appendLine("        const val $n = $i") }
            appendLine("    }")
            appendLine("}")
        })
        root.resolve("com/puresudoku/web").mkdirs()
        root.resolve("com/puresudoku/web/DrawableTable.kt").writeText(buildString {
            appendLine("package com.puresudoku.web")
            appendLine("import com.puresudoku.web.res.*")
            appendLine("import org.jetbrains.compose.resources.DrawableResource")
            appendLine("private val TABLE: Array<DrawableResource> by lazy { arrayOf(")
            names.forEach { n -> appendLine("    Res.drawable.$n,") }
            appendLine(") }")
            appendLine("fun drawableFor(id: Int): DrawableResource = TABLE[id]")
            val bitmaps = names.mapIndexedNotNull { i, n -> i.takeIf { files.getValue(n).extension != "xml" } }
            appendLine("private val BITMAPS = setOf(${bitmaps.joinToString()})")
            appendLine("fun drawableIsBitmap(id: Int): Boolean = id in BITMAPS")
        })
    }
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("puregames")
        browser { commonWebpackConfig { outputFileName = "puregames.js" } }
        binaries.executable()
    }
    sourceSets {
        val wasmJsMain by getting {
            kotlin.srcDir(appSrc)
            kotlin.srcDir(webSrc)
            kotlin.srcDir(generateR)
            resources.srcDir(gatherSite)
            kotlin.exclude(
                "com/puresudoku/MainActivity.kt",
                "com/puresudoku/data/BackupStore.kt",
                // Android versions of the platform layer; the browser versions come from ../web
                "com/puresudoku/platform/**",
            )
            kotlin.exclude(otherGames)
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-browser:0.3")
            }
        }
    }
}

compose.resources {
    publicResClass = false
    generateResClass = always
    packageOfResClass = "com.puresudoku.web.res"
    customDirectory(sourceSetName = "commonMain", directoryProvider = gatherArt.map { layout.buildDirectory.dir("appArt").get() })
}

// The service worker, as in the web build: every file kept offline, and a version that changes with any of them.
// Both builds get one: the quick test build (wasmJsBrowserDevelopmentExecutableDistribution, under a minute
// after a change) that the test arena runs, and the optimized one (wasmJsBrowserDistribution, about 4 minutes).
fun writeServiceWorker(dist: File) {
    val skip = setOf("sw.js", "vercel.json")
    val files = dist.walkTopDown()
        .filter { it.isFile && !it.relativeTo(dist).invariantSeparatorsPath.split("/").any { p -> p.startsWith(".") } }
        .map { it.relativeTo(dist).invariantSeparatorsPath }
        .filter { it !in skip && !it.endsWith(".map") && !it.endsWith(".LICENSE.txt") && it != "index.html" }
        .sorted().toList()
    val digest = MessageDigest.getInstance("SHA-256")
    (files + "index.html").forEach { digest.update(it.toByteArray()); digest.update(dist.resolve(it).readBytes()) }
    val version = "kreat-" + digest.digest().joinToString("") { "%02x".format(it) }.take(16)
    val list = (listOf("./") + files).joinToString(",\n  ", "[\n  ", "\n]") { "\"$it\"" }
    dist.resolve("sw.js").writeText(
        file("../web/sw-template.js").readText().replace("'__VERSION__'", "'$version'").replace("__FILES__", list)
    )
    logger.lifecycle("Service worker: ${files.size + 1} files, version $version")
}

tasks.named("wasmJsBrowserDistribution") {
    inputs.file("../web/sw-template.js")
    doLast { writeServiceWorker(layout.buildDirectory.dir("dist/wasmJs/productionExecutable").get().asFile) }
}
tasks.named("wasmJsBrowserDevelopmentExecutableDistribution") {
    inputs.file("../web/sw-template.js")
    doLast { writeServiceWorker(layout.buildDirectory.dir("dist/wasmJs/developmentExecutable").get().asFile) }
}
