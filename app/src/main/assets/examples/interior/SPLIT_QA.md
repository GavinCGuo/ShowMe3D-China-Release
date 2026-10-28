# Interior split reconstruction QA

The open-room source is split into one shell, 13 individual furniture objects,
and 16 detail objects. Different objects are never combined. The only deliberate
multi-node assets are the two-layer framed artwork and the authored ceiling-light
row. The packaged files cover all 45 visual mesh nodes; only the 42 ceiling
surface triangles are omitted to keep the room open from above.

Validation on 2026-08-11 compared every split mesh primitive against the
normalized monolithic open-room reference:

- packaged GLB files: 30;
- mesh primitives compared: 45;
- missing nodes: 0;
- extra nodes: 0;
- index-array mismatches: 0;
- maximum position difference: `2.9802322387695312e-8` meters.

Every placed object uses the same page `TransformData`, so recombination in
ShowMe3D preserves the authored fit instead of approximating it by eye. Staged
previews enlarge each object around its measured mesh center, without changing
the shared-frame asset used for final placement.
