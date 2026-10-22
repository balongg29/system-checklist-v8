# Farm Checklist (client mod)

Minecraft 26.2, Fabric. Requires Fabric API and Java 25. Client-only: every player installs it themselves.

Press K (rebindable in Controls) to open your checklists.

- Picker: New Checklist, Import from Clipboard, and your saved checklists with a status square
  (gray = not started, yellow = in progress, green = complete), a checked/total count, and item icons.
- Checklist: click a row to check it off. Scan checks off what's in your inventory. Copy, Add Item, Complete/Reopen, Delete.
  Click the pencil on a row to change it to another item (e.g. oak trapdoor -> spruce trapdoor), change how many you Need,
  type how many you Have (use the up/down arrows, or the Up/Down keys; hold Shift for 10), or Remove the row. Scan never lowers a Have you typed.
- Add Item: a creative-inventory style grid. Tabs: All, Blocks, Items, Types. Types holds fence, wool, concrete, slab, stairs, wall,
  trapdoor, door, planks, carpet, glass, bed, banner and more: click a type, then click the wood / color / material you want.
- Import from Clipboard checks every line against the game's real item list. Lines that are not real items (like "temporary_blocks")
  are left out; a review screen shows them with a "Did you mean...?" guess you can accept with Use. Scaffolding and other real blocks stay.
- Buttons are item icons in columns at the sides of the panel (hover for the name). On narrow screens they sit in a row inside the panel.
- Each row has Have arrows (up/down, Shift = 10) next to the pencil.
- Share (ender pearl): copies a code with the list and your progress; your friend pastes it with Import and gets their own copy.
- Share (ender pearl) opens a menu: list only or list + progress, then Copy code or Send in chat. Send whispers the list to the
  players you type (comma separated) or to everyone online. If they have this mod, they see "Michael shared ... [Click to add]" in chat and
  one click adds it to their checklists. Chat has no file attachments, so the list travels as small hidden pieces.
- Collaborate (player head): same code, but marked as a shared project. Importing a code for a project you already have merges
  it instead of copying (new rows added, check marks combined, bigger Have wins). The hopper in a checklist (Sync) merges a code
  from the clipboard. Pass codes back and forth to sync; it is not live (no server).
- Scroll bars are wide and can be dragged.

Checklists are plain text files in .minecraft/farmlist/transcriptions/ (first line "# Title", then "item_id count"). Also accepted: "fence:spruce 64", "wall:cobblestone 64", "oak trapdoor x64".
A bare "wall 64" means a wall whose material you pick later with the pencil.
Checked state is saved in .minecraft/farmlist/progress/.

Build: gradle build  (or let GitHub Actions build it; download the jar from the run's artifacts)
