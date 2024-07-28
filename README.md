<div align="center">

# 📂 nav 📂

An interactive and stylish replacement for ls & cd.

![nav demo](media/screenshot2.png)

![nav demo filter](media/screenshot3.png)

</div>

---

Ever tried to find that one config file hidden deep in your directory tree?
Or maybe you just want to quickly jump to a directory and inspect some files on the way?  
✨ **nav** is here to help! ✨  
Written Kotlin/Native, nav provides a modern and intuitive terminal UI to navigate your filesystem.

- ➡️ Use arrow keys to navigate everywhere
- ⌨️ Type to filter entries, press `Tab` to autocomplete
- ✏️ Instantly edit files with your favorite editor
- ✅ Press `Enter` to move your shell to the current directory

## 🚀 Installation

### 1. Install **nav**

Select your operating system

<details>
<summary>Linux</summary>

```sh
TODO
```

</details>

<details>
<summary>Windows</summary>

On Windows, you can use [scoop](https://scoop.sh) to install nav:

```powershell
scoop bucket add JojoIV "https://github.com/Jojo4GH/scoop-JojoIV"
scoop install nav
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
<summary>Powershell</summary>

Add the following to the end of your PowerShell configuration (find it by running `$PROFILE`):

```powershell
Invoke-Expression (& nav --init powershell | Out-String)
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
maxVisibleEntries = 32
maxVisiblePathElements = 6
hideHints = false
clearOnExit = true
editor = "nano"

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

[modificationTime]
minimumBrightness = 0.4
halfBrightnessAtHours = 12.0
```

For valid key names see [web keyboard event values](https://developer.mozilla.org/en-US/docs/Web/API/UI_Events/Keyboard_event_key_values).

## ❤️ Powered by

- UI: [Mordant](https://github.com/ajalt/mordant)
- CLI: [Clikt](https://github.com/ajalt/clikt)
- Commands: [Kommand](https://github.com/kgit2/kommand)
- Config file: [ktoml](https://github.com/orchestr7/ktoml)
- Kotlin/Native
