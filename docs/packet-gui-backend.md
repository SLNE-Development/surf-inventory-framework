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
- **Drag, drop, double-click and unknown click modes are denied.** They are cancelled and answered with a full
  resync; no view callback runs for them. Only a plain pickup on slot `-999` counts as an outside click.
- **`RenderContext#getInventory()` throws** `UnsupportedOperationException` in packet mode. Probe with
  `RenderContext#isBackedByRealInventory()` first.
- **`SlotClickContext#getClickOrigin()` returns a synthesized `InventoryClickEvent`.** Item access,
  cancellation, slot, slot type, action, hotbar button and click type are served from the packet click. The
  inherited `getInventory()`, `getCursor()` and `setCursor(...)` are left intact so existing plugins keep
  compiling and running, but they resolve against the player's *own* inventory view. Reading them is harmless;
  writing through them reaches real server-side state and must not be done from a packet GUI handler.

## Verification checklist

Manual checklist from `AGENTS.md`. Fill in when validating a build on a real server.

| Szenario | Erwartet | Geprüft am | Ergebnis |
|---|---|---|---|
| Opening a simple GUI | Window opens with the configured title and size | | |
| Displaying all top slots | Every rendered slot shows its item | | |
| Title rendering | Plain and Adventure component titles both render | | |
| Rows/size rendering | 1–6 row chests all open at the right size | | |
| Clicking a normal button | The view's click handler runs once | | |
| Refresh/rerender after click | Changed slots update, unchanged ones are not resent | | |
| Page/screen replacement | Navigating between views replaces the window cleanly | | |
| Close handling | ESC closes the GUI and fires `onClose` exactly once | | |
| Player quit cleanup | Session and viewer are removed, `onClose` fires once | | |
| External inventory open cleanup | Opening a real chest finalizes the packet session | | |
| Shift-click denial | **Changed:** top-slot shift-clicks are *routed* to the view, not denied; the packet is still cancelled | | |
| Number-key denial | **Changed:** top-slot number-key swaps are *routed* to the view; the packet is still cancelled | | |
| Drag denial | Denied, no callback, full resync | | |
| Double-click denial | Denied, no callback, full resync | | |
| Drop denial | Denied, no callback, full resync | | |
| Offhand swap denial | **Changed:** routed to the view; the packet is still cancelled | | |
| Cursor ghost-item correction | No item sticks to the cursor after any click | | |
| Bottom inventory visual correctness | The player's own items render correctly and snap back when clicked | | |
| No GUI display items in real server inventory contents | `/invsee` or a dump shows no GUI icons in any real inventory | | |
| Window id collision | Opening a GUI while a real chest is open closes the chest and never reuses its window id | | |
| World change / respawn | Session is finalized, no stale viewer keeps receiving GUI packets | | |

The four rows marked **Changed** deviate from the original AGENTS.md expectation. Those click modes are
deliberately routed into the click API rather than denied, because the packet is already cancelled at the
listener, so nothing vanilla can mutate. What *is* denied is drag, drop, double-click and any unrecognised
mode.

## Build note

`spotlessCheck` and `spotlessApply` do not run under this project's JDK 25 toolchain —
palantir-java-format throws `NoSuchMethodError` on `Log$DeferredDiagnosticHandler.getDiagnostics`. Formatting
in the packet backend is therefore maintained by hand: 4-space indentation, 120-column limit, no tabs.
