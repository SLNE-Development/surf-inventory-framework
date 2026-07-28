# Packet GUI Backend

An optional rendering backend that draws GUI contents as virtual packet items instead of placing real items
into a Bukkit inventory. The public InventoryFramework API is unchanged; the classic Bukkit backend stays
available and is still the default.

## Activation

The backend is opt-in via a JVM system property:

```
-Dinventory-framework.gui-backend=packet
```

Requirements:

- **PacketEvents must be installed and initialized before InventoryFramework.** It is declared as a
  `softdepend`. If it is missing or not yet initialized, the framework logs a warning and uses the Bukkit
  backend.
- The server's packet classes must match what the native outbound sender expects (see below).

To force the native outbound sender off without disabling packet mode entirely — useful for narrowing down a
suspected packet problem — start with:

```
-Dinventory-framework.gui-backend.native=off
```

That makes the sender report itself unavailable, which in turn falls the whole packet backend back to Bukkit
inventories.

When a click does not reach a view, log every inbound click and the routing decision taken for it:

```
-Dinventory-framework.gui-backend.debug-clicks=true
```

Each click then produces one INFO line naming the window id, slot, button, click type, the computed repair
scope and whether it was routed into the click pipeline or denied.

## How availability is decided

There is no Minecraft version allowlist. On startup `PacketGuiNativeOutboundSender.initialize()`:

1. resolves every NMS class, constructor, field and method it needs,
2. probes both shapes of the `ClientboundContainerSetContentPacket` constructor (`NonNullList` and `List`),
3. runs a **self-check** that actually constructs one of every packet it will ever send — container content,
   container slot, cursor and player inventory slot — without sending anything.

Only if all three succeed is packet mode enabled. Any mismatch produces a single warning naming the detected
Minecraft version, and every GUI silently keeps using real Bukkit inventory items.

Startup log lines to look for:

- `Native packet GUI probe detected Minecraft <version> (…)` — always logged, tells you what was detected.
- `Packet mode enabled. Inventory GUIs are rendered with fake packet items.` — the good case.
- `Bukkit fallback enabled. …` at WARNING — packet mode was requested but could not be honoured. The message
  names the reason.

## Known limitations

- **Chest-style containers only.** `ViewType.CHEST` with a size that is a multiple of 9. Anvil, hopper,
  dropper, dispenser and every other type keep using real Bukkit inventories with real items. The first time
  such a type is encountered it is logged once at INFO.
- **The viewer's own inventory is effectively read-only while a packet GUI is open.** Click packets are
  cancelled before vanilla sees them, so nothing moves by itself. A view can still act on bottom-inventory
  clicks: the click is delivered as an entity-container click, and a handler that calls
  `setCancelled(false)` **and** changes `clickOrigin.currentItem` gets that item written back to the real
  slot. Vanilla pickup/swap/quick-move semantics are deliberately not emulated.
- **Drag, double-click, the drop key and unknown click modes are denied.** They are cancelled and answered
  with a full resync; no view callback runs for them.
- **Outside clicks are routed.** The protocol has two wire forms for them, and both are accepted on a negative
  slot: `PICKUP` (mode 0) when the player holds an item, and `THROW` (mode 4) when the cursor is empty. A
  packet GUI never puts anything on the real cursor, so in practice the client always sends the `THROW` form.
  The negative slot is what separates these from their in-window meaning — `THROW` on a real slot is the drop
  key and stays denied, and drag (`QUICK_CRAFT`) carries a negative slot too but is never treated as a click.
  Consumers see such a click as `ClickType.LEFT`/`RIGHT` with `SlotType.OUTSIDE`, matching the Bukkit backend.
- **`RenderContext#getInventory()` throws** `UnsupportedOperationException` in packet mode. Probe with
  `RenderContext#isBackedByRealInventory()` first.
- **`SlotClickContext#getClickOrigin()` returns a synthesized `InventoryClickEvent`.** Item access,
  cancellation, slot, slot type, action, hotbar button and click type are served from the packet click. The
  inherited `getInventory()`, `getCursor()` and `setCursor(...)` are left intact so existing plugins keep
  compiling and running, but they resolve against the player's *own* inventory view. Reading them is harmless;
  writing through them reaches real server-side state and must not be done from a packet GUI handler.

## Verification checklist

Manual checklist from `AGENTS.md`, extended with the scenarios this backend added. Verified on the live
SurfCanvas server (Minecraft 26.2, `canvas-26.2-883`) with `-Dinventory-framework.gui-backend=packet`, against
surf-api `3.34.0` built from `1.0.5-packet-guis-SNAPSHOT`.

Rows marked *(log)* were confirmed from the `-Dinventory-framework.gui-backend.debug-clicks=true` output rather
than by watching the screen.

