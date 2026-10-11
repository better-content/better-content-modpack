# Native EMI server transport

## Scope and authority

This pack integration document owns the EMI packaging/native transport boundary. Current menu
integration belongs to Better Journal Inventory; shared acceptance belongs to
[inventory-parity.md](inventory-parity.md). The retired custom bag/storage backend and its
208-row behavioral qualification are not current requirements.

## Supported packaging requirement

Install the **same pinned EMI Forge JAR on client and dedicated server**. This is a supported
packaging requirement, **not fixture spoofing**. `mods/emi.pw.toml` uses `side = "both"`.

| Field | Required value |
|---|---|
| Version | `1.1.24+1.20.1+forge` |
| Filename | `emi-1.1.24+1.20.1+forge.jar` |
| CurseForge project/file | `580555` / `8081375` |
| Download mode/hash | `metadata:curseforge`, SHA-1 `ea320200878e4a49196760234a22da763671520a` |

Server packaging does not mutate EMI bytecode, force `EmiClient.onServer`, replace its fill
algorithm, suppress warnings, change taint detection or introduce custom storage transport.
It does not prove client-only fallback behavior or runtime acceptance by itself.

## Pinned native protocol

The common `EmiForge` entrypoint initializes `EmiPacketHandler` and native server networking.
`PlayerLoggedInEvent` sends `PingS2CPacket` through the native channel; receipt sets
`EmiClient.onServer` on the actual client. Client setup is isolated in `EmiClientForge`.
The Forge pin declares no mandatory server-side JEI/renderer dependency.

Native `StandardRecipeHandler.craft` chooses actual native transport. `FillRecipeC2SPacket`
uses the live context menu and its real source/crafting/output slot IDs; the server validates
the menu sync ID, actual slot count, permissions, matching stacks/NBT and native extraction.
Do not fabricate a handshake, synthetic slot, or another inventory authority.

With `EmiClient.onServer` false, `EmiRecipeFiller.clientFill` uses native menu clicks. Native
vanilla crafting-grid `quickMoveStack` has its own return range, independent of packaging.
Any claim about actual clearing/refill behavior must be checked on the current native menu,
not inferred from server installation or a former custom-bag implementation.

## Verification boundaries

The fast `EmiServerTransportContractTest` guards metadata and this native boundary only; it
launches no Forge instance and does not establish a handshake, fill, persistence, pixel or
pointer pass. Explicitly authorized runtime checks must cover native initialization, real
client/server selection authority, unchanged candidate hashes, strict log audit, persistence
when affected and process cleanup. Paired inventory acceptance is required for GUI/integration
changes; native API checks and human pointer checks are separate.

Historical candidate worlds and reports are not standing evidence dependencies. During the
active task use evidence for diagnosis; at handoff remove it according to
[generated-data.md](policies/generated-data.md). Do not claim deleted paths remain inspectable.
