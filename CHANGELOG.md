# Changelog

## 1.3.14 — 2026-10-29

- Stop the inline Find/Replace overlay from repeatedly resizing the source viewer and moving its search bar. Keep settled viewer bounds during overlay layout requests while still reacting to window, font, scrollbar and ruler size changes.
- Leave floating controls at their native positions instead of shifting them with the notebook's top inset.
- Add a real-workbench inline search regression covering idle layout events, query input, Replace expansion, resizing, font and line-number width changes, and reopening.

## 1.3.13 — 2026-10-28

- Keep the overview ruler and scrollbar surroundings in the editor's light/dark CSS theme color instead of inheriting the notebook paper background.
- Reapply the overview background before native painting so window focus changes preserve the same solid color. Retain native annotations and input.
- Restore previous overview backgrounds when decoration stops and release the paint filter and owned colors.

## 1.3.12 — 2026-10-28

- Synchronize source and gutter painting during scrolling, including partial rows, wrapped Korean text and large fonts. Retain the previous complete gutter frame until the matching source frame is ready.
- Extend paper through line number, annotation and folding gutters. Hide Quick Diff backgrounds and range stripes while preserving diagnostics, folding and native input.
- Reuse unchanged paper and rule images, emit bitmap formats accepted directly by Win32 SWT, and reuse gutter capture buffers.
- Preserve existing paper colors, transparency and line geometry; verify 2,880 old/new RGB and alpha raster comparisons.
- In a controlled 80-frame workload, median frame time decreased from 46.530ms in 1.3.11 to 29.034ms in 1.3.12. This measures the test viewer, not overall IDE performance.
- Validate 288 runtime checks each on STS3 / Eclipse 4.21 / Java 11, STS5 / Eclipse 4.40 / Java 21, and Eclipse 2026-09 / 4.41 / Java 21, plus native wheel, paint-order, rendering and resource regressions.

## 1.3.5

- Public name: **Paper**.
- Added MIT license, user manual and Eclipse Marketplace publishing instructions.
- Added a GitHub Pages p2 update site.

## 1.3.4

- Blend native line number antialiasing with the actual paper to reduce bright/dark fringes.

## 1.3.3

- Fix repeated background cleanup overriding changes made after decoration stopped.
- Reuse unchanged preference previews and reduce temporary allocations in ruler compositing.
- Release the native image if GC creation fails.

## 1.3.2

- Fix redundant background recreation and improve native ruler/lifecycle rendering.
- Refresh the editor geometry and paper after a workbench restore.
