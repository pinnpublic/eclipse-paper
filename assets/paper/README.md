# Paper texture image sources

Created with the built-in `image_gen` tool on 2026-10-26 for Paper (development version 1.2.0). These are AI-generated photographic-style materials, not scans of independently photographed sheets. Both selected outputs are bundled in the plug-in; no network request is made at runtime. The assets are distributed under the repository's MIT License.

- `assets/paper/crumpled.png`: source for light/heavy/unfolded crumple and glassine variants. The renderer samples the material and applies the chosen paper color and texture strength.
- `assets/paper/xuan.png`: source for Xuan/raw/sized calligraphy paper variants, combined with different procedural pulp and fibre details.

The original PNGs are kept unchanged. Runtime sampling blends opposite borders and renders at the requested display DPI. Text and notebook rule rendering remain separate.

## Crumpled paper prompt

Use case: photorealistic-natural. Asset type: an edge-to-edge paper material texture for a text editor background, one square 1024x1024 image. Primary request: an authentic photographic flatbed scan of a real blank warm-white sheet of thin office paper that was crumpled into a ball, then carefully unfolded and flattened. Cover the entire frame only with paper. Natural irregular angular and branching creases, broad softly lit facets, small fine wrinkles, tactile real cellulose grain. The paper is flattened and usable for writing. Soft diffuse overhead illumination with very subtle side light to reveal the folds. Mostly pale ivory / warm off-white with restrained soft light grey crease shadows, suitable behind readable black source code. Real paper relief, no raised ropes, no drawn pencil lines, no cracks in stone, no Voronoi cells or repetitive grid, no borders or page edges, no desk or objects, absolutely no letters, writing, text, graphics, watermarks, gradients, vignette, stains or high contrast black details. Full frame continuous fairly uniform material, no dramatic folds or mountains.

## Xuan paper prompt

Use case: photorealistic-natural. Asset type: a square 1024x1024 edge-to-edge natural paper material for a code editor background. Primary request: a faithful macro flatbed-scan photograph of blank traditional Chinese Xuan calligraphy paper (화선지, rice and sandalwood-bark fibre paper), premium unsized raw handmade Xuan. Warm ivory, incredibly fine translucent interlaced cellulose fibres, soft organic patches of thin and thick pulp, subtly cloudy absorbent surface and delicate randomly oriented hairlike paper fibres that are part of the sheet. Real tactile fibrous handmade paper. Flat and unwrinkled. No repeated horizontal lines or textile weave; very different from fabric. Even diffuse light, no vignette or dramatic shadows. Mostly off-white, low contrast, usable behind black printed text. The paper fills the complete frame with no page edge, desk or objects. Absolutely no ink, calligraphy, letters, text, watermarks, decorative patterns, obvious dark hairs or black speckles, stains, borders or objects. Natural fine material details rather than illustrated strokes. Uniform all-over seamless-style texture.
