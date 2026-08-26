# HyperEnderChest

HyperEnderChest is a Paper plugin that keeps normal personal Ender Chests and lets two players share an additional Ender Chest.

It also allows hoppers to insert and extract items from selected Ender Chest blocks. Hopper access must be enabled on each block with a special one-use wooden axe.

## Requirements

- Paper 26.2
- Java 25 or newer

## Features

- Normal personal Ender Chests
- One shared Ender Chest between two consenting players
- Real-time shared inventory for both players
- Persistent shared items and player settings
- Personal or shared view selection
- Hopper insertion and extraction
- Per-block hopper access toggle
- One-use hopper configuration axe
- Permissions and command tab completion
- No NMS dependencies

## Installation

1. Download or build `HyperEnderChest-1.0.0.jar`.
2. Place the JAR in the server's `plugins` directory.
3. Start the Paper server.
4. Edit `plugins/HyperEnderChest/config.yml` if needed.
5. Restart the server after changing the configuration.

## Commands

All commands use `/enderchest`. The shorter `/hec` alias is also available.

| Command | Description |
|---|---|
| `/enderchest open` | Opens the currently selected personal or shared Ender Chest. |
| `/enderchest share <player>` | Sends a share request to an online player. |
| `/enderchest accept [player]` | Accepts a pending share request. |
| `/enderchest deny [player]` | Denies a pending share request. |
| `/enderchest view personal` | Selects the personal vanilla Ender Chest. |
| `/enderchest view shared` | Selects the shared Ender Chest. |
| `/enderchest unshare` | Revokes the current share without deleting archived shared items. |
| `/enderchest hopperaxe` | Gives a one-use wooden axe for toggling hopper access on one Ender Chest block. |
| `/enderchest reload` | Reloads and validates `config.yml`. |

## Creating a Shared Ender Chest

1. Player A runs:

   ```text
   /enderchest share PlayerB
   ```

2. Player B accepts:

   ```text
   /enderchest accept PlayerA
   ```

3. Both players are switched to the shared view.
4. Either player can open it with:

   ```text
   /enderchest open
   ```

Both players use the same live inventory. Changes appear immediately for everyone viewing it.

A player can only belong to one share at a time.

## Switching Between Personal and Shared Storage

Select the normal personal Ender Chest:

```text
/enderchest view personal
```

Select the shared Ender Chest:

```text
/enderchest view shared
```

The selected view persists across logins and server restarts.

When the personal view is selected, physical Ender Chest blocks retain normal vanilla behavior. When the shared view is selected, opening a physical Ender Chest opens the shared inventory.

## Enabling Hopper Access

Hopper access is disabled on an Ender Chest block until it is configured.

1. Select which inventory the block should use:

   ```text
   /enderchest view personal
   ```

   or:

   ```text
   /enderchest view shared
   ```

2. Get the configuration axe:

   ```text
   /enderchest hopperaxe
   ```

3. Hold the special wooden axe in the main hand.
4. Right-click the Ender Chest block.
5. The block is linked to the selected inventory and hopper access is enabled.
6. The axe is removed immediately after use.

Use another configuration axe on the same block to disable hopper access.

Once enabled, connected hoppers can both insert and extract items.

### Personal Ender Chest Limitation

A hopper linked to a personal Ender Chest works only while its owner is online. Paper's public API does not safely expose offline player Ender Chest inventories.

Shared Ender Chests do not have this limitation while the share remains active.

## Revoking a Share

Run:

```text
/enderchest unshare
```

Both players return to personal view. Shared items are archived instead of deleted, preventing accidental item loss.

Ender Chest blocks linked to the revoked share stop working with hoppers.

## Permissions

| Permission | Description | Default |
|---|---|---|
| `hyperenderchest.use` | Uses commands and opens plugin inventories. | Everyone |
| `hyperenderchest.share` | Creates, accepts, denies, and revokes shares. | Operators |
| `hyperenderchest.hopper` | Gets the configuration axe and toggles hopper access. | Operators |
| `hyperenderchest.reload` | Reloads plugin configuration. | Operators |

Permissions can be managed with any Paper-compatible permissions plugin.

## Configuration

Default `config.yml`:

```yaml
inventory-size: 27
request-expiry-seconds: 60
request-cooldown-seconds: 30
require-share-permission: true
require-hopper-permission: true
logging:
  share-events: true
  hopper-transfers: false
```

| Setting | Description |
|---|---|
| `inventory-size` | Shared inventory size. Must be a multiple of 9 between 9 and 54. |
| `request-expiry-seconds` | Time before a share request expires. |
| `request-cooldown-seconds` | Delay before a player can send another request. |
| `require-share-permission` | Requires `hyperenderchest.share` when enabled. |
| `require-hopper-permission` | Requires `hyperenderchest.hopper` when enabled. |
| `logging.share-events` | Logs share creation and revocation. |
| `logging.hopper-transfers` | Logs shared Ender Chest hopper transfers. |

## Data Storage

Plugin data is stored in `plugins/HyperEnderChest/cache.yml`:

- `Players` stores active shares and selected views.
- `Vaults` stores shared Ender Chest contents.
- Hopper block links remain in each Ender Chest block's persistent data.

The plugin creates missing cache sections and validates YAML when loading. Do not edit `cache.yml` while the server is running.

## Project Structure

```text
src/main/java/it/hyperenderchest/
├── SharedEnderChestPlugin.java   Paper runtime entry point
├── command/                      Player commands and tab completion
├── config/                       Validated reloadable settings
├── inventory/                    Custom inventory holders
├── listener/                     Block, inventory, and hopper events
├── model/                        Stable domain identifiers
├── service/                      Sharing and inventory state
└── storage/                      Persistent YAML storage
```

The plugin uses the standard `JavaPlugin#onEnable` and `onDisable` runtime hooks. Paper bootstrappers run earlier, but they are experimental and intended for registry, data pack, constructor, or classpath setup. HyperEnderChest does not need those early facilities.

## Building from Source

Use Java 25 and the included Gradle wrapper.

Windows:

```text
gradlew.bat clean test build
```

Linux or macOS:

```text
./gradlew clean test build
```

The resulting plugin is created in:

```text
build/libs/HyperEnderChest-1.0.0.jar
```

## Basic Usage Example

```text
PlayerA: /enderchest share PlayerB
PlayerB: /enderchest accept PlayerA
PlayerA: /enderchest view shared
PlayerA: /enderchest hopperaxe
```

Player A then right-clicks a physical Ender Chest with the special axe. Hoppers connected to that block can now insert into and extract from the shared inventory.
