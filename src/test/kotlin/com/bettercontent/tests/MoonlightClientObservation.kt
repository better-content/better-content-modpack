package com.bettercontent.tests

import java.nio.file.Files
import java.time.Duration
import java.util.UUID

/** Held-map smoke only. Robot input is synthetic; retained pixels require separate review. */
internal object MoonlightClientObservation {
    private val budget = Duration.ofSeconds(30)
    internal data class Binding(val run: String, val nonce: UUID, val player: String, val clientSha256: String, val serverSha256: String) {
        init {
            require(Regex("[A-Za-z0-9_-]{1,80}").matches(run))
            require(Regex("[A-Za-z0-9_]{1,16}").matches(player))
            require(listOf(clientSha256, serverSha256).all { Regex("[a-f0-9]{64}").matches(it) })
        }
    }
    internal enum class Kind { SELECTED_SLOT, GAME_MODE, MAP_ID }
    internal data class Query(val binding: Binding, val kind: Kind, val nonce: UUID = UUID.randomUUID())
    internal data class Response(val query: Query, val body: String)

    internal fun responsePattern(player: String): Regex {
        require(Regex("[A-Za-z0-9_]{1,16}").matches(player))
        return Regex("(?m)(?<![A-Za-z0-9_])${Regex.escape(player)} has the following entity data: [^\\r\\n]+$")
    }
    internal fun integer(expected: Query, response: Response): Int {
        require(response.query == expected) { "stale/nonmatching map observation binding or query" }
        val match = Regex("${Regex.escape(expected.binding.player)} has the following entity data: (0|[1-9][0-9]*)").matchEntire(response.body)
            ?: error("not an exact nonnegative native integer response")
        val value = match.groupValues[1].toIntOrNull() ?: error("native integer overflow")
        when (expected.kind) {
            Kind.SELECTED_SLOT -> require(value in 0..8) { "invalid native selected hotbar slot" }
            Kind.GAME_MODE -> require(value in 0..3) { "invalid native game mode" }
            Kind.MAP_ID -> require(value >= 0)
        }
        return value
    }
    internal fun marker(binding: Binding, operation: String, nonce: UUID): String {
        require(Regex("[a-z0-9_-]{1,48}").matches(operation))
        return "BC_MOONLIGHT_CLIENT run=${binding.run} nonce=${binding.nonce} request=$nonce player=${binding.player} client=${binding.clientSha256} server=${binding.serverSha256} operation=$operation"
    }
    internal fun requireMarker(expected: String, body: String) {
        require(body == expected) { "stale/nonmatching native map fixture marker" }
    }
    internal fun shouldRun(tier: String, target: String?): Boolean = tier in setOf("dist", "debug") && target == null
    internal fun inventoryCommandSlot(slot: Int): String {
        require(slot in 0..35)
        return if (slot < 9) "hotbar.$slot" else "inventory.${slot - 9}"
    }
    internal fun slotPresence(empty: String, occupied: String, freshText: String): Boolean {
        val pattern = Regex("(?:${Regex.escape(empty)}|${Regex.escape(occupied)})(?=\\r?$)", RegexOption.MULTILINE)
        val matches = pattern.findAll(freshText).map { it.value }.toList()
        require(matches.size == 1) { "missing/ambiguous/stale native inventory-slot observation" }
        return matches.single() == occupied
    }

