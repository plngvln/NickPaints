---

# NickPaints
### Dynamic, Cloud-Synced Gradient Nicknames for Minecraft

NickPaints is an advanced, client-side Fabric mod that brings your Minecraft identity to life with fully customizable, animated gradient nicknames.

---

<img src="https://img.shields.io/badge/Features-blue?style=for-the-badge" alt="Features">

- **Animated Gradient Nicknames:** Create beautiful, flowing gradients with multiple colors that animate smoothly over your nametag.
- **Cloud Syncing:** Design your unique paint and sync it to the cloud. Other players with the mod will see your custom nametag exactly as you designed it.
- **Extensive Customization:** Use a simple yet powerful syntax to control every aspect of your gradient, including animation speed, gradient length, style (smooth or blocky), animation direction, and more.
- **Live In-Game GUI:** Press a key (default `G`) to open an interface with a real-time preview of your design. Features tab-completion for easy argument input.
- **Client-Side Control:** Locally disable paints for all players or specific individuals with simple in-game commands. Your preferences are saved and persist across sessions.

---

<img src="https://img.shields.io/badge/Installation-brightgreen?style=for-the-badge" alt="Installation">

This is a client-side Fabric mod.

1.  **Install the [Fabric Loader](https://fabricmc.net/use/).**
2.  **Install the [Fabric API](https://modrinth.com/mod/fabric-api).** It is a required dependency.
3.  Download the latest version of NickPaints from the [Modrinth Page](https://modrinth.com/project/nickpaints/versions)
4.  Place the downloaded `.jar` file into your `mods` folder.
5.  Launch the game.

---

<img src="https://img.shields.io/badge/Configuration-9cf?style=for-the-badge" alt="Configuration">

### In-Game GUI

The easiest way to configure your paint is through the in-game GUI.

- **Press `G`** (configurable in controls) to open the settings screen.
- **Enter your gradient string** in the text field. You will see a live preview of your design at the top.
- Use **`Tab`** to autocomplete arguments like `speed()`, `segment()`, etc.
- Click **"Save & Sync to Cloud"** to save your design and make it visible to other players. This requires a valid Mojang session.

### Gradient Syntax

The core of the mod is its powerful gradient string syntax. It consists of a list of colors followed by optional arguments.

#### Colors
Colors are defined using standard HEX codes, separated by commas.
`#ff0000, #0000ff` - A simple gradient from red to blue.

#### Arguments
Arguments are added after the colors, in any order.

| Argument | Description | Example |
| :--- | :--- | :--- |
| `speed(ms)` | Controls the animation speed in milliseconds. A full cycle takes this amount of time. | `speed(2000)` |
| `segment(chars)` | Stretches the full gradient over a specified number of characters. Allows long gradients to "scroll" across short names. | `segment(15)` |
| `style(block)` | Changes the gradient from a smooth blend to sharp, distinct color blocks. | `style(block)` |
| `direction(rtl)`| Reverses the gradient and animation direction from Right-to-Left instead of the default Left-to-Right. | `direction(rtl)` |
| `static(true)` | Completely disables all animation, creating a static gradient. | `static(true)` |
| `rainbow(ms)` | A special preset that creates an animated rainbow effect. Overrides any HEX colors. | `rainbow(3000)` |

**Full Example:**
`#e0c3fc, #8ec5fc, #8ec5fc, #e0c3fc speed(1000) segment(8) direction(rtl)`

---

<img src="https://img.shields.io/badge/Commands-informational?style=for-the-badge" alt="Commands">

NickPaints provides a set of client-side commands for managing local settings.

| Command | Description |
| :--- | :--- |
| `/nickpaints clear-cache` | Clears the local cache of other players' paints, forcing the mod to re-fetch them. |
| `/nickpaints toggle global [true/false]` | Enables or disables the rendering of ALL custom paints. Toggles if no state is provided. |
| `/nickpaints toggle player <username> [true/false]` | Disables or enables rendering for a specific player. Supports tab-completion for online and already-disabled players. |
| `/nickpaints toggle list` | Displays the current global rendering status and lists all players for whom paints are locally disabled. |

---

<img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="License">

This project is licensed under the MIT License. See the `LICENSE` file for details.
