# BedrockTunnel 🚇

BedrockTunnel is a desktop MITM packet capture tool for Minecraft: Bedrock Edition. It sits between one Bedrock client and one target server, captures traffic in both directions, and provides a desktop UI for inspection and control.

## ✨ Features

- 🖥️ A desktop GUI built for live packet inspection
- 🌐 Live traffic capture between a Bedrock client and a target server
- 📦 Packet details in `Summary`, `JSON`, and `Hex` views
- 🔎 Packet filtering by direction, state, type, and keyword
- 🚫 Packet blocking with blacklist and whitelist modes
- ⏸️ Packet breakpoints with forward, drop, and resume controls
- 🔁 Basic packet replay during a live session
- 📊 Traffic statistics and per-packet counts
- 🗂️ Saved capture history with offline review

## 📋 Requirements

- Java 21
- Gradle 9.4.1
- A Bedrock client and target server using the same protocol version selected in the UI

## 🔢 Supported Versions

BedrockTunnel currently supports:

- `1.21.90` / `v818`
- `1.21.93` / `v819`
- `1.21.93 (NetEase)` / `v819`
- `1.21.100` / `v827`
- `1.21.111` / `v844`
- `1.21.120` / `v859`
- `1.21.124` / `v860`
- `1.21.130` / `v898`
- `1.26.0` / `v924`
- `1.26.10` / `v944`

## 🛠️ Build

Build the project:

```powershell
./gradlew build
```

Create the fat jar explicitly:

```powershell
./gradlew shadowJar
```

The runnable jar is written to:

- `build/libs/BedrockTunnel.jar`

## ▶️ Run

Run from Gradle:

```powershell
./gradlew run
```

Run the fat jar directly:

```powershell
java -jar build/libs/BedrockTunnel.jar
```

## 🚀 Quick Start

1. Start BedrockTunnel.
2. Enter the local listen host and port.
3. Enter the target Bedrock server host and port.
4. Select the Bedrock protocol version.
5. Click `Start`.
6. Point the Bedrock client to the local listen address instead of the real server.

## 🖥️ Interface Overview

- Top bar: tunnel settings and live controls
- Center: packet list on the left and packet details on the right
- Bottom tabs: rules, statistics, and capture history

## 🔍 Packet Views

- `Summary`: packet metadata and current state
- `JSON`: serialized packet content
- `Hex`: raw packet bytes

## 🧰 Filtering

You can filter the packet list by:

- Direction
- State
- Packet type
- Keyword

Keyword matching checks packet names, summary text, JSON text, and hex text.

## 🚦 Rules And Breakpoints

- Blocking rules can work in `BLACKLIST` or `WHITELIST` mode
- Rules currently match by direction and packet type
- Breakpoints pause live traffic when a matching packet appears
- While paused, you can forward or drop the breakpoint packet, then resume queued traffic

## 🔁 Replay

- Replay is available in live mode
- It resends the selected captured packet with its original direction
- Packet editing is not included yet
- Offline history replay is not included yet

## 📊 Statistics

The `Stats` tab shows:

- Total packet count
- Total byte count
- Total replay count
- Breakpoint hit count
- Per-packet-type counters

## 🗂️ Capture History

Captured sessions are saved locally and can be reopened from the `History` tab for browsing, filtering, and reviewing packet details later.
