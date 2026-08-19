# FNF Porter For Mobile

Android port of [FNF Porter](https://github.com/FNF-Porter/Porter.py) (Psych Engine ↔ V-Slice / Base Game).

**Package:** `com.sametgkte.fnfporter`  
**Min Android:** 7.0 (API 24)

## Credits

### Original FNF Porter
- **Gusborg**
- **tposejank**
- **BombasticTom**
- **VocalFan**

Source: [FNF-Porter/Porter.py](https://github.com/FNF-Porter/Porter.py)  
License: [CC BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/)

### Reverse porter & extra tools
- **SametGkTe** (GkTe Tool / Uktimate FNF Tool)

### Android app
- **SametGkTe**

This project is a non-commercial derivative. You must credit the original authors. You may not sell it.

## Features

- `_polymod_meta.json` `api_version` is **0.8.5** (current Funkin)
- Psych Engine → V-Slice (charts, characters, weeks, stages, audio, images, Lua → HScript)
- V-Slice → Psych Engine (charts, characters, weeks, stages, audio, images, HScript → Lua)
- System Files picker (Use this folder)
- Folder type check (Psych vs V-Slice)

Script conversion is best-effort. Review generated `.lua` / `.hxc` files.

## Build

Needs JDK 11+ (17 recommended) and Android SDK (`platforms;android-34`, `build-tools;34.0.0`).

```bash
export ANDROID_HOME=/path/to/Android/Sdk
export JAVA_HOME=/path/to/jdk
chmod +x build.sh
./build.sh
```

Or push to GitHub — **Actions → Build APK** produces `FNF-Porter-For-Mobile.apk`.

Tag a release to attach the APK automatically:

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Issues

Use [Issues](https://github.com/SametGkTe/FNF-Porter-Mobile/issues) with a template:

- **Script Error** — Lua / HScript conversion
- **Conversion Error** — crash while converting
- **Incomplete conversion** — missing or wrong files
- **Feature request**

The app **Create an issue** button copies the log and opens the template chooser.

## Install

1. Download the APK from [Releases](../../releases).
2. Allow install from unknown sources.
3. Grant **All files access**.
4. Pick folders with **Files** → **Use this folder**.
