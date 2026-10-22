# Perplexity

a community-driven Minecraft client built by anyone who wants to help.

this is pretty much a fun sidequest. im tryna get as many people as possible to collaborate and vibecode the shit out of one client to see if we can actually make something decent.

**you don't need to know how to code.** help with features, bug testing, ideas, design, or whatever else you wanna contribute.

## building

The Reach module has a **Packet Clip** mode with a separate distance slider up to 128 blocks. It sends a bounded series of movement packets before an extended attack and returns afterward. Standard Reach keeps its original 3–6 block slider. Packet Clip is off by default, requires a target the client can pick, and may be rejected by a server's movement or anti-cheat checks; it is not guaranteed infinite reach. Criticals packet spoofing is skipped for clip attacks so the two movement sequences do not interfere.

KillAura also has an independent **Packet Clip** mode with its own range slider up to 128 blocks. It uses the same bounded movement sequence for distant targets, while its Standard range and existing targeting settings remain unchanged. Targets must still be loaded by the client; servers may reject or correct the movement.

requires Git and a compatible JDK.

```bash
git clone https://github.com/Epikofficial/perplexity
cd perplexity
```

on Windows, build with:

```bat
gradlew.bat build
```

on Linux/macOS:

```bash
./gradlew build
```

build artifacts should appear in `build/libs`.

## contributing

fork the repo, make your changes, test them, and then open a pull request. AI contributions are fine too.
