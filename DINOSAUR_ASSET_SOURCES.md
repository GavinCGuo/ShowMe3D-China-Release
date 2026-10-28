# Dinosaur detective asset sources

The lesson bundles converted GLB downloads from the following public Sketchfab
model pages. Static museum scans are reduced to the headset presentation budget
with `tools/optimize_dinosaur_asset.py`; the animated model keeps its armature
and first embedded animation clip.

| Bundled asset | Source |
| --- | --- |
| `raptor_claw.glb` | https://sketchfab.com/3d-models/raptor-claw-d7de938402094c88a1e2e99beafe4f77 |
| `trex_tooth.glb` | https://sketchfab.com/3d-models/ypm-vp004192-tyrannosaurus-rex-tooth-4d1324d15f564b0194b9018bca97f35e |
| `trex_skull.glb` | https://sketchfab.com/3d-models/tyrannosaurus-rexskull-and-jaw-8ead7e1ec1c5401ba8cbc06727d6e7e8 |
| `dinosaur_footprint.glb` | https://sketchfab.com/3d-models/dinosaur-footprint-mount-morgan-a1-f81831f616ae47eab3a8a179efa0c4c9 |
| `archaeopteryx_fossil.glb` | https://sketchfab.com/3d-models/archaeopteryx-fossil-cast-eea4d66eaad34adb80969d9939835d05 |
| `trex_brain_endocast.glb` | https://sketchfab.com/3d-models/tyrannosaurus-rex-brain-endocast-amnh-5117-f4303dc6b9df423b809be241f2b73e04 |
| `trex_skeleton.glb` | https://sketchfab.com/3d-models/t-rex-3d-skeleton-model-tyrannosaurus-36dee36d3bcb4005af240b044e7a83e7 |
| `trex_animated.glb` | https://sketchfab.com/3d-models/animated-t-rex-12d68c0009614dc3aff7e68c901185bd |

The cover, CT scan panel, fossil-gallery panorama, and Cretaceous-forest panorama
were generated for this project with the built-in image generation workflow. The
two panoramas are equirectangular 2:1 images and are used only on pages 4 and 7.

`tools/patch_dinosaur_glb_presentation.py` gives the untextured skull and very
pale skeleton stable fossil-bone base colors, and removes the footprint scan's
viewer-space root rotation so its surface faces the audience.