| Szenario | Erwartet | Geprüft am | Ergebnis |
|---|---|---|---|
| Packet mode activates | Startup logs `Packet mode enabled`, not the Bukkit fallback | 2026-07-28 | OK — probe reported Minecraft 26.2, PacketEvents V_26_2, native sender active |
| Opening a simple GUI | Window opens with the configured title and size | 2026-07-28 | OK — `/protect` and `/shop` open |
| Displaying all top slots | Every rendered slot shows its item | 2026-07-28 | OK |
| Title rendering | Plain and Adventure component titles both render | | |
| Rows/size rendering | 1–6 row chests all open at the right size | 2026-07-28 | OK for 3, 5 and 6 rows (topSize 27/45/54) *(log)*; 1, 2 and 4 rows not exercised |
| Clicking a normal button | The view's click handler runs once | 2026-07-28 | OK |
| Refresh/rerender after click | Changed slots update, unchanged ones are not resent | 2026-07-29 | OK — no visible problem after the render-plan rewrite |
| Page/screen replacement | Navigating between views replaces the window cleanly | 2026-07-28 | OK — chains of views navigated without a stuck window |
| Opening a GUI while another GUI is open | The new window replaces the old one; no command has to be repeated | 2026-07-29 | OK — was broken before `39a19c8`, fixed and re-tested |
| Outside click | Reaches the view; surf-api uses it for back navigation | 2026-07-28 | OK — was broken before `3bf6ef0`; client sends `THROW`/slot -999 |
| Bottom inventory click | Delivered to the view as an entity-container click | 2026-07-28 | OK — `slot=88, bottom=true, scope=PLAYER_INVENTORY, routed` *(log)* |
| Double-click denial | Denied, no callback, full resync | 2026-07-28 | OK — `PICKUP_ALL -> denied, full resync` *(log)* |
| PacketLore on inventory items | An enchanted item in the viewer's inventory shows its lore in the GUI, exactly once | 2026-07-29 | OK — decorated once, also after the mirror was removed in `891b7c0` |
| Close handling | ESC closes the GUI and fires `onClose` exactly once | | |
| Player quit cleanup | Session and viewer are removed, `onClose` fires once | | |
| External inventory open cleanup | Opening a real chest finalizes the packet session | | |
| Shift-click | **Changed:** top-slot shift-clicks are *routed* to the view, not denied; the packet is still cancelled | | |
| Number-key | **Changed:** top-slot number-key swaps are *routed* to the view; the packet is still cancelled | | |
| Offhand swap | **Changed:** routed to the view; the packet is still cancelled | | |
| Drag denial | Denied, no callback, full resync | | |
| Drop-key denial | Denied, no callback, full resync | | |
| Cursor ghost-item correction | No item sticks to the cursor after any click | | |
| Bottom inventory visual correctness | The viewer's own items render correctly and snap back when clicked | | |
| No GUI display items in real server inventory contents | `/invsee` or an inventory dump shows no GUI icons in any real inventory | | |
| Window id collision | Opening a GUI while a real chest is open closes the chest and never reuses its window id | | |
| World change / respawn | Session is finalized, no stale viewer keeps receiving GUI packets | | |

The rows marked **Changed** deviate from the original AGENTS.md expectation. Those click modes are deliberately
routed into the click API rather than denied, because the packet is already cancelled at the listener, so
nothing vanilla can mutate. What *is* denied is drag, the drop key, double-click and any unrecognised mode.

### Still open

The empty rows have not been exercised. Two of them are worth clearing before this is considered done:

- **No GUI display items in real server inventory contents.** This is the claim the whole backend exists to
  make, and it is the one row nobody has checked. Open a GUI, then have a second player or a console command
  dump the viewer's inventory and confirm no GUI icon appears in it.
- **Window id collision.** The guard in `PacketGuiWindowIds` is unit-tested, but the end-to-end path — open a
  real chest, then open a GUI — has only been reasoned about, never run.

The remaining gaps are the denial modes (drag, drop key, offhand swap, number key), lifecycle cleanup on quit
and world change, and cursor correction. Each is a single deliberate action on a server with
`-Dinventory-framework.gui-backend.debug-clicks=true` enabled, which prints the routing decision for every
click.

## Where the rendered items come from

The top rows come from the view's own render model in `PacketViewContainer`; they are never real inventory
contents. The bottom rows are read live from the viewer's Bukkit inventory and sent through
`PacketGuiNativeOutboundSender`, i.e. the same outbound path vanilla uses.

That last point matters for surf-api's PacketLore: because the packets travel the server's normal outbound
path, an enchanted item in the viewer's inventory shows its enchantment lore inside a packet GUI exactly as it
does anywhere else, decorated once. The backend deliberately keeps **no** mirror of the viewer's items — items
captured from already-intercepted outbound packets would be decorated a second time when resent. The only
packet-side viewer state is `PacketViewerWindowTracker`, which remembers the id of the real container window so
a fake window id never collides with it.

## Scheduling

All GUI work runs on the thread that owns the viewer, scheduled through FoliaLib's `PlatformScheduler`, which
the module already depends on. On Folia that is the player's region scheduler; on Paper and Spigot it resolves
to the primary thread. When the task is already on the right thread it runs inline rather than being deferred —
several call sites depend on the work having happened by the time they return, most importantly `open()`, which
publishes the session before rendering it.

If the scheduler refuses a task, the viewer is gone: the session is discarded rather than run on the wrong
thread. That matters for the next-tick path in particular, because a dropped render task would otherwise leave
the session's render request latched and swallow every later one.

## Build note

`spotlessCheck` and `spotlessApply` do not run under this project's JDK 25 toolchain —
palantir-java-format throws `NoSuchMethodError` on `Log$DeferredDiagnosticHandler.getDiagnostics`. Formatting
in the packet backend is therefore maintained by hand: 4-space indentation, 120-column limit, no tabs.
