# Konf

Сonfigs on [night-config](https://github.com/TheElectronWill/night-config):
defaults auto-fill with comments, ranges clamp, invalid edits reset to default.

```java
ConfigFile cfg = Configs.toml("server.toml");

var port = cfg.value("server.port", 25565)
        .range(1, 65535)
        .comment("Listen port", "Restart required")
        .onChange(v -> restartServer(v));

var mode = cfg.value("server.mode", Mode.NORMAL);
var names = cfg.value("server.names", List.of("a", "b"));
var timeout = cfg.value("server.timeout", Duration.ofMinutes(5));

port.get();
port.set(8080);
cfg.sync();
cfg.reload();
```

## Formats

| Format | Extensions       | Comments | Open                  |
|--------|------------------|----------|-----------------------|
| TOML   | `.toml`          | `#`      | `Configs.toml(file)`  |
| JSON   | `.json`          | —        | `Configs.json(file)`  |
| HOCON  | `.conf` `.hocon` | `#`      | `Configs.hocon(file)` |
| YAML   | `.yaml` `.yml`   | —        | `Configs.yaml(file)`  |

Also: `of(file)` — detect by extension · `memory(format)` — no file ·
`builder(file)` — autoreload, defaultResource, charset, writing/parsing.


## Types — all via `value`

| Declaration                                | Java type                                                   | Stored as       |
|--------------------------------------------|-------------------------------------------------------------|-----------------|
| `value("debug", false)`                    | `boolean`                                                   | `debug = false` |
| `value("number", 1)`                       | `byte` `short` `int` `long` `float` `double`                | `number = 1`    |
| `value("motd", "Hi")`                      | `String`                                                    | `"Hi"`          |
| `value("mode", Mode.NORMAL)`               | `enum`                                                      | `"NORMAL"`      |
| `value("names", List.of("a"))`             | `List<String>`                                              | `["a"]`         |
| `value("dur", Duration.ofMinutes(5))`      | `Duration` `Period` `Instant` `Local*` `OffsetDateTime`     | `"PT5M"`        |
| `value("zone", ZoneId.of("UTC"))`          | `ZoneId` `Charset` `Locale`                                 | `"UTC"`         |
| `value("id", uuid)`                        | `UUID` `URI` `Path` `Pattern`                               | `"1234-…"`      |
| `value("big", new BigDecimal("1.5"))`      | `BigInteger` `BigDecimal`                                   | `"1.5"`         |
| `value("modes", defs, ofEnum(Mode.class))` | `List<T>` via `ValueCodec`                                  | `["NORMAL",…]`  |
| `value("x", def, myCodec)`                 | custom `ValueCodec` (`ofString` `ofEnum` `ofNumber` `ofType`)| `codec.store()` |
| `ValueCodec.DURATION` `UUID_CODEC` …       | ready constants for `value(path, defs, codec)`              | —               |

## Contract mode

```java
var cfg = Configs.builder("pack.json").strict().open();
cfg.require("id");
cfg.at("dump.assets");
cfg.subconfig("minecraft.loaders");
cfg.verify();
```

`strict()` — invalid file value throws instead of reset-to-default.

`./gradlew build` → `build/libs/konf-*.jar`
