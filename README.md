# AerVexHorn

A Bukkit/Spigot 1.8.8 plugin that displays ServerShout messages with VexView 2.7.3.

## Build

Use JDK 8 (or a newer JDK able to compile Java 8), Maven, and a trusted **server plugin** JAR for VexView 2.7.3. The VexView dependency is not published by this repository. Install your own copy in the local Maven repository before building:

```sh
mvn install:install-file -Dfile=/path/to/VexView-2.7.3.jar -DgroupId=local.vexview -DartifactId=vexview -Dversion=2.7.3 -Dpackaging=jar -DgeneratePom=true
mvn clean package
```

The output is `target/AerVexHorn-1.0.jar`. The VexView server plugin is a provided dependency and must also be installed on the server. A VexView client mod is not a substitute for the server plugin JAR.

## Configuration and security

Set `servershout.channels` to the ServerShout channel names to display. The forwarding protocol requires a compatible ServerShout build; not all ServerShout versions provide it.

See [SECURITY.md](SECURITY.md) for the authentication setup, legacy-mode network requirements, and resource limits. The default empty key retains compatibility with the existing protocol and relies on a trusted proxy and restricted backend access. To enable authenticated messages, upgrade both ServerShout and AerVexHorn and configure the same secret on both ends.
