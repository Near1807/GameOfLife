# GameLife style guide (content contract version 1)

Everything in this folder is for people and AIs who make pictures for GameLife. A file that follows
these rules drops into `content/` and appears in the app after **Settings → Reload content**.

## The look

- **Pixel art only for game content:** hero, bosses, items, regions. The interface around it is a modern dark UI and needs no art.
- **Mood:** nerdy sci-fi fantasy. Hooded hacker-mages, glitch creatures, server farms, neon cities.
- **Light:** comes from the top left. Every shape has a light side and a dark side.
- **Outline:** a 1-pixel dark outline (`#12121a`) around the silhouette, not inside it.
- **No anti-aliasing:** no blurred edges, no gradients made of many colours, no semi-transparent pixels.
- **Colours:** only the 32 colours of `../palette.json` (see `palette.png`), plus full transparency.

## Sizes and files

| What | Folder and name | Size | Notes |
| --- | --- | --- | --- |
| Boss | `content/enemies/boss_NN.png` | 64x64 per frame | Optional idle strip of 4 frames: 256x64. Optional `boss_NN.json`: `{"name": "...", "title": "..."}` |
| Hero look | `content/hero/hero_NN.png` | 32x32 per frame | Idle strip of 4 frames: 128x32. Changes every 10 overall levels |
| Region | `content/regions/region_NN.png` | 320x180 | Ground line at y = 150: the fighters stand on it |
| Shop item | `content/items/NAME.png` + `NAME.json` | 32x32 | Outfits are a 4-frame hero strip (128x32). See `content/schemas/item.schema.json` |
| Theme | `content/themes/NAME.json` | (none) | Design tokens, see `content/schemas/theme.schema.json` |

- **Names:** lowercase. `NN` is a number; the app sorts by it.
- **Animation:** one horizontal strip per animation, all frames the same size. Idle: 4 frames at 6 fps.
- **Scaling:** the app only scales by whole numbers and never smooths. Draw at the real size; never upscale before saving.

## Before you add a file

```bash
node content/tools/palette-snap.mjs my_picture.png content/enemies/boss_11.png   # fixes colours, checks the size
node content/tools/validate-content.mjs                                          # checks everything
```

## Reference sprites

`reference/` holds approved examples: the hero (`hero_01`), two bosses (`boss_01` beetle,
`boss_03` mechanical eye), a region (`region_01`) and an item icon. Match their outline weight,
shading and level of detail.

## Prompt template for an image AI

See `prompt-template.md`.
