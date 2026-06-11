# treasure_hunt

# 🏴‍☠️ Treasure Hunt
 
> Explore the island. Solve the riddles. Claim the gold.
 
A browser-based 2D adventure game built with vanilla JavaScript and HTML5 Canvas. Navigate a procedurally generated island, collect clue scrolls, manage your torch fuel, and race to find the hidden treasure before darkness takes you.
 
---
 
## 🎮 Gameplay
 
- **Explore** a randomly generated island map — every run is different
- **Find 5 clue scrolls** scattered across the island, each with a riddle leading you closer to the treasure
- **Manage your torch** — it burns out over time, and darkness means game over
- **Collect coins** along the way to boost your score
- **Race the clock** — the faster you find the treasure, the higher your score
---
 
## 🕹️ Controls
 
| Key | Action |
|-----|--------|
| `W` `A` `S` `D` / Arrow Keys | Move |
| `E` | Interact / Pick up item |
| `I` | Toggle inventory |
| `M` | Toggle mini-map |
| `ESC` | Pause |
 
> 📱 On mobile/touch devices, an on-screen D-pad appears automatically.
 
---
 
## 🚀 Getting Started
 
### Play instantly (no install)
Just open `index.html` in your browser — no build step, no server needed.
 
### Run with a local dev server
```bash
# Clone the repo
git clone https://github.com/naimur829/treasure_hunt.git
cd treasure_hunt
 
# Install dev dependencies
npm install
 
# Start local server with live reload
npm run dev
```
 
Then open `http://localhost:3000` in your browser.
 
---
 
## 📁 Project Structure
 
```
treasure_hunt/
├── index.html                  # Entry point
├── src/
│   ├── index.js                # App bootstrap & screen routing
│   ├── game.js                 # Core game loop
│   ├── components/
│   │   ├── map.js              # Procedural island map generation
│   │   ├── player.js           # Player movement & state
│   │   ├── items.js            # Coins, scrolls, treasure chest logic
│   │   └── renderer.js         # Canvas rendering pipeline
│   ├── utils/
│   │   ├── helpers.js          # Math, random, formatting utilities
│   │   └── storage.js          # Leaderboard & settings (localStorage)
│   └── styles/
│       ├── main.css            # Global styles, menus, panels
│       └── game.css            # HUD, canvas, in-game overlays
├── docs/                       # Screenshots, design notes
├── package.json
└── README.md
```
 
---
 
## 🛠️ Tech Stack
 
- **Vanilla JavaScript** (ES6+) — no frameworks, no dependencies
- **HTML5 Canvas** — all game rendering
- **CSS3** — UI, menus, animations
- **localStorage** — leaderboard and settings persistence
---
 
## 🗺️ Roadmap
 
- [ ] Multiple difficulty levels (Easy / Normal / Hardcore)
- [ ] More island biomes (jungle, desert, snow)
- [ ] Enemy creatures that patrol the map
- [ ] Torch refill items hidden in the world
- [ ] Sound effects and ambient music
- [ ] Online leaderboard
---
 
## 📜 License
 
MIT © [naimur829](https://github.com/naimur829)