    fun observe(evidence: EvidenceRun, server: DedicatedServerFixture, client: ClientFixture) {
        require(shouldRun(evidence.tier, evidence.target))
        val binding = Binding(evidence.config.runId, UUID.randomUUID(), client.username, server.pair.clientSha256, server.pair.serverSha256)
        val player = binding.player
        server.assertHashes(); client.assertHashes()
        val sourceFiles = listOf("fabric-api-0.92.6+1.11.14+1.20.1-bc-runtime.1.jar", "moonlight-1.20-2.16.34-forge.jar")
            .associateWith { name -> server.server.resolve("mods/$name").also { require(Files.isRegularFile(it) && !Files.isSymbolicLink(it)) } }
        val sourceHashes = sourceFiles.mapValues { Hashes.sha256(it.value) }
        fun checkedMarker(command: String, operation: String) {
            val text = marker(binding, operation, UUID.randomUUID())
            val match = server.commandResult("$command run say $text", Regex(Regex.escape(text) + "(?=\\r?$)", RegexOption.MULTILINE), operation, budget)
            requireMarker(text, match.value)
        }
        fun empty(slot: Int) {
            require(slot == 8 || slot == -106)
            checkedMarker("execute unless data entity $player Inventory[{Slot:${slot}b}]", if (slot == 8) "hotbar8-empty" else "offhand-empty")
        }
        fun scalar(kind: Kind, path: String, prefix: String = "", retry: Boolean = false): Int {
            val query = Query(binding, kind)
            val result = server.commandResult("${prefix}data get entity $player $path", responsePattern(player), "moonlight-${kind.name.lowercase()}", budget,
                if (retry) Duration.ofSeconds(1) else null)
            return integer(query, Response(query, result.value))
        }
        fun present(slot: Int): Boolean {
            require(slot in 0..35)
            val nonce = UUID.randomUUID()
            val vacant = marker(binding, "slot-$slot-empty", nonce)
            val occupied = marker(binding, "slot-$slot-occupied", nonce)
            val fence = marker(binding, "slot-$slot-fence", nonce)
            val offset = if (Files.exists(server.log)) Files.readString(server.log).length else 0
            // Three ordinary console lines, one fresh-offset commandResult transaction. The
            // fence ensures BOTH conditional branches finished before inspecting their one marker.
            val commands = "execute if data entity $player Inventory[{Slot:${slot}b}] run say $occupied\n" +
                "execute unless data entity $player Inventory[{Slot:${slot}b}] run say $vacant\n" + "say $fence"
            val result = server.commandResult(commands, Regex(Regex.escape(fence) + "(?=\\r?$)", RegexOption.MULTILINE), "moonlight-slot-$slot", budget)
            requireMarker(fence, result.value)
            return slotPresence(vacant, occupied, Files.readString(server.log).drop(offset))
        }
        fun replace(slot: String, item: String) {
            require(slot == "weapon.offhand" || slot in (0..35).map(::inventoryCommandSlot))
            server.commandResult("item replace entity $player $slot $item", Regex("Replaced a slot on ${Regex.escape(player)} with [^\\r\\n]+"), "moonlight-replace-$slot", budget)
        }
        fun robot(key: Int?, click: Boolean, operation: String) {
            require(key == null || key in 1..9)
            require(Regex(":[0-9]{1,4}").matches(client.display) && client.display != ":0")
            val nonce = UUID.randomUUID(); val done = "BC_MOONLIGHT_ROBOT nonce=$nonce operation=$operation"
            val completed = evidence.directory.resolve("moonlight-robot-$operation-$nonce.completed")
            require(!Files.exists(completed))
            val input = """
                import java.awt.Robot;
                import java.awt.event.KeyEvent;
                import java.awt.event.InputEvent;
                void action() throws Exception {
                  Robot r = new Robot();
                  r.setAutoDelay(50);
                  try {
                    ${if (key != null) "r.keyPress(KeyEvent.VK_1 + ${key - 1}); r.keyRelease(KeyEvent.VK_1 + ${key - 1});" else ""}
                    ${if (click) "r.mousePress(InputEvent.BUTTON3_DOWN_MASK); r.delay(100); r.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);" else ""}
                    r.delay(200);
                  } finally {
                    try { r.mouseRelease(InputEvent.BUTTON3_DOWN_MASK); }
                    finally { ${if (key != null) "r.keyRelease(KeyEvent.VK_1 + ${key - 1});" else ""} }
                  }
                }
                try { action(); java.nio.file.Files.writeString(java.nio.file.Path.of(java.net.URI.create("${completed.toUri()}")), "$done", java.nio.file.StandardOpenOption.CREATE_NEW); } catch (Throwable t) { t.printStackTrace(); System.exit(1); }
                /exit
            """.trimIndent()
            val log = evidence.directory.resolve("moonlight-robot-$operation-$nonce.log")
            Commands.runWithInput(listOf(evidence.config.java.parent.resolve("jshell").toString()), client.client, log, input, mapOf("DISPLAY" to client.display))
            // JShell may itself exit zero after a failed remote snippet. A fresh, nonce-bound
            // completion file written ONLY after action/finally is the execution oracle.
            require(Files.isRegularFile(completed) && !Files.isSymbolicLink(completed) && Files.readString(completed) == done) { "actual Robot action did not complete; see $log" }
            evidence.event("moonlight_robot_input", mapOf("operation" to operation, "nonce" to nonce.toString(), "display" to client.display,
                "input" to "synthetic AWT Robot on this game Xvfb only; not human pointer evidence", "client_sha256" to binding.clientSha256, "server_sha256" to binding.serverSha256))
        }
        fun assertSources() {
            server.assertHashes(); client.assertHashes()
            require(sourceFiles.mapValues { Hashes.sha256(it.value) } == sourceHashes) { "Moonlight/FFAPI fixture inputs drifted" }
        }
        fun capture(name: String, hand: String, map: Int) {
            // Allow ordinary network item/map updates and held-item animation to settle.
            // This delay is NOT a packet-delivery or pixel oracle; the actual PNG is reviewed later.
            Thread.sleep(2000)
            require(scalar(Kind.SELECTED_SLOT, "SelectedItemSlot") == 8)
            val image = evidence.directory.resolve(name)
            require(!Files.exists(image)) { "held-map capture must be fresh, not an overwritten/reused image" }
            client.capture(name)
            evidence.event("moonlight_map_capture", mapOf("run_id" to binding.run, "nonce" to binding.nonce.toString(), "player" to player,
                "map_id" to map, "hand" to hand, "image" to image.toString(), "image_sha256" to Hashes.sha256(image), "sources" to sourceHashes,
                "client_sha256" to binding.clientSha256, "server_sha256" to binding.serverSha256, "visual_review" to "PENDING",
                "scope" to "actual client empty-map use / server-created map / held-map frame only; not all Moonlight modules or inventory GUI acceptance",
                "human_pointer_verdict" to "NOT_PERFORMED", "packet_payload_instrumentation" to "NOT_PERFORMED"))
            assertSources()
        }

        // No fixture writes or Robot input before BOTH native slots are proved empty.
        empty(8); empty(-106)
        val originalSlot = scalar(Kind.SELECTED_SLOT, "SelectedItemSlot")
        val originalMode = scalar(Kind.GAME_MODE, "playerGameType")
        require(originalMode != 3) { "held-map input needs a nonspectator controlled player; game mode/flight will not be changed" }
        if (originalMode == 1) checkedMarker("execute unless data entity $player Inventory[{id:\"minecraft:filled_map\"}]", "no-existing-filled-maps")
        var ownsHotbar = false; var ownsOffhand = false; var selectionChanged = false; var mapId: Int? = null
        var creativeCreatedSlot: Int? = null
        var primary: Throwable? = null
        try {
            // Preserve mode AND flight. Actual creative EmptyMapItem.use leaves the empty map
            // selected and Inventory.add(copy) puts the newly created map in the first empty
            // main slot. Existing filled maps are excluded above rather than allowing merges.
            ownsHotbar = true
            replace("hotbar.8", "with minecraft:map 1")
            if (originalMode == 1) {
                creativeCreatedSlot = (0..35).firstOrNull { it != 8 && !present(it) }
                    ?: error("creative map smoke needs another originally empty main slot")
            }
            selectionChanged = true; robot(9, false, "select-owned-slot")
            require(scalar(Kind.SELECTED_SLOT, "SelectedItemSlot") == 8)
            checkedMarker("execute if data entity $player SelectedItem{id:\"minecraft:map\",Count:1b}", "empty-map-selected")
            creativeCreatedSlot?.let { require(!present(it)) { "reserved creative map destination stopped being empty before actual client use" } }
            robot(null, true, "real-client-use-empty-map")
            if (originalMode == 1) {
                val created = requireNotNull(creativeCreatedSlot)
                mapId = scalar(Kind.MAP_ID, "Inventory[{Slot:${created}b}].tag.map",
                    "execute if data entity $player Inventory[{Slot:${created}b,id:\"minecraft:filled_map\",Count:1b}] run ", true)
                checkedMarker("execute if data entity $player SelectedItem{id:\"minecraft:map\",Count:1b}", "creative-empty-map-retained")
                replace("hotbar.8", "from entity $player ${inventoryCommandSlot(created)}")
                server.send("execute if data entity $player Inventory[{Slot:${created}b,id:\"minecraft:filled_map\",Count:1b,tag:{map:$mapId}}] run item replace entity $player ${inventoryCommandSlot(created)} with minecraft:air")
                require(!present(created)) { "owned creative map destination was not restored empty" }
            } else {
                mapId = scalar(Kind.MAP_ID, "SelectedItem.tag.map", "execute if data entity $player SelectedItem{id:\"minecraft:filled_map\",Count:1b} run ", true)
            }
            require(scalar(Kind.MAP_ID, "SelectedItem.tag.map", "execute if data entity $player SelectedItem{id:\"minecraft:filled_map\",Count:1b} run ") == mapId)
            evidence.event("moonlight_native_map_created", mapOf("run_id" to binding.run, "nonce" to binding.nonce.toString(), "map_id" to mapId,
                "original_game_mode" to originalMode, "native_created_slot" to (creativeCreatedSlot ?: 8), "source" to "actual vanilla EmptyMapItem.use after real client Robot right click; no fabricated map data",
                "client_sha256" to binding.clientSha256, "server_sha256" to binding.serverSha256))
            empty(-106)
            capture("moonlight-map-mainhand.png", "main", mapId)
            ownsOffhand = true
            replace("weapon.offhand", "from entity $player hotbar.8")
            checkedMarker("execute if data entity $player Inventory[{Slot:-106b,id:\"minecraft:filled_map\",Count:1b,tag:{map:$mapId}}]", "actual-map-copied-offhand")
            replace("hotbar.8", "with minecraft:air")
            empty(8)
            capture("moonlight-map-offhand.png", "off", mapId)
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            val failures = mutableListOf<Throwable>()
            fun cleanup(action: () -> Unit) { try { action() } catch (failure: Throwable) { failures.add(failure) } }
            fun clearOwned(slot: String, nbtSlot: Int) {
                // Conditional clears never erase a foreign item if an unexpected operation replaced
                // the owned map. Final empty-slot assertions still fail such a scenario.
                if (slot == "hotbar.8") server.send("execute if data entity $player Inventory[{Slot:${nbtSlot}b,id:\"minecraft:map\",Count:1b}] run item replace entity $player $slot with minecraft:air")
                mapId?.let { id -> server.send("execute if data entity $player Inventory[{Slot:${nbtSlot}b,id:\"minecraft:filled_map\",Count:1b,tag:{map:$id}}] run item replace entity $player $slot with minecraft:air") }
                empty(nbtSlot)
            }
            if (ownsOffhand) cleanup { clearOwned("weapon.offhand", -106) }
            if (ownsHotbar) cleanup { clearOwned("hotbar.8", 8) }
            creativeCreatedSlot?.let { slot -> cleanup {
                mapId?.let { id -> server.send("execute if data entity $player Inventory[{Slot:${slot}b,id:\"minecraft:filled_map\",Count:1b,tag:{map:$id}}] run item replace entity $player ${inventoryCommandSlot(slot)} with minecraft:air") }
                require(!present(slot)) { "owned creative destination was not restored empty; unknown items are not cleared" }
            } }
            if (selectionChanged) cleanup { robot(originalSlot + 1, false, "restore-original-slot"); require(scalar(Kind.SELECTED_SLOT, "SelectedItemSlot") == originalSlot) }
            cleanup { require(scalar(Kind.GAME_MODE, "playerGameType") == originalMode); assertSources() }
            evidence.event("moonlight_map_cleanup", mapOf("original_selected_slot" to originalSlot, "original_game_mode" to originalMode,
                "owned_slots_only" to (listOf("hotbar.8", "weapon.offhand") + listOfNotNull(creativeCreatedSlot?.let(::inventoryCommandSlot))), "game_mode_and_flight_never_written" to true, "failures" to failures.map { it.toString() }, "map_id" to mapId))
            if (primary != null) failures.forEach { primary.addSuppressed(it) }
            else if (failures.isNotEmpty()) { val first = failures.first(); failures.drop(1).forEach(first::addSuppressed); throw first }
        }
    }
}
