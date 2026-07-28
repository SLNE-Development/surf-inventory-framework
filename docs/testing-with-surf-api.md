# Einen Branch-Build in surf-api testen

`surf-inventory-framework` wird nicht direkt deployed — surf-api zieht es als Maven-Dependency und shaded es
beim `shadowJar` nach `dev.slne.surf.api.libs.devnatan.inventoryframework`. Um einen Branch dieses Forks auf
einem Server zu testen, muss er also erst als Artefakt vorliegen, das surf-api auflösen kann.

```
surf-inventory-framework          surf-api
  7 Module, group                   Katalog: inventory-framework = "1.0.4"
  dev.slne.forks.inventoryframework   → inventory-framework-platform-paper
  Version aus root gradle.properties  → inventory-framework-platform-bukkit
        │                                        │
        │  publishToMavenLocal                   │  shadowJar relocated
        ▼                                        ▼
      ~/.m2  ──────────────────────────►  surf-api-paper-server-*-all.jar
```

## Ablauf

**1. Fork lokal publizieren**

`gradle.properties` im Fork-Root trägt die Version. Für Branch-Tests eine SNAPSHOT-Version verwenden —
Gradle cached Release-Versionen aus mavenLocal und würde beim zweiten Durchlauf das alte Jar nehmen.

```properties
version=1.0.5-packet-guis-SNAPSHOT
```

Dann vom Fork-Root aus:

```bash
./gradlew publishToMavenLocal
```

Das führt intern `applyPatches` aus und publiziert alle sieben Module nach `~/.m2`. Prüfen:

```bash
ls ~/.m2/repository/dev/slne/forks/inventoryframework/inventory-framework-platform-bukkit/
```

**2. surf-api dagegen bauen**

```bash
cd ../surf-api
./gradlew :surf-api-paper:surf-api-paper-server:shadowJar \
  -PinventoryFramework.localVersion=1.0.5-packet-guis-SNAPSHOT
```

Die Property schaltet in `buildSrc/src/main/kotlin/core-convention.gradle.kts` zwei Dinge frei:
`mavenLocal()` als Repository und eine `resolutionStrategy`, die jede Dependency der Gruppe
`dev.slne.forks.inventoryframework` auf diese Version zwingt. Der Katalogeintrag bleibt unangetastet.
Ohne die Property ist der Block inert — die Änderung ist also commit-fähig und beeinflusst CI nicht.

Zur Kontrolle loggt jedes Projekt beim Konfigurieren:

```
[surf] :surf-api-paper:surf-api-paper-server: resolving dev.slne.forks.inventoryframework
       from mavenLocal at 1.0.5-packet-guis-SNAPSHOT
```

Das fertige Jar liegt unter `surf-api-paper/surf-api-paper-server/build/libs/`.

Wer die Property nicht jedes Mal tippen will, legt sie in eine lokale, nicht eingecheckte
`gradle.properties` in surf-api.

**3. Auf dem Server aktivieren**

Das Packet-Backend ist opt-in:

```
-Dinventory-framework.gui-backend=packet
```

PacketEvents muss installiert sein und vor InventoryFramework laden. Details und die
Verifikations-Checkliste stehen in `docs/packet-gui-backend.md`.

## Iterieren

Nach jeder Codeänderung im Fork:

```bash
cd surf-inventory-framework && ./gradlew publishToMavenLocal
cd ../surf-api && ./gradlew :surf-api-paper:surf-api-paper-server:shadowJar \
  -PinventoryFramework.localVersion=1.0.5-packet-guis-SNAPSHOT
```

Weil die Version ein SNAPSHOT ist, zieht Gradle das neu publizierte Jar ohne
`--refresh-dependencies`.

## Vor dem Merge zurückdrehen

- `gradle.properties` im Fork: SNAPSHOT-Suffix entfernen, also zurück auf eine Release-Version.
- Die surf-api-Änderung kann bleiben (ohne Property inert) oder wird zurückgenommen.
- Der Katalogeintrag `inventory-framework` in surf-api wird erst auf die neue Version gehoben, wenn der
  Fork regulär nach `reposilite.slne.dev/releases` publiziert wurde.

## Warum kein Composite Build

`includeBuild("../surf-inventory-framework/inventory-framework")` mit Dependency-Substitution wäre schneller
zu iterieren, würde aber das Projekt statt des publizierten Artefakts einbinden. Das Paper-Modul shaded beim
Publizieren das Bukkit-Modul mit hinein; eine Substitution umgeht das und baut damit etwas anderes als das,
was produktiv ausgeliefert wird. Für einen Test, der genau die produktive Artefaktform prüfen soll, ist der
mavenLocal-Umweg das ehrlichere Verfahren.
