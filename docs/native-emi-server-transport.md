# Native EMI server transport

## Supported packaging requirement

Install the **same pinned EMI Forge JAR on both the client and dedicated server**. This is a supported packaging requirement for the pack's native full-capacity foreign crafting fill/refill path, **not fixture spoofing**. The only EMI metadata change is `mods/emi.pw.toml`: `side = "client"` becomes `side = "both"`.

The artifact pin remains unchanged:

| Field | Required value |
| --- | --- |
| Version | `1.1.24+1.20.1+forge` |
| Filename | `emi-1.1.24+1.20.1+forge.jar` |
| CurseForge project ID | `580555` |
| CurseForge file ID | `8081375` |
| Download hash format | `sha1` |
| Download hash | `ea320200878e4a49196760234a22da763671520a` |
| Download mode | `metadata:curseforge` |

This change does not upgrade or replace EMI, add a custom transport, force `EmiClient.onServer`, or change its native fill algorithm. It does not mutate EMI bytecode, suppress warnings, change the taint checker, or introduce a log whitelist.

The contained owned-handler ordering adapter is a separate, explicitly authorized integration: public registration adds the owned `ForeignCraftingEmiHandler`; a version-guarded adapter promotes only that current handler in the existing handler list. The owned handler preserves native crafting targets/default transport and adds actual descriptor-backed surplus input slots. Server packaging neither replaces that adapter nor changes native menu identities or slot IDs.

## Exact pinned Forge initialization and distribution proof

These findings come from inspecting the pinned `8081375` artifact's metadata and bytecode, not from assuming that an item viewer is server-compatible:

1. `EmiForge` is the common `@Mod("emi")` entrypoint, without a client-only distribution annotation. Its constructor calls `EmiMain.init()`, `EmiPacketHandler.init()`, and `EmiNetwork.initServer(...)`, then registers command and player-login listeners. `EmiMain.init()` is a no-op in this Forge artifact.
2. The native server sender uses `EmiPacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(...), packet)`. The channel is `emi:emi`, with protocol `0` and upstream `acceptMissingOr` predicates.
3. On `PlayerLoggedInEvent`, `EmiForge.playerConnect` checks for `ServerPlayer` and sends a native `PingS2CPacket` through `EmiNetwork.sendToClient`.
4. `PingS2CPacket.apply` sets `EmiClient.onServer = true` on the actual client through the native received-packet path. No reflection, debug setting, runtime flag forcing, or synthetic handshake is needed.
5. `EmiPacketHandler.init()` registers native `FillRecipeC2SPacket` as discriminator `0`, direction `PLAY_TO_SERVER`, and `PingS2CPacket` as discriminator `3`, direction `PLAY_TO_CLIENT`. The server handler enqueues work on the server thread and applies the packet using `NetworkEvent.Context.getSender`; the client handler enqueues work on the client thread.
6. Client setup/rendering is isolated in `EmiClientForge`, annotated `@Mod.EventBusSubscriber(modid = "emi", bus = MOD, value = Dist.CLIENT)`. The artifact does not require a `DistExecutor` workaround for the common entrypoint. All 25 entries in `emi.mixins.json` are in its `client` list; its common `mixins` list is empty. Both lists in `emi-forge.mixins.json` are empty.
7. `META-INF/mods.toml` declares `javafml`, loader range `[40,)`, and the exact version above. Its only declared mod dependency is JEI, `mandatory = false`, `side = "CLIENT"`, version range `[15.20.0.0,)`. No mandatory dedicated-server JEI or renderer dependency is declared.

These are upstream native optional server capabilities. Source/bytecode feasibility is not a successful dedicated-server boot or handshake verdict; the actual candidate must still pass those gates.

## Native server fill and real surplus IDs

`StandardRecipeHandler.craft` remains the native default implementation. It obtains stacks through `EmiRecipeFiller.getStacks`, restores the actual context screen, and chooses transport using the real `EmiClient.onServer` value. With the native server ping received, it calls `EmiClient.sendFillRecipe`, constructing the existing `FillRecipeC2SPacket` from the real context menu and the handler's actual source/crafting/output slots. The packet's public codec starts with `writeInt(syncId)`, then `writeByte(action)`; the contained intent observer reads that native codec without altering the message or send.

On the server, `FillRecipeC2SPacket.apply`:

- Requires the live player's `containerMenu.containerId` to match the packet's `syncId`.
- Checks each source ID is nonnegative and less than the **actual menu slot count**, then resolves it to the existing `Slot` object. There is no first-45 source ceiling.
- Uses the native crafting/output slot validation and native matching-item/NBT, pickup permission, extraction and stack-limit behavior. It does not grant client authority over bag allocation or create virtual cells.
- Returns crafting leftovers through `player.getInventory().placeItemBackInInventory`. The existing journal `InventoryFaceMixin` routes this normal inventory operation to `BagFaceBinding.pickup`, whose normal handler insertion scans the full native bag capacity as well as the ordinary hotbar.

For the capacity-120 fixture, the actual `CraftingMenu` has **139 total slots, valid IDs 0–138**. Bag cell 118 is source menu ID 137; bag cell 119 is destination menu ID 138. **ID 139 itself is invalid** and must be rejected. The appended source IDs remain real registered/server-active slots, even when their presentation is off-page.

