<div align="center">

# 📂 nav 📂

The interactive and stylish replacement for ls & cd!

![nav demo](media/screenshot2.png)

![nav demo filter](media/screenshot3.png)

</div>

---

[![GitHub][latest-release-badge]][latest-release]
[![GitHub][license-badge]](LICENSE.md)
[![Build][build-badge]][github-actions]
[![Checks][checks-badge]][github-actions]
[![Package][package-badge]][github-actions]

[![Kotlin Multiplatform][kotlin-multiplatform-badge]][kotlin-multiplatform]
[![Linux X64 Platform][linux-x64-platform-badge]][kotlin-native]
[![Linux ARM64 Platform][linux-arm64-platform-badge]][kotlin-native]
[![MinGW X64 Platform][mingw-x64-platform-badge]][kotlin-native]
[![JVM Platform][jvm-platform-badge]][kotlin-jvm]

[latest-release-badge]: https://img.shields.io/github/v/release/Jojo4GH/nav?label=Latest
[latest-release]: https://github.com/Jojo4GH/nav/releases/latest
[license-badge]: https://img.shields.io/github/license/Jojo4GH/nav?label=License

[build-badge]: https://img.shields.io/github/actions/workflow/status/Jojo4GH/nav/workflow.yml?branch=master&label=Build
[checks-badge]: https://img.shields.io/github/check-runs/Jojo4GH/nav/master?label=Checks
[package-badge]: https://img.shields.io/github/actions/workflow/status/Jojo4GH/nav/package.yml?branch=master&label=Package
[github-actions]: https://github.com/Jojo4GH/nav/actions

[kotlin-multiplatform-badge]: https://img.shields.io/badge/Kotlin_Multiplatform-grey?logo=kotlin
[linux-x64-platform-badge]: https://img.shields.io/badge/Native-Linux_X64-e082f3
[linux-arm64-platform-badge]: https://img.shields.io/badge/Native-Linux_ARM64-e082f3
[mingw-x64-platform-badge]: https://img.shields.io/badge/Native-MinGW_X64-e082f3
[jvm-platform-badge]: https://img.shields.io/badge/Platform-JVM-4dbb5f

[kotlin-multiplatform]: https://kotlinlang.org/docs/multiplatform.html
[kotlin-native]: https://kotlinlang.org/docs/native-overview.html
[kotlin-jvm]: https://kotlinlang.org/docs/jvm-get-started.html

Ever tried to find that one config file hidden deep in your directory tree?
Or maybe you just want to quickly jump to a directory and inspect some files on the way?  
✨ **nav** is here to help! ✨  
Written in Kotlin/Native, nav provides a modern and intuitive terminal UI to navigate your filesystem.

- ➡️ Use arrow keys to navigate everywhere
- ⌨️ Type to filter entries, press `Tab` to autocomplete
- ✏️ Instantly edit files with your favorite editor on the fly
- 📈 Create files and directories or run commands everywhere
- ✅ Press `Enter` to move your shell to the current directory
- ⭐ Define custom macros for even more powerful workflows

## 🚀 Installation

### 1. Install **nav**

Select your operating system

<details>
<summary>Linux</summary>

Install with any of the following package managers:

| Distribution         | Repository                                                  | Instructions                               |
|----------------------|-------------------------------------------------------------|--------------------------------------------|
| Arch Linux           | [AUR]                                                       | `pacman -S nav-cli` <br/> `yay -S nav-cli` |
| NixOS                | [Nixpkgs]                                                   | `nix-shell -p nav`                         |
| Debian, Ubuntu, etc. | [nav_amd64.deb][Deb_amd64] <br/> [nav_arm64.deb][Deb_arm64] | `dpkg -i ...`                              |

[AUR]: https://aur.archlinux.org/packages/nav-cli
[Nixpkgs]: https://search.nixos.org/packages?show=nav
[Deb_amd64]: https://github.com/Jojo4GH/nav/releases/latest/download/nav_amd64.deb
[Deb_arm64]: https://github.com/Jojo4GH/nav/releases/latest/download/nav_arm64.deb

Or install (or update) nav with the [installer script](install/install.sh):
```sh
curl -sS https://raw.githubusercontent.com/Jojo4GH/nav/master/install/install.sh | sh
```

