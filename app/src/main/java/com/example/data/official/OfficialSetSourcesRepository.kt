package com.example.data.official

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.mse.MseSetParser
import com.example.domain.comparison.CardOrigin
import com.example.domain.comparison.ComparableCardRecord
import com.example.domain.comparison.ComparableSet
import com.example.domain.comparison.OfficialSetSummary
import com.example.domain.comparison.SetComparisonEngine
import com.example.domain.comparison.SplitOffSetManifest
import com.example.domain.nexus.NexusCardPackExport
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Real Official Set Puller & Local Real-File Comparison Source Repository.
 *
 * ZERO mock sets or hardcoded dummy card lists.
 * Pulls live official set metadata and real card records (with ETag & local disk caching) from:
 * - Scryfall API (Magic: The Gathering)
 * - YGOPRODeck API (Yu-Gi-Oh!)
 * - Pokémon TCG API (Pokémon TCG)
 * - Open-Resource public JSON URLs
 *
 * Also parses real local files selected via Android SAF (`OpenDocument`):
 * - `.mse-set` ZIP archives (via lossless [MseSetParser])
 * - Yu-Gi-Oh! / ProjectIgnis / DataEditorX SQLite `.cdb` databases (`datas` + `texts` tables)
 * - Card-Forge `cardsfolder/*.txt` scripts
 * - Nexus Hub / MPV JSON Card Packs & ComparableSet JSON files
 *
 * STRICT INVARIANT: All pulled official cards keep `image == null` so no copyrighted artwork
 * is ever auto-embedded into user `.mse-set` workspaces.
 */
