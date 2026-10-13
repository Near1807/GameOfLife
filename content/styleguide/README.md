# GameLife style guide (content contract version 1)

Everything in this folder is for people and AIs who make pictures for GameLife. A file that follows
these rules drops into `content/` and appears in the app after **Settings → Reload content**.

## The look

- **Pixel art only for the hero and the shop looks.** The tower-defense map, towers and enemies are vector shapes drawn by the app (v4), and the interface is a modern dark UI: neither needs art.
- **Mood:** nerdy sci-fi fantasy. Hooded hacker-mages, glitch creatures, server farms, neon cities.
- **Light:** comes from the top left. Every shape has a light side and a dark side.
- **Outline:** a 1-pixel dark outline (`#12121a`) around the silhouette, not inside it.
- **No anti-aliasing:** no blurred edges, no gradients made of many colours, no semi-transparent pixels.
- **Colours:** only the 32 colours of `../palette.json` (see `palette.png`), plus full transparency.

## Sizes and files

| What | Folder and name | Size | Notes |
| --- | --- | --- | --- |
| Hero look | `content/hero/hero_NN.png` | 32x32 per frame | Idle strip of 4 frames: 128x32. Changes every 10 overall levels |
| Shop look | `content/items/NAME.png` + `NAME.json` | 32x32 | Outfits are a 4-frame hero strip (128x32); skins are a 32x32 icon. See `content/schemas/item.schema.json` |
| Theme | `content/themes/NAME.json` | (none) | Design tokens, see `content/schemas/theme.schema.json` |

- **Names:** lowercase. `NN` is a number; the app sorts by it.
- **Animation:** one horizontal strip per animation, all frames the same size. Idle: 4 frames at 6 fps.
- **Scaling:** the app only scales by whole numbers and never smooths. Draw at the real size; never upscale before saving.

## Before you add a file

```bash
node content/tools/palette-snap.mjs my_picture.png content/hero/hero_04.png   # fixes colours, checks the size
node content/tools/validate-content.mjs                                      # checks everything
```

## Reference sprites

`reference/` holds an approved example: the hero (`hero_01`). Match its outline weight, shading
and level of detail.

## Prompt template for an image AI

See `prompt-template.md`.