With full ordinary hotbar/stone cells, `OAK_LOG x2` at bag cell 118, and only bag cell 119 empty, the native server route can extract one log and return the tagged `TORCH x7`/`x11` from the crafting grid into the last bag cell using the existing native inventory bridge. This is an implementation trace, not an observed runtime success. The independent complete ownership ledger must prove exact count/NBT conservation, target contents, and result preview; the client must prove source coverage, matching typed intent, authoritative consumption, and destination reveal.

## Client-only native clearing/return limitation — pending fix acceptance

The client-only fallback is **not fixed by this packaging change**. Neither is ordinary crafting-grid `QUICK_MOVE` beyond its current native return range.

When `EmiClient.onServer` is false, the native default calls `EmiRecipeFiller.clientFill`:

- Input gathering uses `handler.getInputSources(menu)`, so the owned handler can expose real appended input slots.
- Input/target/remainder operations use normal `gameMode.handleInventoryMouseClick` calls with the actual `Slot.index`; they do not require synthetic pointer input or an EMI custom packet.
- Initial grid clearing quick-moves each native crafting slot. Native `CraftingMenu.quickMoveStack` for input slots 1–9 calls `moveItemStackTo(stack, 10, 46, false)`, covering only original player menu IDs 10–45.
- In the inspected pre-extension journal integration, `SurplusQuickMove.handle` delegates non-surplus crafting slot 1 to that native method. Its `StorageTransfers.move` interception honors the supplied range and does not spill into appended destination 138.

Thus, before the separately authorized native return extension, the wrong tagged torch cannot clear into the empty hidden surplus cell through this client-only path when the original player return range is completely full. An official owned `craft` wrapper could capture intent and delegate the same default transport, but that wrapper alone would not resolve this native return-range limitation. Do not move ingredients into the first 27 cells, aggregate aliases, force the server flag, or report the fallback as covered.

### Separately authorized parent-owned return extension

This limitation is **pending fix acceptance**, not a permanently accepted gap. The parent owns the production `SurplusQuickMove` change, its focused guard and the real client-network fixtures. The bounded extension is authorized only for exact vanilla `CraftingMenu` input slots 1–9, a survival/noncreative/nonspectator owner, a live captured native binding and an exact descriptor with start 46, first bag index 27 and the complete real slot shape. Preserve the original return order through IDs 10–45 first; only residual items may use lawful actual appended slots through existing `StorageTransfers.move` and native handler insertion. No arbitrary range widening, synthetic cells, EMI algorithm override or custom packet is authorized.

Two new full-only rows bring the requested manifest to **208 full / 36 sentinel**:

- `client.foreign_grid_return`: tagged torch in native crafting input slot 1, original player return range full and only the hidden last bag cell empty; require exact authoritative return and page observation.
- `client.foreign_grid_return_blocked`: all destinations full; require exact no-mutation and page preservation.

Both use the existing generic client click/`QUICK_MOVE`/barrier observation path. They directly prove native crafting-grid return behavior independently of EMI packet transport. The four foreign EMI rows retain their own actual overlay/native packet/query assertions. Passing these direct return rows alone does **not** prove that the `onServer = false` `clientFill` branch executed, nor does server installation do so. No additional owned `craft` wrapper or global action-suppression mechanism is planned unless actual evidence establishes a need. Source inspection and a pending patch are not runtime acceptance.

## Failure evidence and independent acceptance gates

Build-406 run `20261008T102920Z-627743` retained evidence under `generated/test-evidence/20261008T102920Z-627743/target-journal-inventory`:

- The report was **202/206**, with exactly the four foreign EMI rows failing at the fixture's early `onServer` requirement.
- All four stopped before opening a recipe overlay, querying/selecting a handler, or attempting fill. Their `pending = false`, zero typed sequence, unchanged page and empty destination are **not** executed negative-control signoff.
- The genuine native surface had capacity 120, three pages, logical size 427×240, and unchanged OS viewport 1280×720. Input 137 remained `OAK_LOG x2`; destination 138 remained empty. The tagged torches remained in native crafting slot 1.
- The retained server metadata specified `side = "client"`; the pinned EMI JAR was absent from the dedicated server's mods directory. Native `EmiClient.onServer` was therefore false in the observed responses.
- The strict log audit recorded zero target findings and no EMI taint text; persistence was not reached. Process cleanup completed with no surviving PIDs. The failed fixture was retained.

Do not rewrite this historical result or infer that adding server metadata already passed behavior. A fresh unchanged candidate pair must independently pass:

1. Dedicated-server startup and normal client join/native ping handshake with the same pinned server JAR.
2. The complete **208-row** full behavioral manifest (36 sentinel rows), including both direct native crafting-grid return/blocked-return rows and the four independent foreign EMI rows: real fill/refill source 137, hidden return 138, exact count/NBT ledger, authoritative reveal, query-only non-intent and genuine rejected-fill non-mutation.
3. Reconnect and fresh-server-restart **persistence** observations with the unchanged native UUID/contents/upgrades/filter metadata.
4. The unchanged **strict log audit**, including EMI taint detection, and candidate-hash checks.
5. Complete process **cleanup** and retained diagnostic evidence for every failed or aborted gate.

The fast `EmiServerTransportContractTest` only pins packaging metadata and these explicit boundaries. It does not launch Forge, fabricate a transport handshake, or establish runtime/pixel/pointer acceptance. Full release/integrated gates and human visual/pointer review remain separate requirements.