Or manually download the [latest release](https://github.com/Jojo4GH/nav/releases/latest).

</details>

<details>
<summary>Windows</summary>

On Windows, you can use [scoop](https://scoop.sh) to install nav:

```powershell
scoop bucket add JojoIV "https://github.com/Jojo4GH/scoop-JojoIV"
scoop install nav
```

Or without adding the bucket:

```powershell
scoop install "https://raw.githubusercontent.com/Jojo4GH/scoop-JojoIV/master/bucket/nav.json"
```

</details>

### 2. Set up your shell

Configure your shell to initialize nav. This is required for the *cd* part of nav's functionality.

<details>
<summary>Bash</summary>

Add the following to the end of `~/.bashrc`:

```sh
eval "$(nav --init bash)"
```

</details>

<details>
<summary>Zsh</summary>

Add the following to the end of `~/.zshrc`:

```sh
eval "$(nav --init zsh)"
```

</details>

<details>
<summary>Powershell</summary>

Add one of the following to the end of your PowerShell configuration (find it by running `$PROFILE`):

```powershell
Invoke-Expression (& nav --init powershell | Out-String)
```

```powershell
Invoke-Expression (& nav --init pwsh | Out-String)
```

</details>

<details>
<summary>NixOS</summary>

Bash:

```nix
programs.bash.shellInit = "eval \"$(nav --init bash)\"";
```

Zsh:

```nix
programs.zsh.shellInit = "eval \"$(nav --init zsh)\"";
```

Or with `home-manager`:

```nix
home-manager.users.user.programs = {
    bash = {
        enable = true;
        bashrcExtra = "eval \"$(nav --init bash)\"";
    };
    zsh = {
        inherit (config.programs.zsh) enable;
        initExtra = "eval \"$(nav --init zsh)\"";
    };
};
```

</details>

## 🔧 Configuration

To create or edit the config file you can use the `--edit-config` command line option.
The default location for the file is `~/.config/nav.toml`.
You can change this by setting the `NAV_CONFIG` environment variable:

<details>
<summary>Linux</summary>

```sh
export NAV_CONFIG=~/some/other/path/nav.toml
```

</details>

<details>
<summary>Powershell</summary>

```powershell
$ENV:NAV_CONFIG = "$HOME\some\other\path\nav.toml"
```

</details>

You can also use the `--config` command line option to explicitly specify a config file.

The default configuration looks as follows:

```toml
# If not specified, uses the first that exists of the following:
# $EDITOR, $VISUAL, nano, nvim, vim, vi, code, notepad
# You can also use the --editor command line option to override this
editor = ""

hideHints = false
clearOnExit = true

limitToTerminalHeight = true
maxVisibleEntries = 20 # Set to 0 for unlimited entries
maxVisiblePathElements = 6
# Used to distinguish escape sequences on Linux terminals
inputTimeoutMillis = 4 # Set to 0 for no timeout
suppressInitCheck = false

# Which columns to show for each entry and how to order them
shownColumns = [
    "Permissions",      # Permissions of the entry in unix style
    # "HardLinkCount",  # Number of hard links to the entry (not shown by default)
    # "UserName",       # Name of the user owning the entry (not shown by default)
    # "GroupName",      # Name of the group owning the entry (not shown by default)
    "EntrySize",        # Size of the file
    "LastModified",     # Time of last modification
]

[keys] # Configure how nav is controlled

submit = "Enter"
cancel = "Escape"

cursor.up = "ArrowUp"
cursor.down = "ArrowDown"
cursor.home = "Home"
cursor.end = "End"

nav.up = "ArrowLeft"
nav.into = "ArrowRight"
nav.open = "ArrowRight"

menu.up = "PageUp"
menu.down = "PageDown"

filter.autocomplete = "Tab"
filter.clear = "Escape"

[autocomplete] # Configure the auto complete behavior

# Controls the behavior of the auto complete feature
# - "CommonPrefixCycle": Auto completes the largest common prefix and cycles through all entries
# - "CommonPrefixStop": Auto completes the largest common prefix and stops
style = "CommonPrefixCycle"
# Controls auto navigation on completion
# - "None": Do not auto navigate
# - "OnSingleAfterCompletion": Auto completes the entry and on second action navigates
# - "OnSingle": Auto completes the entry and navigates immediately (not recommended)
autoNavigation = "OnSingleAfterCompletion"

[colors] # Configure how nav looks

# Possible values for themes are: Retro, Monochrome, SimpleColor, Original
theme = "Retro"
simpleTheme = "Monochrome" # Used for terminals with less color capabilities (see: accessibility.simpleColors)

# The following colors can also be explizitly set (default: theme/simpleTheme colors):
path = "FFFFFF"
filter = "FFFFFF"
filterMarker = "FFFFFF"
keyHints = "FFFFFF"

permissionRead = "FFFFFF"
permissionWrite = "FFFFFF"
permissionExecute = "FFFFFF"
entrySize = "FFFFFF"
modificationTime = "FFFFFF"

directory = "FFFFFF"
file = "FFFFFF"
link = "FFFFFF"

[modificationTime] # Configure how the modification time is rendered

minimumBrightness = 0.4
halfBrightnessAtHours = 12.0

[accessibility] # Configure accessibility options

simpleColors = false # Whether to use the simple color theme (default: auto)
decorations = false # Whether to show decorations (default: auto)
```

For valid key names see [web keyboard event values](https://developer.mozilla.org/en-US/docs/Web/API/UI_Events/Keyboard_event_key_values).

### Entry Macros

You can define custom macros that work with entries (e.g. directories, files) in the configuration file as follows:

```toml
[[entryMacros]]
# The description displayed (required) (see placeholders)
description = "..."
# The conditions for the macro to be available (defaults to false)
onFile = false
onDirectory = false
onSymbolicLink = false
# The command to run (required) (see placeholders)
command = "..."
# What to do after the command was executed. Possible values are:
# - "DoNothing": Do nothing
# - "ExitAtCurrentDirectory": Exit at the current directory
# - "ExitAtInitialDirectory": Exit at the initial directory
afterCommand = "..."            # Defaults to "DoNothing"
afterSuccessfulCommand = "..."  # Defaults to value of afterCommand
afterFailedCommand = "..."      # Defaults to value of afterCommand
# The key to trigger the macro or null for no quick macro (defaults to null)
quickMacroKey = "..."
```

There are several placeholders available for `description` and `command`:
- `{initialDir}`: The initial directory where nav was started
- `{dir}`: The current directory inside nav
- `{entryPath}`: The path of the currently highlighted entry
- `{entryName}`: The name of the currently highlighted entry
- `{filter}`: The current filter string or empty if no filter is set

Macros are available in the menu (default `PageDown`).
They can also quickly be triggered by tapping `ctrl` together with or followed by the `quickMacroKey`.

Example:

```toml
# An alternative editor macro
[[entryMacros]]
description = "open {entryName} in code"
command = "code '{entryPath}'"
afterSuccessfulCommand = "ExitAtCurrentDirectory"
onFile = true
onDirectory = true
quickMacroKey = "ArrowRight"

# Same as above, but waits for the editor to close before returning again to nav
[[entryMacros]]
description = "open {entryName} in code and wait"
command = "code --wait '{entryPath}'"
onFile = true
onDirectory = true
quickMacroKey = "shift+ArrowRight"

# A macro for deleting directories recursively
[[entryMacros]]
description = "delete {entryName} recursively"
command = "rm -rf '{entryPath}'"
onDirectory = true
quickMacroKey = "Delete"

# A macro for printing the full path of the entry
[[entryMacros]]
description = "print full path"
command = "echo '{entryPath}'"
onFile = true
onDirectory = true
onSymbolicLink = true
```

## Known Issues

- On windows some symbolic link destinations can not be resolved correctly.
- On windows special characters in paths may lead invalid file information being returned or errors ([#22](https://github.com/Jojo4GH/nav/issues/22), [#24](https://github.com/Jojo4GH/nav/pull/24)).  
  This can be fixed by enabling `Settings` -> `Language & region` -> `Administrative language settings` -> `Change system locale...` -> `Use Unicode UTF-8 for worldwide language support`

## ❤️ Powered by

- UI: [Mordant](https://github.com/ajalt/mordant)
- CLI: [Clikt](https://github.com/ajalt/clikt)
- Commands: [Kommand](https://github.com/kgit2/kommand)
- Config file: [ktoml](https://github.com/orchestr7/ktoml)
- Kotlin/Native
