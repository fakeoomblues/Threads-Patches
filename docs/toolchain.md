# Toolchain setup (Fedora WSL, fish)

Provision once per host, not on every build. Run `fish` blocks in fish and `bash`
blocks in bash. For routine work, use [development verification](development.md#verify).

## 1. Host tools

Fedora WSL includes Python and the shell tools. Install the additional commands
used by repo development/release workflows:

```fish
sudo dnf install -y uv git gh jq fish ripgrep unzip binutils
```

Install Homebrew by following [brew.sh](https://brew.sh), then install Java and
APK analysis/Android SDK tools:

```fish
brew install openjdk@21 jadx apktool android-cli
brew unlink openjdk
brew link openjdk@21
```

## 2. Java, Android CLI, and analysis tools

Use **Java 21** and the checked-in `./gradlew`; no separate Gradle install.
Set this in `~/.config/fish/config.fish`:

```fish
set -gx ANDROID_HOME "$HOME/Android/Sdk"
fish_add_path ~/.local/bin
```

### SDK packages: build requirements versus analysis utilities

`android-cli` installs the SDK manager. Gradle downloads the Android 36 platform
required by Morphe's `compileSdk` and the AGP-compatible Build-Tools as needed, so
neither needs a manual install command. The SDK manager can install platform-tools
and NDK for local device/native analysis. It treats NDK releases as side-by-side
packages whose IDs include the release number, so
`android sdk install ndk` is not a valid package name. List stable candidates
with `android sdk list --all 'ndk/*'`, then install the desired exact ID. Build
Tools IDs also include a version; don't install one manually here because AGP
selects and downloads its compatible Build-Tools version.

```fish
android info
android sdk list
android sdk install platform-tools
# Optional: select a stable package ID from `android sdk list --all 'ndk/*'`:
android sdk install "ndk;29.0.14206865"
android sdk list
fish_add_path ~/Android/Sdk/platform-tools
# After a Gradle build installs Build-Tools:
fish_add_path (find ~/Android/Sdk/build-tools -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n1)
# Optional: add the NDK compiler tools only when doing native-code analysis:
fish_add_path ~/Android/Sdk/ndk/29.0.14206865/toolchains/llvm/prebuilt/linux-x86_64/bin
```

Morphe sets `compileSdk = 36`; AGP chooses and installs its compatible build-tools
(the repo does not pin `buildToolsVersion`). [Gradle can download missing build
packages](https://developer.android.com/studio/intro/update#download-with-gradle)
with an existing SDK, accepted licenses, and network access. Add the resulting
AGP-selected `build-tools` directory to PATH when using tools such as `aapt`
and `apksigner`.

`platform-tools` supplies `adb`; build-tools supplies `aapt`, `aapt2`, `apksigner`,
and `zipalign`. An already-installed suitable build-tools version is fine; adjust
PATH accordingly. `ANDROID_HOME` controls Gradle discovery, PATH controls terminal
tools. No emulator or system image is required.

Use the `android` CLI for supported deployment and UI workflows (install/run,
layout inspection, and screen capture); see [validation](validation.md). Keep
`adb` for lower-level device operations such as Wireless debugging pairing,
connection/listing, and commands not exposed by `android`. `fd-find` is not part
of the maintained toolchain; use `rg` for repository searches.

### Python applications: persistent tools versus one-shot runs

| Tool | Command | Use |
| --- | --- | --- |
| Frida | `uv tool install frida-tools` (optional) | Runtime instrumentation; install only when needed |
| Kaggle | `uv tool install kaggle` | Required by `scripts/remote_decompile.py` |
| APKiD | `uvx apkid app.apk` | On-demand recon |
| objection | `uvx objection --help` | On-demand dynamic triage; never assume a persistent install |

`uv tool` puts isolated executables in `~/.local/bin`; `uvx` uses cached temporary
environments. Install Frida tooling only for runtime instrumentation, with a
matching-version/ABI `frida-server` **on the device**:
[releases](https://github.com/frida/frida/releases), [Android setup](https://frida.re/docs/android/).
Kaggle requires credentials and a private notebook; see [remote decompilation](reverse-engineering.md#remote-decompilation-for-large-apks).
Host analysis tools are `jadx` (Java), `apktool` (resources/smali), `rg` (search),
and `strings` (DEX strings).

## 3. Device access

Wireless ADB avoids USB passthrough:

```fish
adb pair DEVICE_IP:PAIRING_PORT
adb connect DEVICE_IP:DEBUG_PORT
adb devices
```

Use the distinct ports from Android's Wireless debugging screen. The WSL host
network/firewall must allow access to the phone.

## 4. Repository dependencies

Use a GitHub PAT with `read:packages` for the Morphe Gradle registry:
`GITHUB_ACTOR` / `GITHUB_TOKEN`, or `gpr.user` / `gpr.key` in private
`~/.gradle/gradle.properties`. Never commit credentials. No JS toolchain is required;
release tooling uses `gh`, `python3`, and `jq`.

## 5. Morphe CLI and GUI share one JAR

Morphe Desktop is distributed upstream as a JAR; this repo does not configure a
package-manager package for it. Fetch the latest stable release without hard-coding
a version, and verify it against the SHA-256 digest published in GitHub release
metadata before using it. This release digest is an integrity check against GitHub's
published asset metadata, not an independent trust anchor:

```bash
set -euo pipefail
repo=MorpheApp/morphe-desktop
release=$(gh release view --repo "$repo" --json tagName,assets)
tag=$(jq -r '.tagName' <<< "$release")
version=${tag#v}
asset="morphe-desktop-${version}-all.jar"
digest=$(jq -r --arg asset "$asset" \
  '.assets[] | select(.name == $asset) | (.digest // "") | sub("^sha256:"; "")' \
  <<< "$release")
[[ "$digest" =~ ^[[:xdigit:]]{64}$ ]] || { echo "Missing published SHA-256 for $asset" >&2; exit 1; }
mkdir -p "$HOME/.local/share/morphe"
gh release download "$tag" --repo "$repo" --pattern "$asset" --dir "$HOME/.local/share/morphe"
printf '%s  %s\n' "$digest" "$HOME/.local/share/morphe/$asset" | sha256sum -c -
```

Morphe Desktop 1.17.0 was checked locally against the pinned Zalo APKM. It still
fails Morphe's internal DEX hierarchy verification on missing Google IMA classes,
in both `FULL` and `STRIP_FAST` bytecode modes; upgrading alone does not unblock
that APK. The latest-release download above may select a newer version, which
must be revalidated against the target APK. Do not treat SDK verification as
passed or install an output that failed patching.

`morphe-desktop-*-all.jar` starts the GUI without a subcommand, the CLI with one.
Do not replace a JAR during an active patch run. `scripts/repatch.py` discovers
the highest numeric version in this directory; `--jar <path>` pins a specific one.
No environment
configuration is needed for its default JAR, bundle, or keystore discovery. See
[CLI patching](cli.md) for commands, runtime data-directory resolution, and
[signing](cli.md#signing) for key/password selection.
Upstream: [README](https://github.com/MorpheApp/morphe-desktop),
[CLI reference](https://github.com/MorpheApp/morphe-desktop/blob/main/docs/documentation.md#cli).

## 6. Storage and path conventions

On the standard Fedora WSL host, APKMirror downloads live in
`/mnt/c/Users/zeldrisho/Downloads/`; other hosts may use any local directory.
Keep APK investigation artifacts in the gitignored [analysis workspace](reverse-engineering.md#analysis-workspace).
The helper prefers the repository's persistent `Morphe.keystore`, with shared
Morphe data-directory keys as fallbacks; see [signing](cli.md#signing).

## 7. Original APK source

Download originals **only from [APKMirror](https://www.apkmirror.com/)**. Pass the
split `.apkm` directly to Morphe or `scripts/repatch.py`, never an extracted
`base.apk`. Record the source page URL, version name, versionCode, ABI/variant,
and input SHA-256.

## Build-tool behavior

Extensions package compiled DEX through Morphe's `extension` plugin without a
standalone R8 configuration. Shrinker behavior is not established by this repository's
configuration; the bundle contract checks resulting DEX descriptors and flags.

## Verify setup

```fish
python3 --version
java -version
./gradlew --version
android sdk list
adb version
aapt version
jadx --version
apktool --version
```

Then run [canonical verification](development.md#verify), followed by
[device validation](validation.md).
