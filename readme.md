## Download

[Latest Release](https://github.com/RobinTPotter/StopmotionCamera2/releases)

## Build Status

![Build Status](https://github.com/RobinTPotter/StopmotionCamera2/actions/workflows/android.yml/badge.svg)

Revenge of the Stopmotion Camera

Takes photographs using the back camera and stores in `Pictures/StopMotion/YYYYMMDD-NNN/FFFFF.jpg` where N is the zero padded "scene" number, to try and help organize things, and F is the zero padded frame number. If frames are deleted the app _should_ renumber sequentially.

Onionskinning shows the previous 3 frames transparently.

buttons:

- `capture frame` - takes a frame
- `preview scene` - opens a preview activity, scrub through and play and several speeds
- `up scene` - increments the scene number
- `down scene` - decrements the scene number (min is zero)

feed back label at the bottom on the screen.

When a scene is selected the onionskins update to show the last frames from that scene.

Uses kotlin and cameraX
