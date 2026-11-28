package com.example

import com.example.data.mse.MseStyleParser
import com.example.data.upstream.BetaFeedProtocol
import com.example.data.upstream.BetaFrameCategory
import com.example.data.upstream.BetaFrameRadarRepository
import com.example.data.upstream.BetaFrameSourceFeed
import com.example.data.upstream.DiscoveredBetaFrameItem
import com.example.data.upstream.FrameOriginTrust
import com.example.data.upstream.UniqueFrameUseKind
import com.example.ui.StudioViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BetaFrameRadarUnitTest {

    @Test
    fun testNonMseOfficialForkedAndFanFramesStayPermanentlyLabeledBeta() {
        val officialMse = DiscoveredBetaFrameItem(
            id = "mse_official_1",
            displayName = "Magic M15 Mainframe",
            folderName = "magic-m15-altered.mse-style",
            gameId = "magic",
            category = BetaFrameCategory.CURRENT_GAME,
            originTrust = FrameOriginTrust.CANONICAL_OFFICIAL_MSE,
            sourceFeedLabel = "MagicSetEditor2",
            sourceWebUrl = "https://github.com/G-e-n-e-v-e-n-s-i-S/MagicSetEditor2",
        )
        assertFalse(officialMse.isAlwaysBeta)
        assertEquals("OFFICIAL MSE", officialMse.effectiveStatusBadge())

        // Non-MSE official, Forked repo, Fan YGO custom, and Fan Art style MUST ALWAYS stay labeled BETA
        val nonMseOfficial = officialMse.copy(
            id = "non_mse_1",
            originTrust = FrameOriginTrust.NON_MSE_OFFICIAL,
            isAlwaysBeta = false, // even if someone attempts to clear it, originTrust.alwaysBeta enforces BETA
        )
        val forkedRepo = officialMse.copy(
            id = "fork_1",
            originTrust = FrameOriginTrust.FORKED_REPO,
            isAlwaysBeta = false,
        )
        val fanYgo = officialMse.copy(
            id = "fan_ygo_1",
            category = BetaFrameCategory.FAN_YGO_CUSTOM,
            originTrust = FrameOriginTrust.FAN_YGO_CUSTOM,
            isAlwaysBeta = false,
        )
        val fanArt = officialMse.copy(
            id = "fan_art_1",
            category = BetaFrameCategory.FAN_STYLE,
            originTrust = FrameOriginTrust.FAN_ART_STYLE,
            isAlwaysBeta = false,
        )

        listOf(nonMseOfficial, forkedRepo, fanYgo, fanArt).forEach { item ->
            assertTrue(
                "Origin ${item.originTrust} must permanently display BETA in badge",
                item.effectiveStatusBadge().contains("BETA"),
            )
        }

        // Verify automatic fork classifier forces FORKED_REPO and rejects non-canonical repos as NON_MSE_OFFICIAL
        assertEquals(
            FrameOriginTrust.CANONICAL_OFFICIAL_MSE,
            BetaFrameRadarRepository.classifyRepoOriginTrust(
                owner = "G-e-n-e-v-e-n-s-i-S",
                repo = "MagicSetEditor2",
                isFork = false,
                defaultTrust = FrameOriginTrust.CANONICAL_OFFICIAL_MSE,
            ),
        )
        assertEquals(
            FrameOriginTrust.FORKED_REPO,
            BetaFrameRadarRepository.classifyRepoOriginTrust(
                owner = "Lyris12",
                repo = "DataEditorX",
                isFork = true,
                defaultTrust = FrameOriginTrust.NON_MSE_OFFICIAL,
            ),
        )
        assertEquals(
            FrameOriginTrust.NON_MSE_OFFICIAL,
            BetaFrameRadarRepository.classifyRepoOriginTrust(
                owner = "MagicSetEditorPacks",
                repo = "Full-Non-Magic-Pack",
                isFork = false,
                defaultTrust = FrameOriginTrust.CANONICAL_OFFICIAL_MSE,
            ),
        )

        // Verify all 16 built-in public feeds are registered
        assertEquals(16, BetaFrameRadarRepository.builtInFeeds().size)
    }

    @Test
    fun testRssAndAtomXmlParserHandlesDeviantArtItchIoAndRedditFeeds() {
        val deviantArtFeed = BetaFrameSourceFeed(
            id = "da_ygo_test",
            label = "DeviantArt — Fan YGO Custom Monster Frames",
            protocol = BetaFeedProtocol.DEVIANTART_RSS,
            category = BetaFrameCategory.FAN_YGO_CUSTOM,
            originTrust = FrameOriginTrust.FAN_YGO_CUSTOM,
            feedUrl = "https://backend.deviantart.com/rss.xml?type=deviation&q=yugioh",
        )
        val deviantArtRss = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
              <channel>
                <title>DeviantArt YGO Templates</title>
                <item>
                  <title>Yu-Gi-Oh Dark Synchro Series 10 Blank Card Template</title>
                  <link>https://www.deviantart.com/duelartist/art/dark-synchro-template-998877</link>
                  <guid>https://www.deviantart.com/duelartist/art/dark-synchro-template-998877</guid>
                  <media:credit role="author">DuelArtist99</media:credit>
                  <media:content url="https://images-wixmp.example/dark-synchro-421x614.png" medium="image"/>
                  <description>Negative level Dark Synchro custom monster card frame template for YGO.</description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val daItems = BetaFrameRadarRepository.parseRssOrAtomXml(deviantArtRss, deviantArtFeed)
        assertEquals(1, daItems.size)
        val daFirst = daItems.first()
        assertEquals("DuelArtist99", daFirst.authorCredit)
        assertEquals("https://www.deviantart.com/duelartist/art/dark-synchro-template-998877", daFirst.sourceWebUrl)
        assertEquals("https://images-wixmp.example/dark-synchro-421x614.png", daFirst.previewThumbnailUrl)
        assertEquals(UniqueFrameUseKind.YGO_DARK_SYNCHRO, daFirst.uniqueUseAssessment?.kind)
        assertTrue(daFirst.isEligibleForUniqueNoTemplateSynthesis())

        val itchFeed = BetaFrameSourceFeed(
            id = "itch_test",
            label = "Itch.io — TCG Template Assets",
            protocol = BetaFeedProtocol.GENERIC_RSS,
            category = BetaFrameCategory.FAN_STYLE,
            originTrust = FrameOriginTrust.FAN_ART_STYLE,
            feedUrl = "https://itch.io/game-assets/tag-tcg.xml",
        )
        val itchRss = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel>
                <item>
                  <title>Modular Sci-Fi TCG Card Frame Template Pack</title>
                  <link>https://pixelcraft.itch.io/scifi-tcg-card-template</link>
                  <guid>itch-scifi-001</guid>
                  <dc:creator>PixelCraftStudio</dc:creator>
                  <description>Blank card template with energy cost, shield stat, and ability text zones for custom TCG games.</description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val itchItems = BetaFrameRadarRepository.parseRssOrAtomXml(itchRss, itchFeed)
        assertEquals(1, itchItems.size)
        assertEquals("PixelCraftStudio", itchItems.first().authorCredit)
        assertEquals(UniqueFrameUseKind.UNIVERSAL_UNIQUE_TCG_FRAME, itchItems.first().uniqueUseAssessment?.kind)

        val redditFeed = BetaFrameSourceFeed(
            id = "reddit_ygo_test",
            label = "Reddit — r/customyugioh Templates & New Frames",
            protocol = BetaFeedProtocol.GENERIC_RSS,
            category = BetaFrameCategory.FAN_YGO_CUSTOM,
            originTrust = FrameOriginTrust.FAN_YGO_CUSTOM,
            feedUrl = "https://www.reddit.com/r/customyugioh/search.rss?q=frame",
        )
        val redditAtom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>customyugioh search</title>
              <entry>
                <id>t3_abc123</id>
                <title>[Template] Link-Pendulum Monster Card Frame v2</title>
                <link rel="alternate" href="https://www.reddit.com/r/customyugioh/comments/abc123/link_pendulum_frame/"/>
                <author><name>/u/PendulumSmith</name></author>
                <updated>2026-11-28T10:00:00+00:00</updated>
                <content type="html">&lt;p&gt;Custom YGO Link-Pendulum hybrid frame with Link arrows and scales.&lt;/p&gt;</content>
              </entry>
            </feed>
        """.trimIndent()

        val redditItems = BetaFrameRadarRepository.parseRssOrAtomXml(redditAtom, redditFeed)
        assertEquals(1, redditItems.size)
        assertEquals("PendulumSmith", redditItems.first().authorCredit)
        assertEquals("https://www.reddit.com/r/customyugioh/comments/abc123/link_pendulum_frame/", redditItems.first().sourceWebUrl)
        assertEquals(UniqueFrameUseKind.YGO_LINK_PENDULUM, redditItems.first().uniqueUseAssessment?.kind)
    }

    @Test
    fun testUniqueUseDetectorTargetsNewYgoMonsterFramesAndRejectsGenericNonFrameArt() {
        fun makeCandidate(title: String, desc: String, gameId: String = "yugioh"): DiscoveredBetaFrameItem =
            DiscoveredBetaFrameItem(
                id = "cand_${title.hashCode()}",
                displayName = title,
                folderName = "cand.mse-style",
                gameId = gameId,
                category = BetaFrameCategory.FAN_YGO_CUSTOM,
                originTrust = FrameOriginTrust.FAN_YGO_CUSTOM,
                sourceFeedLabel = "DeviantArt — Fan YGO Custom Monster Frames",
                sourceWebUrl = "https://www.deviantart.com/creator/art/example",
                descriptionSnippet = desc,
                hasUpstreamTemplateFile = false,
            )

        val darkSynchro = BetaFrameRadarRepository.assessUniqueFrameUse(
            makeCandidate("Dark Synchro Monster Frame", "Negative Level stars and Dark Tuner summoning")
        )
        assertTrue(darkSynchro.hasClearUniqueUse)
        assertEquals(UniqueFrameUseKind.YGO_DARK_SYNCHRO, darkSynchro.kind)

        val linkPendulum = BetaFrameRadarRepository.assessUniqueFrameUse(
            makeCandidate("Link-Pendulum Hybrid Monster Frame", "8-way link arrows plus pendulum scale box")
        )
        assertTrue(linkPendulum.hasClearUniqueUse)
        assertEquals(UniqueFrameUseKind.YGO_LINK_PENDULUM, linkPendulum.kind)

        val maximum = BetaFrameRadarRepository.assessUniqueFrameUse(
            makeCandidate("Rush Duel Maximum Monster Template", "3-card Maximum Mode with MAXIMUM ATK")
        )
        assertTrue(maximum.hasClearUniqueUse)
        assertEquals(UniqueFrameUseKind.YGO_MAXIMUM_MONSTER, maximum.kind)

        val novelYgo = BetaFrameRadarRepository.assessUniqueFrameUse(
            makeCandidate("YGO Chrono Evolute Custom Summon Monster", "New custom summon mechanic for fan duels")
        )
        assertTrue(novelYgo.hasClearUniqueUse)
        assertEquals(UniqueFrameUseKind.YGO_NOVEL_MONSTER_MECHANIC, novelYgo.kind)

        // Playmats, sleeves, wallpapers, and memes MUST be rejected
        val playmat = BetaFrameRadarRepository.assessUniqueFrameUse(
            makeCandidate("Blue-Eyes Custom Playmat Design", "Printable rubber playmat and matching card sleeves", "custom-tcg")
        )
        assertFalse("Playmat must NOT qualify for auto-synthesis", playmat.hasClearUniqueUse)
        assertEquals(UniqueFrameUseKind.NONE_OR_GENERIC_ART, playmat.kind)

        val wallpaper = BetaFrameRadarRepository.assessUniqueFrameUse(
            makeCandidate("Dark Magician 4K Desktop Wallpaper", "Fanart banner wallpaper for desktop background", "custom-tcg")
        )
        assertFalse("Wallpaper must NOT qualify for auto-synthesis", wallpaper.hasClearUniqueUse)
        assertEquals(UniqueFrameUseKind.NONE_OR_GENERIC_ART, wallpaper.kind)
    }

    @Test
    fun testSynthesizedUniqueYgoBetaCodeEmitsMechanicFieldsAndParsesCleanly() {
        val linkPendItem = DiscoveredBetaFrameItem(
            id = "synth_ygo_lp_1",
            displayName = "Apex Link-Pendulum Frame",
            folderName = "yugioh-link-pendulum.mse-style",
            gameId = "yugioh",
            category = BetaFrameCategory.FAN_YGO_CUSTOM,
            originTrust = FrameOriginTrust.FAN_YGO_CUSTOM,
            sourceFeedLabel = "Reddit — r/customyugioh Templates & New Frames",
            authorCredit = "ArcCreator",
            sourceWebUrl = "https://www.reddit.com/r/customyugioh/comments/xyz/apex_link_pendulum/",
            descriptionSnippet = "Custom Link-Pendulum hybrid monster frame",
            hasUpstreamTemplateFile = false,
        )
        val assessment = BetaFrameRadarRepository.assessUniqueFrameUse(linkPendItem)
        assertEquals(UniqueFrameUseKind.YGO_LINK_PENDULUM, assessment.kind)

        val (styleCode, gameSnippet) = BetaFrameRadarRepository.synthesizeUniqueUseBetaCode(
            item = linkPendItem,
            assessment = assessment,
        )

        assertTrue(styleCode.contains("link markers:"))
        assertTrue(styleCode.contains("link rating:"))
        assertTrue(styleCode.contains("pendulum left scale:"))
        assertTrue(styleCode.contains("pendulum right scale:"))
        assertTrue(styleCode.contains("pendulum text:"))
        assertTrue(gameSnippet.contains("name: link markers"))
        assertTrue(gameSnippet.contains("name: pendulum text"))

        val parsed = MseStyleParser.parseStyleText(styleCode, linkPendItem.folderName)
        assertEquals(421, parsed.cardWidthPx)
        assertEquals(614, parsed.cardHeightPx)
        assertTrue("Synthesized style must pass composite geometry validation", parsed.hasValidCompositeGeometry())
        val hitboxNames = parsed.hitboxes.map { it.field }.toSet()
        assertTrue(hitboxNames.contains("link markers"))
        assertTrue(hitboxNames.contains("link rating"))
        assertTrue(hitboxNames.contains("pendulum text"))
        assertTrue(hitboxNames.contains("image"))
        assertTrue(hitboxNames.contains("name"))
    }

    @Test
    fun testAllFanMadeFramesEmbedCodeOnlySourceTrailForPermissionAndRules() {
        // 1. Unsourced fan-made frame MUST fail hasRequiredFanSources() and refuse code generation
        val unsourcedFan = DiscoveredBetaFrameItem(
            id = "unsourced_fan",
            displayName = "Unsourced Dark Synchro Frame",
            folderName = "unsourced.mse-style",
            gameId = "yugioh",
            category = BetaFrameCategory.FAN_YGO_CUSTOM,
            originTrust = FrameOriginTrust.FAN_YGO_CUSTOM,
            sourceFeedLabel = "Unknown Feed",
            sourceFeedUrl = "",
            sourceWebUrl = "",
            styleFileRawUrl = "",
            authorCredit = "Someone",
            hasUpstreamTemplateFile = false,
        )
        assertTrue(unsourcedFan.isFanMade())
        assertFalse("Fan-made frame with zero URLs must fail source gate", unsourcedFan.hasRequiredFanSources())

        var threwOnUnsourced = false
        try {
            BetaFrameRadarRepository.buildDownloadableBetaCodeText(
                item = unsourcedFan,
                assessment = BetaFrameRadarRepository.assessUniqueFrameUse(unsourcedFan),
                rawStyleCode = "mse version: 2.0.0\ngame: yugioh\ncard width: 421\ncard height: 614",
                rawGameCode = null,
                wasAutoSynthesized = true,
            )
        } catch (ex: IllegalStateException) {
            threwOnUnsourced = ex.message?.contains("source URL", ignoreCase = true) == true
        }
        assertTrue("Building code for unsourced fan-made frame must throw source-gate error", threwOnUnsourced)

        // 2. Verbatim upstream fan template MUST embed source trail in BOTH formatted text AND rawStyleCode
        val verbatimFan = DiscoveredBetaFrameItem(
            id = "verbatim_fan_1",
            displayName = "OmegaDEX Series 10 Fan Style",
            folderName = "yugioh-omegadex.mse-style",
            gameId = "yugioh",
            category = BetaFrameCategory.FAN_YGO_CUSTOM,
            originTrust = FrameOriginTrust.FORKED_REPO,
            sourceFeedLabel = "OmegaDEX / DataEditorX Fork (Lyris12)",
            sourceFeedUrl = "https://github.com/Lyris12/DataEditorX",
            authorCredit = "Lyris12",
            sourceWebUrl = "https://github.com/Lyris12/DataEditorX/tree/master/DataEditorX/data",
            styleFileRawUrl = "https://raw.githubusercontent.com/Lyris12/DataEditorX/master/style",
            hasUpstreamTemplateFile = true,
        )
        val verbatimStyle = """
            mse version: 2.0.0
            game: yugioh
            card width: 421
            card height: 614
            card style:
            	name:
            		left: 32
            		top: 28
            		width: 312
            		height: 36
            	image:
            		left: 49
            		top: 112
            		width: 322
            		height: 322
            	rule text:
            		left: 34
            		top: 460
            		width: 353
            		height: 110
        """.trimIndent()
        val verbatimPkg = BetaFrameRadarRepository.buildDownloadableBetaCodeText(
            item = verbatimFan,
            assessment = BetaFrameRadarRepository.assessUniqueFrameUse(verbatimFan),
            rawStyleCode = verbatimStyle,
            rawGameCode = null,
            wasAutoSynthesized = false,
            fetchedAtIso = "2026-11-28T10:30:00Z",
        )
        assertTrue(verbatimPkg.formattedDownloadableText.contains("=== FAN-MADE SOURCE TRAIL (PERMISSION & RULES RETRACING) ==="))
        assertTrue(verbatimPkg.rawStyleCode.contains("# Creator (Permission): Lyris12"))
        assertTrue(verbatimPkg.rawStyleCode.contains("# Rules/Source URL: https://github.com/Lyris12/DataEditorX/tree/master/DataEditorX/data"))

        // 3. Auto-synthesized no-template fan frame MUST ALSO embed source trail in BOTH formatted text AND rawStyleCode
        val synthFan = DiscoveredBetaFrameItem(
            id = "synth_fan_max_1",
            displayName = "Fan Rush Duel Maximum Frame",
            folderName = "yugioh-maximum-fan.mse-style",
            gameId = "yugioh",
            category = BetaFrameCategory.FAN_YGO_CUSTOM,
            originTrust = FrameOriginTrust.FAN_YGO_CUSTOM,
            sourceFeedLabel = "DeviantArt — Fan YGO Custom Monster Frames",
            sourceFeedUrl = "https://backend.deviantart.com/rss.xml?type=deviation&q=yugioh",
            authorCredit = "RushDesigner7",
            sourceWebUrl = "https://www.deviantart.com/rushdesigner7/art/maximum-frame-555",
            descriptionSnippet = "Custom Rush Duel Maximum Monster frame with MAXIMUM ATK",
            hasUpstreamTemplateFile = false,
        )
        val synthAssess = BetaFrameRadarRepository.assessUniqueFrameUse(synthFan)
        val (synthStyle, synthGame) = BetaFrameRadarRepository.synthesizeUniqueUseBetaCode(synthFan, synthAssess)
        val synthPkg = BetaFrameRadarRepository.buildDownloadableBetaCodeText(
            item = synthFan,
            assessment = synthAssess,
            rawStyleCode = synthStyle,
            rawGameCode = synthGame,
            wasAutoSynthesized = true,
            fetchedAtIso = "2026-11-28T10:30:00Z",
        )
        assertNotNull(synthPkg.rawGameCode)
        assertTrue(synthPkg.formattedDownloadableText.contains("=== FAN-MADE SOURCE TRAIL (PERMISSION & RULES RETRACING) ==="))
        assertTrue(synthPkg.formattedDownloadableText.contains("=== BEGIN CUSTOM MECHANIC GAME FIELDS (game snippet) ==="))
        assertTrue(synthPkg.rawStyleCode.contains("# Creator (Permission): RushDesigner7"))
        assertTrue(synthPkg.rawStyleCode.contains("# Rules/Source URL: https://www.deviantart.com/rushdesigner7/art/maximum-frame-555"))
        // And MSE parser still cleanly parses style code even with inline # source trail comments
        val reparsed = MseStyleParser.parseStyleText(synthPkg.rawStyleCode, synthFan.folderName)
        assertTrue(reparsed.hasValidCompositeGeometry())
    }

    @Test
    fun testForegroundOnlyRadarGatePreventsBackgroundAutoChecks() {
        val cooldown = StudioViewModel.BETA_RADAR_COOLDOWN_MS
        val now = 2_000_000_000L

        // Backgrounded -> NEVER runs even if online, enabled, and past cooldown
        assertFalse(
            StudioViewModel.shouldRunAutomaticForegroundCheck(
                inForeground = false,
                isOnline = true,
                autoCheckEnabled = true,
                lastCheckedMs = 0L,
                nowMs = now,
                cooldownMs = cooldown,
            )
        )

        // Offline -> NEVER runs even in foreground
        assertFalse(
            StudioViewModel.shouldRunAutomaticForegroundCheck(
                inForeground = true,
                isOnline = false,
                autoCheckEnabled = true,
                lastCheckedMs = 0L,
                nowMs = now,
                cooldownMs = cooldown,
            )
        )

        // User toggled Auto-Check off -> NEVER runs automatically
        assertFalse(
            StudioViewModel.shouldRunAutomaticForegroundCheck(
                inForeground = true,
                isOnline = true,
                autoCheckEnabled = false,
                lastCheckedMs = 0L,
                nowMs = now,
                cooldownMs = cooldown,
            )
        )

        // Within 15-minute cooldown -> deferred
        assertFalse(
            StudioViewModel.shouldRunAutomaticForegroundCheck(
                inForeground = true,
                isOnline = true,
                autoCheckEnabled = true,
                lastCheckedMs = now - (cooldown - 1_000L),
                nowMs = now,
                cooldownMs = cooldown,
            )
        )

        // Foreground + Online + Enabled + First scan (0L) -> runs
        assertTrue(
            StudioViewModel.shouldRunAutomaticForegroundCheck(
                inForeground = true,
                isOnline = true,
                autoCheckEnabled = true,
                lastCheckedMs = 0L,
                nowMs = now,
                cooldownMs = cooldown,
            )
        )

        // Foreground + Online + Enabled + Cooldown elapsed -> runs
        assertTrue(
            StudioViewModel.shouldRunAutomaticForegroundCheck(
                inForeground = true,
                isOnline = true,
                autoCheckEnabled = true,
                lastCheckedMs = now - cooldown,
                nowMs = now,
                cooldownMs = cooldown,
            )
        )
    }
}
