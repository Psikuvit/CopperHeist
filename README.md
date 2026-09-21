# Copper Heist

A team loot-heist minigame for Paper servers. Two teams, **Copper** and **Iron**, race to fill their vault. Players
grab loot around the arena and drop it into their dock chest, and **Copper Golems** carry it from the dock to the vault.
Golems oxidize over time and slow down, so someone has to keep scraping them. Meanwhile the other team is stealing,
sabotaging and drilling into your vault.

> Status: feature complete, but **nothing has been played on a real server yet.** Treat the first session as a test pass
> (see [`docs/playtest-guide.md`](docs/playtest-guide.md)).

Requires **Paper 26.2 or newer** and **Java 25**.

## Contents

- [Gameplay](#gameplay)
- [Look and feel](#look-and-feel)
- [Configuration and content](#configuration-and-content)
- [Selectable implementations](#selectable-implementations)
- [Arena setup](#arena-setup)
- [Stats, leaderboards and rewards](#stats-leaderboards-and-rewards)
- [Multi-server](#multi-server)
- [Administration](#administration)
- [Integrations and API](#integrations-and-api)
- [Commands and permissions](#commands-and-permissions)
- [Building](#building)

## Gameplay

**Match flow.** A match runs through phases with a boss bar, titles and sounds:

| Phase | What happens |
|---|---|
| Setup | No loot yet. Pick a role, shop, get ready (optionally confined to your base). |
| Collection | Loot spawns around the arena. |
| Heist | Vault Drills unlock in the shop. |
| Final Rush | Delivered loot is worth double, golems age faster, everyone glows. |

The team with the higher vault score when time runs out wins. A team that is left alone wins by forfeit after a grace
period; players who disconnect keep their slot for a while and can rejoin.

**Loot.** Five tiers (copper, gold, emerald, diamond and the Relic), each with its own value, weight and item, spawning at
common, rare and cache points. Carrying loot slows you down, and there is a carry limit. Loot dropped when you die goes into
a **loot bag** that anyone can pick up. Unclaimed loot slowly gains bonus value. **Relics** appear on a schedule and are
worth a lot; carry one and your carry limit changes, drop it and it respawns.

**Docks and vaults.** Your dock is a **copper chest** and your vault is made of normal **chests**. Drop loot into the dock and
your golems do what vanilla Copper Golems do: find the copper chest, take an item out, carry it in their hand and put it into a
chest that is empty or already holds that item. Loot that arrives in your vault chests is scored. Enemy dock chests need a
**lockpick** channel before they open.

**Golems.** Each team starts with a few and can buy more. They oxidize through four stages (fresh, exposed, weathered,
oxidized), getting slower each time until they freeze as a statue. Scrape them back with an axe, wax them with honeycomb, or
reset the whole dock area with a Storm Rod. Hitting an enemy golem stuns it and makes it drop what it carries. They run
on vanilla golem AI (pathfinding, chest opening, arm animations, held item), so the plugin only adds safety guards: loot taken
from the enemy dock or put into the enemy vault is put back, a golem in the enemy base is sent home, anything that isn't loot in a
golem's hand goes back to its dock, and a golem holding loot that no chest will accept has it placed in its vault. Stunned and fully
oxidized golems stand still, and oxidation lowers their speed.

**Roles** (chosen from the shop NPC or `/ch role`): Runner (default), Thief (temporary invisibility), Mechanic (better
scraping), Guard (defensive bonus near your base), Saboteur (reveals enemy golems). Roles have loadouts, passives and an
active ability with a cooldown; extra abilities such as Dash and Heal Pulse are included.

**Shop** (paid with the loot value you carry): honeycomb, wind charges, healing potion, oxidizer splash, a new golem, Storm
Rod, Alarm, and the Vault Drill (Heist phase only).

**Sabotage.** *Alarms* placed near your base flag intruders; each one shows a glowing marker box that only your own team can
see (enemies never do; `alarms.show-box` turns it off). A *Vault Drill* placed on an enemy vault door breaches it for a
timed window if attackers stay near it and defenders don't destroy it first. *Gust Pads* launch whoever steps on them.

**Clean state.** Players joining the server are reset (items, armor, offhand, potion effects, glow, vitals) and given the hub
kit (join compass and guide book). The compass opens the **arena picker**: a live menu of every arena, on this server and on
the rest of the network, coloured by whether you can join it right now, with its state, player count and preset. Entering an arena, spectating and leaving a match all wipe the previous context's items and
effects before the next state is applied, so nothing leaks between hub, match and spectating. Hub items can't be dropped or
moved into containers, and players outside a running match (hub, waiting room, results screen) take no damage or hunger, admins included; falling into
the void sends you back to a safe spot. All of this is under `lobby.*` in `config.yml`; only the join-time wipe skips admins
with `copperheist.admin.bypass`.

**Match rules.** Team-aware damage, spawn protection, a hidden enemy score option, an anti-turtle *vault decay* option,
respawn modes, and in-match command blocking.

## Look and feel

The whole plugin is styled from one palette, so you can rebrand it without touching any text.

- **Theme** (`theme:` in `config.yml`): twelve colours (`primary`, `secondary`, `accent`, `ok`, `bad`, `info`, `text`, `muted`,
  `dim`, `special`, `copper`, `iron`), applied on `/ch reload`. The bundled messages, scoreboard, shop, roles and guide use these
  as MiniMessage tags (`<primary>`, `<muted>` ...) plus symbol tags (`<arrow>`, `<check>`, `<cross>`, `<dot>`, `<bar>`, `<star>`,
  `<line>`). Every normal MiniMessage tag (`<red>`, `<#ff8800>`, `<gradient>`, click and hover) still works alongside them.
- **Framed menus** with consistent sounds: the shop shows each item's price, whether you can afford it and any lock, limit or
  cooldown, plus your carried balance, and refreshes as you buy; the role picker marks your current role; the arena picker updates live.
- Restyled scoreboard, tab list, chat prefix, action bar, leaderboards and stats output, including gold/silver/bronze top-three lines.

## Configuration and content

Almost everything is data, not code.

- `config.yml` - every number and switch. Layered per game: **per-arena overrides > preset > config.yml > default**.
- `roles.yml`, `loot.yml`, `shop.yml` - roles, loot tiers and shop entries with their actions.
- `presets/*.yml` - partial config copies an arena can switch on. Bundled: `classic`, `quick` (8 minutes) and `hardcore`
  (instant respawn, hidden score, vault decay). Add your own by dropping a file in the folder.
- Feature toggles (`features.*`) switch whole mechanics off: roles, shop, relics, alarms, vault drill, lockpicking, loot bags,
  final rush, unclaimed bonus.
- **Language packs** in `lang/<code>.yml`: every message is a key, players can see their own client language, and anything
  missing falls back to English. Role names, shop text and the guide book can be translated per language too.
- Scoreboard layout (`scoreboard.yml`) and the in-game guide book (`guide.yml`) are configurable.
- **Updates keep your files:** options added by a new version are written into `config.yml`, `lang/en.yml`, `scoreboard.yml`
  and `guide.yml` on start, with their comments; your values are never changed and the old file is kept as `<name>.bak`.
  `shop.yml`, `roles.yml` and `loot.yml` are not touched, since a missing entry there usually means you removed it on purpose.
  A file with a syntax error is reported in the log and the built-in defaults are used until it is fixed.
- `/ch reload` re-reads config, messages, scoreboard, shop, roles, loot, presets and the lobby kit.

Per-arena tweaks without editing files: `/ch arena options <arena> preset <name>`,
`... set <path> <value>` and `... clear <path>`.

## Selectable implementations

Each of these is a config key that picks one of several built-in choices; an unknown value logs a warning and uses the
default. Other plugins can register more through the API.

| Key | Choices |
|---|---|
| `npc.type` (shop keepers) | `mannequin` (custom skin), `villager`, `armor-stand`, `interaction`, `none` |
| `navigator.type` (hub navigators) | the same choices, set separately from the shop keepers |
| `ui.menu` | `chest` GUI or `dialog` (Paper's pop-up dialogs) |
| `loot.bag.visual` | `item-display`, `block-display`, `label-only` |
| `respawn.mode` | `spectator-wait`, `instant`, `ghost` |
| `reset.method` (arena cleanup) | `entities`, `snapshot` (also restores blocks) |
| `database.type` | `sqlite`, `mysql`, `none` |
| `rewards.provider` | `none`, `vault`, `commands` |

The Mannequin NPC supports a skin from a player name, a UUID or a raw texture value, plus pose, name and description options.

### Shop keepers and navigators

Shop keepers and the hub's game navigators are separate features built by the same NPC providers. Either can be a:

- **Villager** by profession (librarian, armorer, toolsmith, cartographer, cleric ...), clothing biome and level.
- **Mannequin** by skin (player name, UUID or texture), plus armor and held items.
- **Armor stand** by head (any block, or a player head with a skin), armor pieces (leather can be dyed) and held items. Players can't
  take or change what they wear.

**Shop keepers** belong to the cosmetics system: each team's keepers are built from the rarest *shop keeper* cosmetic (category `npc`,
effect `shop-keeper`) that a teammate has equipped, and without one from the plain `npc.type` settings. A cosmetic's params are the whole
look, so owners add or change keeper styles in `cosmetics.yml` - about thirty ship. `/ch arena addshop <arena> <team>` adds another keeper spot
(`setshop` replaces them with one, `clearshops` removes them all).

**Navigators** are hub NPCs that open the arena picker: `/ch navigator create <id> [look]`, `look`, `remove` and `list`. Their looks are
in `navigator-looks.yml`, they are saved in `navigators.yml` and re-created if they go missing, and `navigator.type` is the default kind.

## Arena setup

- `/ch setup <arena>` prints a clickable checklist: what is done, what is missing, and a `[set]` button for every step.
- The steps behind it are `/ch arena ...` commands: lobby, spectator point, bounds, spawns, copper-chest docks, chest vaults, vault door,
  golem idle point, shop keepers (as many as you like), base and vault regions, loot points (common/rare/cache), relic points and gust pads.
- `/ch arena validate <arena>` explains what blocks enabling; `/ch arena enable <arena>` turns it on.
- **Snapshots:** `/ch arena snapshot <arena>` saves every block in the arena's bounds; with `reset.method: snapshot` they
  are restored after each match, spread over several ticks. `/ch arena paste <arena>` builds a snapshot into the world.
- **Centred points:** lobby, spectator, spawns, golem idle, shop NPC, waypoints, loot and relic points and the hub spawn are snapped
  to the middle of their block (x.5, z.5) when set and when loaded, so loot, NPCs and golems always spawn centred, even in older arena files.
- **Regions:** optional base regions (confinement, alarm and Guard rules) and sealed vault regions.
- A ready-made, fully configured **test arena** is generated by `tools/gen_testmap.py` into `testmap/` (see `testmap/README.md`).

## Levels, XP and coins

Every match pays **XP** (which sets your level and rank) and **coins** (for cosmetics). Amounts are in `progress.yml`: taking part,
win or loss, MVP, a **first win of the day** bonus, and per loot value, steal, relic, scrape, kill and drill. Level-ups show a
title, play a sound, pay coins and can hand out a cosmetic (`level-rewards`). Ranks (Rookie, Pickpocket, Thief, Safecracker,
Mastermind, Legend) and the level curve are configurable, and `/ch admin booster <x> <minutes>` runs a temporary XP and coin boost.

- `/ch level` shows your level, rank, XP bar and coins; `/ch top level` ranks players by level.
- Level, rank, XP bar and coins appear on the hub scoreboard and the in-match tab list, and as `%copperheist_level%`, `%_rank%`,
  `%_xp_next%`, `%_coins%` and `%_xp%`.
- XP and coins are stored with the player's stats (so they follow the player across the network) and need stats to be enabled.

### Goals: quests, achievements and the daily reward

- **Quests** (`quests.yml`): three daily quests and one weekly quest, picked from pools by the day or week number, so they are the same for
  everyone and every server of a network. They change at midnight UTC (weekly: Monday) and count finished matches: matches, wins, MVPs,
  loot delivered, steals, kills, scrapes, relics, vault drills. A quest pays XP, coins and optionally a cosmetic the moment it is done.
- **Achievements** (`achievements.yml`): about 18 permanent goals on lifetime stats and level. Unlocking one shows a real **toast** with its
  own icon, title and description (falling back to a title if the server can't show it), plus a chat line, and pays XP, coins and
  optionally a cosmetic. Secret ones stay "???" until earned.
- **Daily reward** (`daily.yml`): a login streak that grows every consecutive day (a missed day resets it) and a reward you claim once a day
  with `/ch daily` or the clickable message on join. The list repeats every 7 days, and day 7 gives a cosmetic by default.
- The **goals screen** (hub Writable Book, `/ch quests`) shows all of it, with the achievements one click away (`/ch achievements`).
- **Vanilla advancements are switched off** so they don't compete: no "X has made the advancement" chat, and the vanilla advancements are
  removed from the server (no toasts, an empty advancements screen; recipes are kept). Both are in the `advancements:` block of `config.yml`.
  Datapack reloads bring the advancements back, so the plugin removes them again after every server load.

**Cosmetics**: `cosmetics.yml` defines cosmetics by category (arrow trail, kill effect, victory, golem, shop
skin, title, join effect), rarity, coin price, level and permission. Players buy or earn them; server owners can also sell any of them by
giving the permission `copperheist.cosmetic.<id>` from a store plugin. Other plugins add effects through the API
(`registerCosmeticEffect`). `/ch admin cosmetic give|take <player> <id>` manages them.

The **cosmetics menu** (hub Ender Chest item, `/ch cosmetics`, or the chest button in the shop) has a tile per category showing how
many you own and what is equipped, plus three **featured** picks that change every day at midnight UTC. Each category is a paged grid in
rarity colours; click to equip or unequip, click something you can afford to buy it (after a confirm screen), right-click to preview
it just for you. One button switches all cosmetic effects off for you.

**Previewing a shop keeper look** is cinematic: an admin stands where the keeper should appear, facing the way it should face, and runs
`/ch setpreview`. When a player right-clicks a shop keeper cosmetic in the hub, that keeper is spawned there for them alone, and they
are put in spectator mode watching an invisible camera in front of it - they can't move or turn, and nobody else sees the keeper or them.
Sneaking, `cosmetics.preview-seconds`, quitting or a server stop ends it and puts them back where they were with their game mode.
`cosmetics.preview-distance` sets how far the camera sits. Previews only work in the hub, not in an arena.

**What ships** (about 35 starter cosmetics in `cosmetics.yml`, all editable):

| Category | Effect | Plays |
|---|---|---|
| Arrow trails | `particle-trail` | behind arrows, wind charges, tridents and thrown potions (capped server-wide so busy matches stay smooth) |
| Kill effects | `particle-burst`, `firework`, `lightning` | where you killed someone |
| Victory effects | the same, plus spirals and confetti | for the winning team at the end of a match |
| Golem skins | `golem-hat`, `particle-burst` | on your team's golems - the rarest one anyone on the team has equipped |
| Shop keepers | `shop-keeper` | the team's shop NPC is built from the cosmetic: a villager profession, a Mannequin skin or an armor stand outfit (type, name, skin, head, armor, held items) |
| Titles | - | in chat with `chat.enabled: true`, and as `%copperheist_title%` for other chat plugins |
| Join effects | the same as kill effects | when you join the server |
| Death messages | `death-message` | your own line announced to the match when you defeat someone, replacing the plain death message (`{killer}` and `{victim}` are filled in) |

Everything is visual only: cosmetic fireworks and lightning can never hurt anyone. Each player can switch effects off for themselves.

## Stats, leaderboards and rewards

- Persistent per-player stats: games, wins, losses, loot delivered and stolen, steals, relics, kills, deaths, golems scraped,
  drills completed and destroyed, MVPs. Stored in SQLite (default) or MySQL, written in batches so gameplay never waits on
  the database. Match history is recorded too.
- `/ch stats [player]` and `/ch top [stat]`.
- `/ch leaderboard create <id> <stat>` places a floating text board in the world; boards refresh on a timer from a cache.
- **Rewards** at match end for the winning team, the losing team and the MVP: Vault money or console commands.
- **PlaceholderAPI** placeholders such as `%copperheist_wins%`, `%copperheist_top_wins_1_name%`, `%copperheist_team%`,
  `%copperheist_arena_players_<arena>%`.

## Multi-server

Several Paper servers behind BungeeCord or Velocity can act as one network, using **Redis** for live coordination and
**MySQL** for shared stats.

- Every server publishes its arenas' status; `/ch list` shows arenas from all servers.
- `/ch join` sends a player to a joinable arena on another server when none is available locally, and
  `/ch join <server>.<arena>` targets a specific one. The destination puts them straight into the arena.
- **Player data follows the player.** Stats, XP, coins and the profile (first join, flags, owned and equipped items) live in the
  shared MySQL database. When a player moves between servers, the new server waits (via Redis) until the old one has saved and
  released them, then loads - so nothing is read stale or lost, and coins can't be spent twice. Counters are written as additions,
  so two servers never overwrite each other. `network.profile-handoff-seconds` caps the wait (a crashed server can't lock anyone
  out). Without Redis it works on one server; several servers on one MySQL *without* Redis can read stale data on a fast switch.
- Maintenance mode and admin broadcasts cover the whole network.
- A lobby server is just a server with no arenas. If Redis is unreachable the plugin runs as a single server.

Setup steps and limits: [`docs/multi-server.md`](docs/multi-server.md).

## Administration

Under `/ch admin` (permission `copperheist.admin.manage`): list players in a match, kick a player from a match, teleport to arena
points, maintenance mode, network broadcasts, edit or reset player stats, disable or delete an arena, and view the network
(`servers`). Also `/ch info` for a health check, `/ch forcestart`, `/ch forcestop`, `/ch setphase`, `/ch spawnrelic` and
`/ch giveloot` for testing.

**Golem debugging** (`/ch admin debug ...`, off by default and free when off): `golems on|off` logs what vanilla's golems do
(state changes, what they pick up and put down, the nearest chest) and what the plugin's safety guards do about it;
`visuals on|off` shows each golem's state on its label, draws a particle line to where it is heading and marks every dock and vault
chest; `dump` prints a snapshot of every golem plus pending chest reservations. `verbose` is reserved for extra detail.

## Integrations and API

- Optional soft dependencies: **PlaceholderAPI** and **Vault**. Missing plugins never cause errors.
- **World rules:** in arena worlds, natural mob spawning is off and PvP only works between players in the same running match
  (`world-rules.*`, scope `arena` or `all`).
- **Developer API** (`CopperHeistAPI` through Bukkit's ServicesManager): read-only game views, stats and top lists, and
  registration of shop actions, role abilities and NPC, menu, respawn, loot-bag and reset providers. Custom events cover the
  match lifecycle (loot delivered or stolen, relics, alarms, vault drills, phase changes, match end).
  See [`docs/api.md`](docs/api.md).
- **GUI framework:** every chest menu is a `Menu` opened through one `MenuManager`. It cancels all clicks and drags, routes each click
  to the button's action, ignores click spam, redraws menus that ask to, and closes them on shutdown. A new menu only says what
  it looks like and what its buttons do (`plugin.getMenus().open(player, new MyMenu(plugin, player))`).

## Commands and permissions

Player commands: `/ch join [arena]`, `leave`, `list`, `shop`, `role [role]`, `spectate <arena>`, `stats [player]`, `top [stat]`.

Admin commands are grouped by permission:

| Permission | Default | Covers |
|---|---|---|
| `copperheist.play` | everyone | joining and playing |
| `copperheist.stats` | everyone | `/ch stats`, `/ch top` |
| `copperheist.spectate` | everyone | `/ch spectate` |
| `copperheist.admin.arena` | op | `/ch arena ...`, `/ch setup`, `/ch leaderboard` |
| `copperheist.admin.debug` | op | force start/stop, set phase, spawn relic, give loot, `/ch info` |
| `copperheist.admin.reload` | op | `/ch reload` |
| `copperheist.admin.manage` | op | `/ch admin ...` |
| `copperheist.admin.bypass` | op | any command in a match, joining during maintenance |
| `copperheist.admin.*` / `copperheist.*` | op | everything |

## Building

```bash
mvn clean package
```

Needs JDK 25. The result is `target/CopperHeist-1.0.jar` (Jedis is shaded in). `mvn test` runs the small unit test suite
(settings layering, bundled YAML validity, presets, stat names, theme tags).

More docs: [`docs/playtest-guide.md`](docs/playtest-guide.md), [`docs/multi-server.md`](docs/multi-server.md),
[`docs/api.md`](docs/api.md).
