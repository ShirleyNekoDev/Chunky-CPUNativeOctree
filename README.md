[Experimental] NativeOctree plugin for Chunky 2.5+
==================================

Provides a new octree implementation which uses native CPU operations and OS memory management to speed up chunk loading / octree creation and rendering of Chunky scenes.

__⚠️ Disclaimer:__ This project is experimental. Do not expect interoperability, feature completeness or faster rendering with the current implementation. Especially Java-native interoperability is a bottleneck and will change in the future.

## Project Goals

Different octree implementations optimized for different user needs with optimizations for different stages in the scene creation process:
- Octree optimized for insertion / building (faster octree creation)
- Octree optimized for memory efficiency (loading huge worlds)
- Octree optimized for access speed (fast rendering)

_(Personal goals: learn Zig, efficient memory management, optimizations for modern platforms)_

## Requirements

- Chunky 2.5.0-snapshot builds
- Java 21 with [_JEP 442: Foreign Function & Memory API (Third Preview)_](https://openjdk.org/jeps/442)
  - ⚠️ _this requires preview features to be enabled - see installation below_

### Builds / Releases

Releases are compiled for **x86_64** (64-bit) systems targeting Windows, Linux and MacOS and packed into a single cross-platform `.jar`.

__⚠️ Disclaimer:__ Only Windows is tested at the moment.

### Build Requirements

- JDK 21+
- Zig 0.11.0+

## Installation

1. Download the newest version from the releases
2. Put the downloaded `.jar`-file in `.chunky/plugins/`
3. Start the Chunky launcher and ensure that you have the newest release installed (2.5-SNAPSHOT or higher)
4. Enable the plugin in the plugin manager
5. Make sure that you are using an installation of Java __19__ (or a subversion 19.x, but not a lower or higher version)
6. Append `--enable-preview --enable-native-access=ALL-UNNAMED` to the Java options
7. Start Chunky and switch the octree implementation to `NATIVE_ZIGv1` in the `Advanced` tab
8. (If you have the debug console enabled, you should now see some messages about created octrees)
