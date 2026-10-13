# Prompt template for new GameLife pictures

Copy this, fill in the brackets, and attach `palette.png` plus one or two files from `reference/`.

---

Create a pixel-art sprite for a game. Follow every rule:

- Subject: [a boss: "a glitching cyber-moth made of corrupted code" | a hero look | a shop item: "a charm shaped like a frozen hourglass" | a region background: "a floating library in a violet nebula"]
- Canvas: exactly [64x64 for a boss | 32x32 for a hero or item | 320x180 for a region]. [For animation: a horizontal strip of 4 frames, each frame the size above: an idle loop with a gentle bob and a pulsing glow.]
- Style: clean pixel art, nerdy sci-fi fantasy, matching the attached reference sprites in outline weight and detail.
- Colours: use ONLY the 32 colours of the attached palette, plus full transparency. No other colours.
- Pixels: no anti-aliasing, no blur, no gradients that need extra colours, no semi-transparent pixels. Every pixel is either one palette colour or fully transparent.
- Light from the top left. A 1-pixel dark outline (#12121a) around the silhouette.
- Background: fully transparent (except for region backgrounds, which fill the whole canvas). For a region, keep the ground at y = 150 so characters can stand on it.
- Output: a PNG at the exact size, not upscaled.

---

After generating, run:

```bash
node content/tools/palette-snap.mjs generated.png content/enemies/boss_11.png
node content/tools/validate-content.mjs
```

Then add `content/enemies/boss_11.json` with `{"name": "...", "title": "..."}` and press
**Settings → Reload content**.
