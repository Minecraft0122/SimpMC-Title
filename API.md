# SimpMC-Title Plugin API

SimpMC-Title exposes a versioned Java API as a Bukkit service. Integrations can read title/profile data, grant or equip titles, manage shared title groups, update player names, and access the built-in currency.

## Compile Dependency

The API classes are included in the SimpMC-Title plugin JAR. To use them in another Maven plugin, first build and install this project into your local Maven repository:

```powershell
mvn install
```

Then add the dependency to the integrating plugin's `pom.xml`:

```xml
<dependency>
    <groupId>cn.simpmc</groupId>
    <artifactId>SimpMC-Title</artifactId>
    <version>1.3.0</version>
    <scope>provided</scope>
</dependency>
```

Do not shade or relocate SimpMC-Title API classes. Add the plugin as a load dependency in the integrating plugin's `plugin.yml`:

```yaml
depend:
  - SimpMC-Title
```

Use `softdepend` instead when the integration is optional and handle an unavailable service as shown below.

## Obtain the Service

```java
import cn.simpmc.title.api.SimpMCTitleAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExamplePlugin extends JavaPlugin {
    private SimpMCTitleAPI titleApi;

    @Override
    public void onEnable() {
        titleApi = Bukkit.getServicesManager().load(SimpMCTitleAPI.class);
        if (titleApi == null) {
            getLogger().warning("SimpMC-Title API is unavailable; title integration is disabled.");
            return;
        }
        getLogger().info("Using SimpMC-Title API v" + titleApi.getApiVersion());
    }
}
```

`SimpMCTitleAPI.API_VERSION` identifies the API contract. Check this value when an integration depends on a newer API revision.

On Paper, call synchronous API methods on the server thread. On Folia, call configuration/group mutations and Vault reads on the global region scheduler, and read an online player's profile on that player's entity scheduler. Methods returning `CompletableFuture` handle player scheduling themselves.

## Read Data

```java
titleApi.getTitle("custom-...").ifPresent(title -> {
    String id = title.id();
    String display = title.display();
    String description = title.description();
});

String displayedTitle = titleApi.getTitleDisplay(player.getUniqueId());
String fullName = titleApi.getFullDisplay(player.getUniqueId(), player.getName());
Map<String, Long> owned = titleApi.getOwnedTitles(player.getUniqueId());
long internalBalance = titleApi.getInternalBalance(player.getUniqueId());
```

`TitleInfo.display()` and `getTitleDisplay()` retain the configured color syntax such as `&7`. `getDisplayName()` and `getFullDisplay()` return Bukkit legacy text with section-sign color codes. An owned-title expiry value is an epoch timestamp in milliseconds; `0` means permanent. Returned lists, sets and maps are immutable snapshots.

## Grant and Equip

Player mutations return `CompletableFuture<Boolean>` and execute on the target player's entity scheduler, including on Folia. A duration of `0` means permanent. These methods require an online `Player`.

```java
Player target = Bukkit.getPlayerExact("ExamplePlayer");
if (target != null) {
    titleApi.registerCustomTitle("&7[&bEvent Winner&7]").ifPresent(titleId ->
        titleApi.forceEquipTitle(target, titleId, 7).thenAccept(success -> {
            if (!success) {
                getLogger().warning("Could not equip the event title.");
            }
        })
    );
}
```

- `grantTitle(player, id, days)` adds ownership without equipping the title.
- `equipTitle(player, id)` equips only when the player owns it or has permission to use it.
- `forceEquipTitle(player, id, days)` grants and equips the title, bypassing ownership checks.
- `takeTitle(player, id)` removes ownership and clears it if currently equipped.
- `clearTitle(player)` clears the player's personal active title. A shared group title remains visible until the player is removed from the group.

`registerCustomTitle(display)` creates a validated custom definition in `config.yml` when it does not already exist, and returns its generated ID. Call it on the server's main thread (or Folia global region scheduler), because it updates and reloads plugin configuration. Pass the returned ID to grant/equip methods; do not construct generated IDs yourself.

## Title Groups

Group definitions are stored in `groups.yml`. Group mutation methods are synchronous and should be called on the server's main thread (or Folia global region scheduler). UUID-based membership methods support offline players and refresh a member's display when they are online.

```java
String group = "event-winners";
titleApi.createTitleGroup(group);
titleApi.setTitleGroupTitle(group, "&7[&6Winners&7]");
titleApi.addPlayerToTitleGroup(group, playerUuid);

String assignedGroup = titleApi.getTitleGroup(playerUuid).orElse(null);
```

Each player can belong to one group. A group title takes priority over the member's personal title. `getTitleGroups()` returns the current group names.

## Names and Currency

```java
titleApi.setNickname(target, "EventPlayer").thenAccept(success -> { /* handle result */ });
titleApi.setNameColor(target, "&a").thenAccept(success -> { /* handle result */ });
titleApi.resetName(target);

titleApi.giveInternalBalance(target, 25).thenAccept(success -> { /* handle result */ });
long balance = titleApi.getInternalBalance(target.getUniqueId());
boolean vaultReady = titleApi.isGoldEconomyAvailable();
double gold = titleApi.getGoldBalance(target); // NaN when Vault economy is unavailable
```

Name updates use the plugin's configured nickname validation. Name colors accept color codes (including RGB/gradient formats supported by the plugin); formatting-only codes such as `&l` are rejected. Internal-currency mutations require an online player and run on that player's entity scheduler.

## API Surface

Public types are in `cn.simpmc.title.api`:

- `SimpMCTitleAPI`: service interface.
- `TitleInfo`: immutable title definition snapshot.

The implementation class is not part of the integration contract. Always retrieve the interface from Bukkit's `ServicesManager` instead of depending on SimpMC-Title implementation classes.
