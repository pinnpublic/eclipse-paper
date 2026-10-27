# Changelog

## 1.3.5

- Public name: **Paper**. Existing bundle, feature and preference IDs are retained for upgrades.
- Added MIT license, user manual and Eclipse Marketplace publishing instructions.
- Prepared a GitHub Pages p2 update site. Rendering behavior is unchanged from 1.3.4.

## 1.3.4

- Blend native line number antialiasing with the actual paper to reduce bright/dark fringes.
- Preserve native fonts, baselines and ruler input.

## 1.3.3

- Fix repeated background cleanup overriding changes made after decoration stopped.
- Reuse unchanged preference previews and reduce temporary allocations in ruler compositing.
- Release the native image if GC creation fails.

## 1.3.2

- Fix redundant background recreation and improve native ruler/lifecycle rendering.
- Refresh the editor geometry and paper after a workbench restore.

The local frozen v1.0 snapshot uses internal version 1.2.7 to preserve upgrade ordering.