class OfficialSetSourcesRepository(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
) {
    private val cacheDir: File
        get() = File(context.filesDir, "official_sets_cache").apply { mkdirs() }

    private val comparableSetAdapter = moshi.adapter(ComparableSet::class.java)
    private val nexusPackAdapter = moshi.adapter(NexusCardPackExport::class.java)
    private val splitOffManifestAdapter = moshi.adapter(SplitOffSetManifest::class.java)

    // =========================================================================
    // 1. MAGIC: THE GATHERING — SCRYFALL API
    // =========================================================================

    suspend fun fetchScryfallSets(): Result<List<OfficialSetSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = cachedHttpGet("https://api.scryfall.com/sets")
            val root = JSONObject(body)
            val data = root.optJSONArray("data") ?: JSONArray()
            val results = mutableListOf<OfficialSetSummary>()
            for (i in 0 until data.length()) {
                val obj = data.optJSONObject(i) ?: continue
                val count = obj.optInt("card_count", 0)
                if (count <= 0) continue
                val code = obj.optString("code", "").trim()
                val name = obj.optString("name", "").trim()
                if (code.isEmpty() || name.isEmpty()) continue
                results += OfficialSetSummary(
                    setId = code,
                    setName = "$name (${code.uppercase()})",
                    source = SOURCE_SCRYFALL,
                    gameId = "magic",
                    cardCount = count,
                    releasedAt = obj.optString("released_at", ""),
                    iconUrl = obj.optString("icon_svg_uri", "").takeIf { it.isNotBlank() },
                    queryParam = code,
                )
            }
            results.sortedByDescending { it.releasedAt }
        }
    }

    suspend fun fetchScryfallSetCards(summary: OfficialSetSummary): Result<ComparableSet> =
        withContext(Dispatchers.IO) {
            runCatching {
                val code = URLEncoder.encode(summary.queryParam.ifBlank { summary.setId }, "UTF-8")
                var nextUrl: String? = "https://api.scryfall.com/cards/search?q=set:$code&order=set"
                val cards = mutableListOf<ComparableCardRecord>()
                var pagesFetched = 0

                while (nextUrl != null && pagesFetched < MAX_SCRYFALL_PAGES) {
                    val body = cachedHttpGet(nextUrl)
                    val root = JSONObject(body)
                    val data = root.optJSONArray("data") ?: JSONArray()
                    for (i in 0 until data.length()) {
                        val obj = data.optJSONObject(i) ?: continue
                        val face0 = obj.optJSONArray("card_faces")?.optJSONObject(0)
                        val id = obj.optString("id", "scryfall_${summary.setId}_$i")
                        val name = obj.optString("name", "").trim()
                        if (name.isEmpty()) continue
                        val manaCost = obj.optString("mana_cost", "")
                            .ifBlank { face0?.optString("mana_cost", "").orEmpty() }
                        val cmc = if (obj.has("cmc") && !obj.isNull("cmc")) {
                            obj.optDouble("cmc", 0.0).toInt()
                        } else {
                            SetComparisonEngine.parseNumericCost(manaCost)
                        }
                        val typeLine = obj.optString("type_line", "")
                            .ifBlank { face0?.optString("type_line", "").orEmpty() }
                        val oracleText = obj.optString("oracle_text", "")
                            .ifBlank { face0?.optString("oracle_text", "").orEmpty() }
                        val power = obj.optString("power", "")
                            .ifBlank { face0?.optString("power", "").orEmpty() }
                        val toughness = obj.optString("toughness", "")
                            .ifBlank { face0?.optString("toughness", "").orEmpty() }
                        val rarity = obj.optString("rarity", "")
                        val colorsArr = obj.optJSONArray("colors") ?: face0?.optJSONArray("colors")
                        val colors = buildList {
                            if (colorsArr != null) {
                                for (c in 0 until colorsArr.length()) {
                                    add(expandMtgColor(colorsArr.optString(c)))
                                }
                            }
                        }
                        val kwArr = obj.optJSONArray("keywords")
                        val keywords = buildList {
                            if (kwArr != null) {
                                for (k in 0 until kwArr.length()) {
                                    kwArr.optString(k).takeIf { it.isNotBlank() }?.let(::add)
                                }
                            }
                        }
                        val extra = linkedMapOf<String, String>()
                        obj.optString("collector_number", "").takeIf { it.isNotBlank() }?.let {
                            extra["card number"] = it
                        }
                        obj.optString("flavor_text", "")
                            .ifBlank { face0?.optString("flavor_text", "").orEmpty() }
                            .takeIf { it.isNotBlank() }
                            ?.let { extra["flavor text"] = it }
                        obj.optString("loyalty", "").takeIf { it.isNotBlank() }?.let {
                            extra["loyalty"] = it
                        }

                        cards += ComparableCardRecord(
                            id = id,
                            passcodeOr21Digit = obj.optString("oracle_id", "").takeIf { it.isNotBlank() },
                            name = name,
                            costOrLevel = manaCost,
                            numericCost = cmc,
                            typeLine = typeLine,
                            powerOrAtk = power,
                            toughnessOrDef = toughness,
                            rulesText = oracleText,
                            keywords = keywords,
                            colorsOrAttributes = colors,
                            rarity = rarity,
                            cardOrigin = CardOrigin.CANON_ORIGINAL.wireValue,
                            extraFields = extra,
                            image = null, // STRICT: never auto-populate copyrighted art
                        )
                    }
                    pagesFetched++
                    nextUrl = if (root.optBoolean("has_more", false)) {
                        root.optString("next_page", "").takeIf { it.startsWith("https://") }
                    } else {
                        null
                    }
                }

                ComparableSet(
                    setId = summary.setId,
                    setName = summary.setName,
                    source = SOURCE_SCRYFALL,
                    gameId = "magic",
                    cards = cards,
                )
            }
        }

    // =========================================================================
    // 2. YU-GI-OH! — YGOPRODECK API
    // =========================================================================

    suspend fun fetchYgoProDeckSets(): Result<List<OfficialSetSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = cachedHttpGet("https://db.ygoprodeck.com/api/v7/cardsets.php")
            val array = JSONArray(body)
            val results = mutableListOf<OfficialSetSummary>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val setName = obj.optString("set_name", "").trim()
                val setCode = obj.optString("set_code", "").trim()
                val count = obj.optInt("num_of_cards", 0)
                if (setName.isEmpty() || count <= 0) continue
                results += OfficialSetSummary(
                    setId = setCode.ifBlank { setName },
                    setName = if (setCode.isNotBlank()) "$setName ($setCode)" else setName,
                    source = SOURCE_YGOPRODECK,
                    gameId = "yugioh",
                    cardCount = count,
                    releasedAt = obj.optString("tcg_date", ""),
                    queryParam = setName,
                )
            }
            results.sortedByDescending { it.releasedAt }
        }
    }

    suspend fun fetchYgoProDeckSetCards(summary: OfficialSetSummary): Result<ComparableSet> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(summary.queryParam.ifBlank { summary.setName }, "UTF-8")
                val body = cachedHttpGet("https://db.ygoprodeck.com/api/v7/cardinfo.php?cardset=$encoded")
                val root = JSONObject(body)
                val data = root.optJSONArray("data") ?: JSONArray()
                val cards = mutableListOf<ComparableCardRecord>()
                for (i in 0 until data.length()) {
                    val obj = data.optJSONObject(i) ?: continue
                    val idNum = obj.optLong("id", 0L)
                    val passcode = if (idNum > 0L) idNum.toString() else null
                    val name = obj.optString("name", "").trim()
                    if (name.isEmpty()) continue
                    val type = obj.optString("humanReadableCardType", "").ifBlank { obj.optString("type", "") }
                    val race = obj.optString("race", "")
                    val attr = obj.optString("attribute", "")
                    val levelVal = when {
                        obj.has("level") && !obj.isNull("level") -> obj.optInt("level", 0)
                        obj.has("linkval") && !obj.isNull("linkval") -> obj.optInt("linkval", 0)
                        else -> null
                    }
                    val atk = if (obj.has("atk") && !obj.isNull("atk")) obj.optInt("atk").toString() else ""
                    val def = if (obj.has("def") && !obj.isNull("def")) obj.optInt("def").toString() else ""
                    val desc = obj.optString("desc", "")
                    val extra = linkedMapOf<String, String>()
                    if (attr.isNotBlank()) extra["attribute"] = attr
                    if (race.isNotBlank()) extra["race"] = race
                    if (levelVal != null) extra["level"] = levelVal.toString()
                    if (atk.isNotBlank()) extra["atk"] = atk
                    if (def.isNotBlank()) extra["def"] = def
                    obj.optString("archetype", "").takeIf { it.isNotBlank() }?.let { extra["archetype"] = it }

                    cards += ComparableCardRecord(
                        id = passcode ?: "ygo_${summary.setId}_$i",
                        passcodeOr21Digit = passcode,
                        name = name,
                        costOrLevel = levelVal?.let { "LV $it" } ?: "",
                        numericCost = levelVal,
                        typeLine = if (race.isNotBlank()) "$type — $race" else type,
                        superType = type,
                        subType = race,
                        powerOrAtk = atk,
                        toughnessOrDef = def,
                        rulesText = desc,
                        colorsOrAttributes = listOfNotNull(attr.takeIf { it.isNotBlank() }),
                        cardOrigin = CardOrigin.CANON_ORIGINAL.wireValue,
                        extraFields = extra,
                        image = null,
                    )
                }
                ComparableSet(
                    setId = summary.setId,
                    setName = summary.setName,
                    source = SOURCE_YGOPRODECK,
                    gameId = "yugioh",
                    cards = cards,
                )
            }
        }

    // =========================================================================
    // 3. POKÉMON TCG — POKÉMON TCG API
    // =========================================================================

    suspend fun fetchPokemonTcgSets(): Result<List<OfficialSetSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = cachedHttpGet("https://api.pokemontcg.io/v2/sets?pageSize=100&orderBy=-releaseDate")
            val root = JSONObject(body)
            val data = root.optJSONArray("data") ?: JSONArray()
            val results = mutableListOf<OfficialSetSummary>()
            for (i in 0 until data.length()) {
                val obj = data.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").trim()
                val name = obj.optString("name", "").trim()
                if (id.isEmpty() || name.isEmpty()) continue
                val series = obj.optString("series", "")
                results += OfficialSetSummary(
                    setId = id,
                    setName = if (series.isNotBlank()) "$name ($series)" else name,
                    source = SOURCE_POKEMON_TCG,
                    gameId = "pokemon",
                    cardCount = obj.optInt("total", obj.optInt("printedTotal", 0)),
                    releasedAt = obj.optString("releaseDate", ""),
                    queryParam = id,
                )
            }
            results
        }
    }

    suspend fun fetchPokemonTcgSetCards(summary: OfficialSetSummary): Result<ComparableSet> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encodedId = URLEncoder.encode(summary.queryParam.ifBlank { summary.setId }, "UTF-8")
                val body = cachedHttpGet("https://api.pokemontcg.io/v2/cards?q=set.id:$encodedId&pageSize=250")
                val root = JSONObject(body)
                val data = root.optJSONArray("data") ?: JSONArray()
                val cards = mutableListOf<ComparableCardRecord>()
                for (i in 0 until data.length()) {
                    val obj = data.optJSONObject(i) ?: continue
                    val id = obj.optString("id", "ptcg_${summary.setId}_$i")
                    val name = obj.optString("name", "").trim()
                    if (name.isEmpty()) continue
                    val supertype = obj.optString("supertype", "")
                    val subtypes = jsonStringList(obj.optJSONArray("subtypes"))
                    val types = jsonStringList(obj.optJSONArray("types"))
                    val hp = obj.optString("hp", "")
                    val retreatCost = obj.optInt("convertedRetreatCost", 0)
                    val rulesBuilder = StringBuilder()
                    jsonStringList(obj.optJSONArray("rules")).forEach { rulesBuilder.appendLine(it) }
                    val abilities = obj.optJSONArray("abilities")
                    if (abilities != null) {
                        for (a in 0 until abilities.length()) {
                            val ab = abilities.optJSONObject(a) ?: continue
                            rulesBuilder.appendLine("[${ab.optString("type", "Ability")}] ${ab.optString("name")}: ${ab.optString("text")}")
                        }
                    }
                    val attacks = obj.optJSONArray("attacks")
                    var maxEnergyCost: Int? = null
                    if (attacks != null) {
                        for (a in 0 until attacks.length()) {
                            val atkObj = attacks.optJSONObject(a) ?: continue
                            val costCount = atkObj.optInt("convertedEnergyCost", 0)
                            if (maxEnergyCost == null || costCount > maxEnergyCost) maxEnergyCost = costCount
                            val costStr = jsonStringList(atkObj.optJSONArray("cost")).joinToString("")
                            val dmg = atkObj.optString("damage", "")
                            rulesBuilder.appendLine("[$costStr] ${atkObj.optString("name")} ($dmg): ${atkObj.optString("text")}")
                        }
                    }
                    val extra = linkedMapOf<String, String>()
                    if (hp.isNotBlank()) extra["hp"] = hp
                    if (retreatCost > 0) extra["retreat cost"] = retreatCost.toString()
                    obj.optString("number", "").takeIf { it.isNotBlank() }?.let { extra["card number"] = it }

                    cards += ComparableCardRecord(
                        id = id,
                        name = name,
                        costOrLevel = maxEnergyCost?.let { "$it Energy" } ?: "",
                        numericCost = maxEnergyCost,
                        typeLine = if (subtypes.isNotEmpty()) "$supertype — ${subtypes.joinToString(" ")}" else supertype,
                        superType = supertype,
                        subType = subtypes.joinToString(" "),
                        powerOrAtk = hp,
                        toughnessOrDef = if (retreatCost > 0) retreatCost.toString() else "",
                        rulesText = rulesBuilder.toString().trim(),
                        colorsOrAttributes = types,
                        rarity = obj.optString("rarity", ""),
                        cardOrigin = CardOrigin.CANON_ORIGINAL.wireValue,
                        extraFields = extra,
                        image = null,
                    )
                }
                ComparableSet(
                    setId = summary.setId,
                    setName = summary.setName,
                    source = SOURCE_POKEMON_TCG,
                    gameId = "pokemon",
                    cards = cards,
                )
            }
        }

    // =========================================================================
    // 4. OPEN-RESOURCE PUBLIC SET JSON URL
    // =========================================================================

    suspend fun fetchOpenResourceSetFromUrl(
        url: String,
        displayName: String = "Open-Resource Set",
    ): Result<ComparableSet> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = url.trim()
            require(trimmed.startsWith("https://")) {
                "Open-Resource set URL must use HTTPS."
            }
            val rawJson = cachedHttpGet(trimmed)
            parseComparableJsonString(rawJson, fallbackName = displayName, source = SOURCE_OPEN_URL)
        }
    }

    // =========================================================================
    // 5. LOCAL REAL CARD FILES VIA ANDROID SAF (.mse-set, .cdb, .txt, .json)
    // =========================================================================

    suspend fun loadLocalComparableSetFromUri(uri: Uri): Result<ComparableSet> =
        withContext(Dispatchers.IO) {
            runCatching {
                val displayName = resolveDisplayName(uri) ?: "local_set"
                val lower = displayName.lowercase()
                when {
                    lower.endsWith(".mse-set") || lower.endsWith(".zip") -> parseLocalMseSet(uri, displayName)
                    lower.endsWith(".cdb") || lower.endsWith(".sqlite") || lower.endsWith(".db") ->
                        parseLocalYgoCdb(uri, displayName)
                    lower.endsWith(".txt") -> parseLocalForgeTxt(uri, displayName)
                    lower.endsWith(".json") -> parseLocalJsonSet(uri, displayName)
                    else -> {
                        // Sniff content by reading prefix
                        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IllegalStateException("Cannot read selected file.")
                        when {
                            rawBytes.size >= 16 && rawBytes.copyOfRange(0, 15).toString(Charsets.US_ASCII) == "SQLite format 3" ->
                                parseLocalYgoCdbBytes(rawBytes, displayName)
                            rawBytes.size >= 4 && rawBytes[0] == 'P'.code.toByte() && rawBytes[1] == 'K'.code.toByte() ->
                                parseLocalMseSetBytes(rawBytes, displayName)
                            rawBytes.toString(Charsets.UTF_8).trimStart().startsWith("{") ||
                                rawBytes.toString(Charsets.UTF_8).trimStart().startsWith("[") ->
                                parseComparableJsonString(rawBytes.toString(Charsets.UTF_8), displayName, SOURCE_NEXUS_PACK)
                            else -> parseForgeTextContent(rawBytes.toString(Charsets.UTF_8), displayName)
                        }
                    }
                }
            }
        }

    private fun parseLocalMseSet(uri: Uri, displayName: String): ComparableSet {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Unable to open .mse-set stream.")
        val archive = stream.use { MseSetParser.readArchive(it, displayName = displayName, sourceUri = uri.toString()) }
        val manifest = archive.embeddedBlobs[SetComparisonEngine.SPLIT_OFF_MANIFEST_BLOB_NAME]
            ?.let { runCatching { splitOffManifestAdapter.fromJson(it.toString(Charsets.UTF_8)) }.getOrNull() }
        return SetComparisonEngine.fromMseSetArchive(archive, manifest)
    }

    private fun parseLocalMseSetBytes(bytes: ByteArray, displayName: String): ComparableSet {
        val archive = bytes.inputStream().use { MseSetParser.readArchive(it, displayName = displayName) }
        val manifest = archive.embeddedBlobs[SetComparisonEngine.SPLIT_OFF_MANIFEST_BLOB_NAME]
            ?.let { runCatching { splitOffManifestAdapter.fromJson(it.toString(Charsets.UTF_8)) }.getOrNull() }
        return SetComparisonEngine.fromMseSetArchive(archive, manifest)
    }

    private fun parseLocalYgoCdb(uri: Uri, displayName: String): ComparableSet {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Unable to open SQLite .cdb stream.")
        return parseLocalYgoCdbBytes(bytes, displayName)
    }

    private fun parseLocalYgoCdbBytes(bytes: ByteArray, displayName: String): ComparableSet {
        val tempFile = File.createTempFile("ygo_cdb_", ".cdb", context.cacheDir)
        try {
            tempFile.writeBytes(bytes)
            val db = SQLiteDatabase.openDatabase(tempFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val cards = mutableListOf<ComparableCardRecord>()
            db.use { sqlite ->
                val sql = """
                    SELECT d.id, d.ot, d.alias, d.setcode, d.type, d.atk, d.def, d.level, d.race, d.attribute, d.category, t.name, t.desc
                    FROM datas d
                    INNER JOIN texts t ON d.id = t.id
                    ORDER BY d.id ASC
                """.trimIndent()
                sqlite.rawQuery(sql, null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val ot = cursor.getInt(1)
                        val alias = cursor.getLong(2)
                        val setcode = cursor.getLong(3)
                        val typeBits = cursor.getInt(4)
                        val atkVal = cursor.getInt(5)
                        val defVal = cursor.getInt(6)
                        val levelRaw = cursor.getInt(7)
                        val raceBits = cursor.getLong(8)
                        val attrBits = cursor.getInt(9)
                        val categoryBits = cursor.getLong(10)
                        val name = cursor.getString(11).orEmpty().trim()
                        val desc = cursor.getString(12).orEmpty().trim()
                        if (name.isEmpty()) continue

                        val level = levelRaw and 0xFF
                        val isMonster = (typeBits and 0x1) != 0
                        val typeLabel = decodeYgoType(typeBits)
                        val attrLabel = decodeYgoAttribute(attrBits)
                        val raceLabel = decodeYgoRace(raceBits)
                        val atkStr = if (isMonster) (if (atkVal < 0) "?" else atkVal.toString()) else ""
                        val defStr = if (isMonster && (typeBits and 0x4000000) == 0) {
                            if (defVal < 0) "?" else defVal.toString()
                        } else {
                            ""
                        }

                        val extra = linkedMapOf<String, String>()
                        extra["ot"] = ot.toString()
                        if (alias > 0L) extra["alias"] = alias.toString()
                        if (setcode != 0L) extra["setcode"] = "0x${setcode.toString(16)}"
                        if (categoryBits != 0L) extra["category"] = "0x${categoryBits.toString(16)}"
                        if (attrLabel.isNotBlank()) extra["attribute"] = attrLabel
                        if (raceLabel.isNotBlank()) extra["race"] = raceLabel
                        if (isMonster && level > 0) extra["level"] = level.toString()
                        if (atkStr.isNotBlank()) extra["atk"] = atkStr
                        if (defStr.isNotBlank()) extra["def"] = defStr

                        cards += ComparableCardRecord(
                            id = id.toString(),
                            passcodeOr21Digit = id.toString(),
                            name = name,
                            costOrLevel = if (isMonster && level > 0) "LV $level" else "",
                            numericCost = if (isMonster && level > 0) level else null,
                            typeLine = if (raceLabel.isNotBlank()) "$typeLabel — $raceLabel" else typeLabel,
                            superType = typeLabel,
                            subType = raceLabel,
                            powerOrAtk = atkStr,
                            toughnessOrDef = defStr,
                            rulesText = desc,
                            colorsOrAttributes = listOfNotNull(attrLabel.takeIf { it.isNotBlank() }),
                            cardOrigin = CardOrigin.CANON_ORIGINAL.wireValue,
                            extraFields = extra,
                            image = null,
                        )
                    }
                }
            }
            return ComparableSet(
                setId = displayName.substringBeforeLast('.'),
                setName = displayName,
                source = SOURCE_LOCAL_CDB,
                gameId = "yugioh",
                cards = cards,
            )
        } finally {
            tempFile.delete()
        }
    }

    private fun parseLocalForgeTxt(uri: Uri, displayName: String): ComparableSet {
        val rawText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("Unable to read Card-Forge .txt file.")
        return parseForgeTextContent(rawText, displayName)
    }

    private fun parseForgeTextContent(rawText: String, displayName: String): ComparableSet {
        // Support either a single card file or multiple cards separated by blank lines starting with Name:
        val chunks = rawText.replace("\r\n", "\n")
            .split(Regex("(?=^Name:)", RegexOption.MULTILINE))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val cards = chunks.mapNotNull { chunk ->
            SetComparisonEngine.parseForgeCardText(chunk, displayName)
        }
        require(cards.isNotEmpty()) {
            "No valid Card-Forge entries (Name:, ManaCost:, Types:, Oracle:) found in $displayName."
        }
        return ComparableSet(
            setId = displayName.substringBeforeLast('.'),
            setName = displayName,
            source = SOURCE_FORGE_TXT,
            gameId = "magic",
            cards = cards,
        )
    }

    private fun parseLocalJsonSet(uri: Uri, displayName: String): ComparableSet {
        val rawJson = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("Unable to read JSON file.")
        return parseComparableJsonString(rawJson, fallbackName = displayName, source = SOURCE_NEXUS_PACK)
    }

    private fun parseComparableJsonString(
        rawJson: String,
        fallbackName: String,
        source: String,
    ): ComparableSet {
        // 1. Direct ComparableSet JSON
        runCatching { comparableSetAdapter.fromJson(rawJson) }.getOrNull()?.let { parsed ->
            if (parsed.cards.isNotEmpty()) {
                return parsed.copy(
                    cards = parsed.cards.map { it.copy(image = null) },
                )
            }
        }
        // 2. NexusCardPackExport JSON
        runCatching { nexusPackAdapter.fromJson(rawJson) }.getOrNull()?.let { pack ->
            if (pack.cards.isNotEmpty()) {
                return ComparableSet(
                    setId = pack.packId,
                    setName = pack.packName.ifBlank { fallbackName },
                    source = SOURCE_NEXUS_PACK,
                    gameId = pack.targetClientId,
                    cards = pack.cards.map { c ->
                        ComparableCardRecord(
                            id = c.cardId,
                            passcodeOr21Digit = c.register21Digit,
                            name = c.cardName,
                            costOrLevel = c.colorPiePips.joinToString(""),
                            numericCost = c.colorPiePips.size.takeIf { it > 0 },
                            typeLine = c.cardType,
                            superType = c.cardType,
                            rulesText = c.rulesText,
                            colorsOrAttributes = c.colorPiePips,
                            cardOrigin = CardOrigin.CANON_ORIGINAL.wireValue,
                            extraFields = c.codingVariables,
                            image = null,
                        )
                    },
                )
            }
        }
        // 3. Generic JSON object with `cards` or `data` array
        val root = JSONObject(rawJson)
        val arr = root.optJSONArray("cards") ?: root.optJSONArray("data")
            ?: throw IllegalArgumentException("JSON does not contain a valid ComparableSet, NexusCardPackExport, or cards/data array.")
        val cards = mutableListOf<ComparableCardRecord>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name", obj.optString("cardName", "")).trim()
            if (name.isEmpty()) continue
            val cost = obj.optString("costOrLevel", obj.optString("mana_cost", obj.optString("cost", "")))
            cards += ComparableCardRecord(
                id = obj.optString("id", obj.optString("cardId", "json_${i + 1}")),
                passcodeOr21Digit = obj.optString("passcode", obj.optString("register21Digit", "")).takeIf { it.isNotBlank() },
                name = name,
                costOrLevel = cost,
                numericCost = SetComparisonEngine.parseNumericCost(cost),
                typeLine = obj.optString("typeLine", obj.optString("type_line", obj.optString("type", ""))),
                powerOrAtk = obj.optString("power", obj.optString("atk", "")),
                toughnessOrDef = obj.optString("toughness", obj.optString("def", "")),
                rulesText = obj.optString("rulesText", obj.optString("oracle_text", obj.optString("desc", ""))),
                cardOrigin = CardOrigin.CANON_ORIGINAL.wireValue,
                image = null,
            )
        }
        return ComparableSet(
            setId = root.optString("setId", fallbackName.substringBeforeLast('.')),
            setName = root.optString("setName", root.optString("name", fallbackName)),
            source = source,
            gameId = root.optString("gameId", "custom"),
            cards = cards,
        )
    }

    // =========================================================================
    // ETAG + LOCAL DISK CACHE HTTP HELPER
    // =========================================================================

    private fun cachedHttpGet(url: String): String {
        val key = sha256Hex(url).take(24)
        val bodyFile = File(cacheDir, "$key.json")
        val etagFile = File(cacheDir, "$key.etag")

        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)

        val savedEtag = if (etagFile.exists() && bodyFile.exists()) etagFile.readText().trim() else ""
        if (savedEtag.isNotEmpty()) {
            builder.header("If-None-Match", savedEtag)
        }

        return try {
            okHttpClient.newCall(builder.build()).execute().use { response ->
                when (response.code) {
                    304 -> {
                        if (bodyFile.exists()) {
                            bodyFile.readText()
                        } else {
                            throw IllegalStateException("HTTP 304 received without cached payload for $url")
                        }
                    }
                    in 200..299 -> {
                        val payload = response.body?.string().orEmpty()
                        if (payload.isNotBlank()) {
                            bodyFile.writeText(payload)
                            response.header("ETag")?.takeIf { it.isNotBlank() }?.let { etag ->
                                etagFile.writeText(etag)
                            }
                        }
                        payload
                    }
                    else -> {
                        if (bodyFile.exists()) {
                            bodyFile.readText()
                        } else {
                            throw IllegalStateException("HTTP ${response.code} from $url")
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            if (bodyFile.exists()) {
                bodyFile.readText()
            } else {
                throw ex
            }
        }
    }

    private fun expandMtgColor(symbol: String): String = when (symbol.uppercase()) {
        "W" -> "White"
        "U" -> "Blue"
        "B" -> "Black"
        "R" -> "Red"
        "G" -> "Green"
        else -> symbol
    }

    private fun decodeYgoType(bits: Int): String = buildList {
        if ((bits and 0x1) != 0) add("Monster")
        if ((bits and 0x2) != 0) add("Spell")
        if ((bits and 0x4) != 0) add("Trap")
        if ((bits and 0x10) != 0) add("Normal")
        if ((bits and 0x20) != 0) add("Effect")
        if ((bits and 0x40) != 0) add("Fusion")
        if ((bits and 0x80) != 0) add("Ritual")
        if ((bits and 0x1000) != 0) add("Tuner")
        if ((bits and 0x2000) != 0) add("Synchro")
        if ((bits and 0x800000) != 0) add("Xyz")
        if ((bits and 0x1000000) != 0) add("Pendulum")
        if ((bits and 0x4000000) != 0) add("Link")
        if ((bits and 0x10000) != 0) add("Quick-Play")
        if ((bits and 0x20000) != 0) add("Continuous")
        if ((bits and 0x40000) != 0) add("Equip")
        if ((bits and 0x80000) != 0) add("Field")
        if ((bits and 0x100000) != 0) add("Counter")
    }.joinToString(" ").ifBlank { "Card" }

    private fun decodeYgoAttribute(bits: Int): String = when (bits) {
        0x01 -> "EARTH"
        0x02 -> "WATER"
        0x04 -> "FIRE"
        0x08 -> "WIND"
        0x10 -> "LIGHT"
        0x20 -> "DARK"
        0x40 -> "DIVINE"
        else -> ""
    }

    private fun decodeYgoRace(bits: Long): String = when (bits) {
        0x1L -> "Warrior"
        0x2L -> "Spellcaster"
        0x4L -> "Fairy"
        0x8L -> "Fiend"
        0x10L -> "Zombie"
        0x20L -> "Machine"
        0x40L -> "Aqua"
        0x80L -> "Pyro"
        0x100L -> "Rock"
        0x200L -> "Winged Beast"
        0x400L -> "Plant"
        0x800L -> "Insect"
        0x1000L -> "Thunder"
        0x2000L -> "Dragon"
        0x4000L -> "Beast"
        0x8000L -> "Beast-Warrior"
        0x10000L -> "Dinosaur"
        0x20000L -> "Fish"
        0x40000L -> "Sea Serpent"
        0x80000L -> "Reptile"
        0x100000L -> "Psychic"
        0x200000L -> "Divine-Beast"
        0x400000L -> "Creator God"
        0x800000L -> "Wyrm"
        0x1000000L -> "Cyberse"
        0x2000000L -> "Illusion"
        else -> ""
    }

    private fun jsonStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    companion object {
        const val SOURCE_SCRYFALL = "scryfall"
        const val SOURCE_YGOPRODECK = "ygoprodeck"
        const val SOURCE_POKEMON_TCG = "pokemontcg"
        const val SOURCE_LOCAL_CDB = "local_cdb"
        const val SOURCE_LOCAL_MSE_SET = "local_mse_set"
        const val SOURCE_FORGE_TXT = "forge_txt"
        const val SOURCE_NEXUS_PACK = "nexus_pack"
        const val SOURCE_OPEN_URL = "open_url"

        private const val MAX_SCRYFALL_PAGES = 4
        private const val USER_AGENT =
            "TcgStudioAndroid/8.0 (Summer-Warrior/MPV-TCG-Card-Making-Software-Updated)"
    }
}
