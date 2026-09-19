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

**Docks and vaults.** Drop loot into your dock chest and your golems collect it, walk it along a waypoint route and deposit
it in the vault chests, which scores it. Enemy dock chests need a **lockpick** channel before they open.

**Golems.** Each team starts with a few and can buy more. They oxidize through four stages (fresh, exposed, weathered,
oxidized), getting slower each time until they freeze as a statue. Scrape them back with an axe, wax them with honeycomb, or
reset the whole dock area with a Storm Rod. Hitting an enemy golem stuns it and makes it drop what it carries. They play
arm animations when picking up and dropping off, hold the item they carry, and recover when stuck or knocked off route.

**Roles** (chosen from the shop NPC or `/ch role`): Runner (default), Thief (temporary invisibility), Mechanic (better
scraping), Guard (defensive bonus near your base), Saboteur (reveals enemy golems). Roles have loadouts, passives and an
active ability with a cooldown; extra abilities such as Dash and Heal Pulse are included.

**Shop** (paid with the loot value you carry): honeycomb, wind charges, healing potion, oxidizer splash, a new golem, Storm
Rod, Alarm, and the Vault Drill (Heist phase only).

**Sabotage.** *Alarms* placed near your base flag intruders. A *Vault Drill* placed on an enemy vault door breaches it for a
timed window if attackers stay near it and defenders don't destroy it first. *Gust Pads* launch whoever steps on them.

**Clean state.** Players joining the server are reset (items, armor, offhand, potion effects, glow, vitals) and given the hub
kit (join compass and guide book). Entering an arena, spectating and leaving a match all wipe the previous context's items and
effects before the next state is applied, so nothing leaks between hub, match and spectating. Hub items can't be dropped or
moved into containers, and players outside a running match (hub, waiting room, results screen) take no damage or hunger, admins included; falling into
the void sends you back to a safe spot. All of this is under `lobby.*` in `config.yml`; only the join-time wipe skips admins
with `copperheist.admin.bypass`.

**Match rules.** Team-aware damage, spawn protection, a hidden enemy score option, an anti-turtle *vault decay* option,
respawn modes, and in-match command blocking.

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
- `/ch reload` re-reads config, messages, scoreboard, shop, roles, loot, presets and the lobby kit.

Per-arena tweaks without editing files: `/ch arena options <arena> preset <name>`,
`... set <path> <value>` and `... clear <path>`.

## Selectable implementations

Each of these is a config key that picks one of several built-in choices; an unknown value logs a warning and uses the
default. Other plugins can register more through the API.

| Key | Choices |
|---|---|
| `npc.type` (shop / role NPC) | `mannequin` (custom skin), `villager`, `armor-stand`, `interaction`, `none` |
| `ui.menu` | `chest` GUI or `dialog` (Paper's pop-up dialogs) |
| `loot.bag.visual` | `item-display`, `block-display`, `label-only` |
| `respawn.mode` | `spectator-wait`, `instant`, `ghost` |
| `reset.method` (arena cleanup) | `entities`, `snapshot` (also restores blocks) |
| `database.type` | `sqlite`, `mysql`, `none` |
| `rewards.provider` | `none`, `vault`, `commands` |

The Mannequin NPC supports a skin from a player name, a UUID or a raw texture value, plus pose, name and description options.

## Arena setup

- `/ch setup <arena>` prints a clickable checklist: what is done, what is missing, and a `[set]` button for every step.
- The steps behind it are `/ch arena ...` commands: lobby, spectator point, bounds, spawns, dock and vault chests, vault door,
  golem idle point and waypoints, shop NPC point, base and vault regions, loot points (common/rare/cache), relic points and gust pads.
- `/ch arena validate <arena>` explains what blocks enabling; `/ch arena enable <arena>` turns it on.
- **Snapshots:** `/ch arena snapshot <arena>` saves every block in the arena's bounds; with `reset.method: snapshot` they
  are restored after each match, spread over several ticks. `/ch arena paste <arena>` builds a snapshot into the world.
- **Regions:** optional base regions (confinement, alarm and Guard rules) and sealed vault regions.
- A ready-made, fully configured **test arena** is generated by `tools/gen_testmap.py` into `testmap/` (see `testmap/README.md`).

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
- Maintenance mode and admin broadcasts cover the whole network.
- A lobby server is just a server with no arenas. If Redis is unreachable the plugin runs as a single server.

Setup steps and limits: [`docs/multi-server.md`](docs/multi-server.md).

## Administration

Under `/ch admin` (permission `copperheist.admin.manage`): list players in a match, kick a player from a match, teleport to arena
points, maintenance mode, network broadcasts, edit or reset player stats, disable or delete an arena, and view the network
(`servers`). Also `/ch info` for a health check, `/ch forcestart`, `/ch forcestop`, `/ch setphase`, `/ch spawnrelic` and
`/ch giveloot` for testing.

## Integrations and API

- Optional soft dependencies: **PlaceholderAPI** and **Vault**. Missing plugins never cause errors.
- **World rules:** in arena worlds, natural mob spawning is off and PvP only works between players in the same running match
  (`world-rules.*`, scope `arena` or `all`).
- **Developer API** (`CopperHeistAPI` through Bukkit's ServicesManager): read-only game views, stats and top lists, and
  registration of shop actions, role abilities and NPC, menu, respawn, loot-bag and reset providers. Custom events cover the
  match lifecycle (loot delivered or stolen, relics, alarms, vault drills, phase changes, match end).
  See [`docs/api.md`](docs/api.md).

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
(settings layering, bundled YAML validity, presets, stat names).

More docs: [`docs/playtest-guide.md`](docs/playtest-guide.md), [`docs/multi-server.md`](docs/multi-server.md),
[`docs/api.md`](docs/api.md).
