# Konf

Configs on [night-config](https://github.com/TheElectronWill/night-config):
defaults auto-fill with comments, ranges clamp, invalid edits reset to default.

```groovy
implementation "team.terrafirmagreg:konf-core:version"
implementation "team.terrafirmagreg:konf-toml:version" // konf-json / konf-hocon / konf-yaml
```

```java
ConfigFile cfg = Configs.toml("server.toml");

var port = cfg.value("server.port", 25565)
        .range(1, 65535)
        .comment("Listen port", "Restart required");

port.get();     // file value, or default written on first access
port.set(8080); // validates, writes, saves
cfg.sync();     // materializes every declared value
cfg.load();     // re-reads file, reapplies overlays
```

## Formats

| Format | Module       | Extensions       | Comments | Open                  |
|--------|--------------|------------------|----------|-----------------------|
| TOML   | `konf-toml`  | `.toml`          | `#`      | `Configs.toml(file)`  |
| JSON   | `konf-json`  | `.json`          | no       | `Configs.json(file)`  |
| HOCON  | `konf-hocon` | `.conf` `.hocon` | `#`      | `Configs.hocon(file)` |
| YAML   | `konf-yaml`  | `.yaml` `.yml`   | no       | `Configs.yaml(file)`  |

`of(file)` detects by extension, `memory(format)` keeps no file, `builder(file)` adds
autoreload/strict/onSave/onLoad/defaultResource/defaultData/charset/writing/parsing modes.

## Types

| Declaration                                | Java type                                                                   | Stored as       |
|--------------------------------------------|-----------------------------------------------------------------------------|-----------------|
| `value("debug", false)`                    | `boolean`                                                                   | `debug = false` |
| `value("number", 1)`                       | `byte` `short` `int` `long` `float` `double`                                | `number = 1`    |
| `value("motd", "Hi")`                      | `String`                                                                    | `"Hi"`          |
| `value("mode", Mode.NORMAL)`               | `enum`                                                                      | `"NORMAL"`      |
| `value("names", List.of("a"))`             | `List<String>`                                                              | `["a"]`         |
| `value("dur", Duration.ofMinutes(5))`      | `Duration` `Period` `Instant` `Local*` `OffsetDateTime`                     | `"PT5M"`        |
| `value("zone", ZoneId.of("UTC"))`          | `ZoneId` `Charset` `Locale`                                                 | `"UTC"`         |
| `value("id", uuid)`                        | `UUID` `URI` `Path` `Pattern`                                               | `"1234-…"`      |
| `value("big", new BigDecimal("1.5"))`      | `BigInteger` `BigDecimal`                                                   | `"1.5"`         |
| `value("modes", defs, ofEnum(Mode.class))` | `List<T>` via `ValueCodec`                                                  | `["NORMAL",…]`  |
| `value("x", def, myCodec)`                 | custom `ValueCodec` (`ofString` `ofEnum` `ofNumber` `ofType` `listOf` `passthrough`) | `codec.store()` |
| `value("app", new App(), bean(App::new))`  | POJO: all fields become a table                                             | `[app]` `…`     |
| `value("pt", new Pt(0), bean(Pt.class))`   | `record`: strict schema                                                     | `[pt]` `…`      |

```java
cfg.section("dump.assets").value("x", d);  // declares dump.assets.x; sections compose
builder(file).strict();                    // InvalidConfigException instead of reset
cfg.verify();                              // rejects undeclared file keys
cfg.overlayEnv("APP_");                    // APP_DUMP__ASSETS__X=1 → dump.assets.x
```

`./gradlew build` → `build/libs/konf-*.jar`
