# Prompt template for new GameLife pictures

Copy this, fill in the brackets, and attach `palette.png` plus one or two files from `reference/`.

---

Create a pixel-art sprite for a game. Follow every rule:

- Subject: [a hero look: "a hooded netrunner-mage in teal robes" | an outfit (the same hero body, new clothes) | a map skin icon: "a tiny night sky over a neon city"]
- Canvas: exactly 32x32. [For animation: a horizontal strip of 4 frames, each frame the size above: an idle loop with a gentle bob and a pulsing glow.]
- Style: clean pixel art, nerdy sci-fi fantasy, matching the attached reference sprites in outline weight and detail.
- Colours: use ONLY the 32 colours of the attached palette, plus full transparency. No other colours.
- Pixels: no anti-aliasing, no blur, no gradients that need extra colours, no semi-transparent pixels. Every pixel is either one palette colour or fully transparent.
- Light from the top left. A 1-pixel dark outline (#12121a) around the silhouette.
- Background: fully transparent (a skin icon may fill the canvas).
- Output: a PNG at the exact size, not upscaled.

---

After generating, run:

```bash
node content/tools/palette-snap.mjs generated.png content/hero/hero_04.png
node content/tools/validate-content.mjs
```

Then add `content/hero/hero_04.json` with `{"name": "..."}` and press
**Settings → Reload content**.
