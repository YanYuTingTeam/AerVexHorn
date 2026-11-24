# AerVexHorn

A Bukkit/Spigot 1.8.8 plugin that displays ServerShout messages with VexView 2.7.3.

## Build

Use JDK 8 (or a newer JDK able to compile Java 8), Maven, and a trusted **server plugin** JAR for VexView 2.7.3. The VexView dependency is not published by this repository. Install your own copy in the local Maven repository before building:

```sh
mvn install:install-file -Dfile=/path/to/VexView-2.7.3.jar -DgroupId=local.vexview -DartifactId=vexview -Dversion=2.7.3 -Dpackaging=jar -DgeneratePom=true
mvn clean package
```

The output is `target/AerVexHorn-1.0.jar`. The VexView server plugin is a provided dependency and must also be installed on the server. A VexView client mod is not a substitute for the server plugin JAR.

## Configuration and display

Set `servershout.channels` to the ServerShout channel names to display. The forwarding protocol requires a compatible ServerShout build; not all ServerShout versions provide it.

The existing `servershout:cast` v2 directed and v3 broadcast protocols remain supported. A v3 broadcast displays once for every online player on the receiving backend. This update works with the existing v2/v3 sender and does not require ServerShout PR #2.

Messages continue scrolling out through the left edge without VexView interpreting negative X coordinates as another anchor. Multiline backgrounds follow the text height, and the bottom position keeps the visible lines inside the window. The leading `<` in VexView delimiter sequences (`<S>`, `<#>`, `<##>`, `<#&>`, `<&>`) is replaced with `＜` for display, preserving other text and formatting.

Run `mvn clean verify` for the display regression checks. Full proxy/backend/client rendering still needs an in-game check. Use the existing proxy interception of client `servershout:cast` messages and restrict direct access to the backend to trusted proxies.
