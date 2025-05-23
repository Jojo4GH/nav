<div align="center">

# 📂 nav 📂

The interactive and stylish replacement for ls & cd!

![nav demo](media/screenshot2.png)

![nav demo filter](media/screenshot3.png)

</div>

---

Ever tried to find that one config file hidden deep in your directory tree?
Or maybe you just want to quickly jump to a directory and inspect some files on the way?  
✨ **nav** is here to help! ✨  
Written in Kotlin/Native, nav provides a modern and intuitive terminal UI to navigate your filesystem.

- ➡️ Use arrow keys to navigate everywhere
- ⌨️ Type to filter entries, press `Tab` to autocomplete
- ✏️ Instantly edit files with your favorite editor on the fly
- 📈 Create files and directories or run commands everywhere
- ✅ Press `Enter` to move your shell to the current directory

## 🚀 Installation

### 1. Install **nav**

Select your operating system

<details>
<summary>Linux</summary>

Install (or update) nav with the [installer script](install/install.sh):
```sh
curl -sS https://raw.githubusercontent.com/Jojo4GH/nav/master/install/install.sh | sh
```
Or install with any of the following package managers:

| Distribution | Repository | Instructions                               |
|--------------|------------|--------------------------------------------|
| Arch Linux   | [AUR]      | `pacman -S nav-cli` <br/> `yay -S nav-cli` |
| NixOS        | [Nixpkgs]  | `nix-shell -p nav`                         |

[AUR]: https://aur.archlinux.org/packages/nav-cli
[Nixpkgs]: https://search.nixos.org/packages?show=nav

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

The default location for the configuration file is `~/.config/nav.toml`.
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

The default configuration looks as follows:

```toml
editor = "nano"
hideHints = false
clearOnExit = true

limitToTerminalHeight = true
maxVisibleEntries = 20 # Set to 0 for unlimited entries
maxVisiblePathElements = 6
# Used to distinguish escape sequences on Linux terminals
inputTimeoutMillis = 4 # Set to 0 for no timeout
suppressInitCheck = false

[keys]

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

[colors]

path = "00DBB7"
filter = "00DBB7"
filterMarker = "00DBB7"
keyHints = "FFFFFF"

permissionRead = "F71674"
permissionWrite = "F5741D"
permissionExecute = "009FFD"
entrySize = "F5741D"
modificationTime = "009FFD"

directory = "F71674"
file = "F5741D"
link = "009FFD"

[autocomplete]

# Controls the behavior of the auto complete feature
# - "CommonPrefixCycle": Auto completes the largest common prefix and cycles through all entries
# - "CommonPrefixStop": Auto completes the largest common prefix and stops
style = "CommonPrefixCycle"
# Controls auto navigation on completion
# - "None": Do not auto navigate
# - "OnSingleAfterCompletion": Auto completes the entry and on second action navigates
# - "OnSingle": Auto completes the entry and navigates immediately (not recommended)
autoNavigation = "OnSingleAfterCompletion"

[modificationTime]

minimumBrightness = 0.4
halfBrightnessAtHours = 12.0
```

For valid key names see [web keyboard event values](https://developer.mozilla.org/en-US/docs/Web/API/UI_Events/Keyboard_event_key_values).

## Known Issues

- Symbolic link destinations are not shown and handled weirdly.

## ❤️ Powered by

- UI: [Mordant](https://github.com/ajalt/mordant)
- CLI: [Clikt](https://github.com/ajalt/clikt)
- Commands: [Kommand](https://github.com/kgit2/kommand)
- Config file: [ktoml](https://github.com/orchestr7/ktoml)
- Kotlin/Native
