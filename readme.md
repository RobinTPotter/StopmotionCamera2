## Download

[Latest Release](https://github.com/RobinTPotter/StopmotionCamera2/releases)

## Build Status

![Build Status](https://github.com/RobinTPotter/StopmotionCamera2/actions/workflows/android.yml/badge.svg)

# Revenge of the Stopmotion Camera 

Takes photographs using the back camera and stores in `Pictures/StopMotion/YYYYMMDD-NNN/FFFFF.jpg` where N is the zero padded "scene" number, to try and help organize things, and F is the zero padded frame number. If frames are deleted the app _should_ renumber sequentially.

Onionskinning shows the previous N frames transparently.

touch preview to focus, would be nice if light meter was separate.

buttons:

- `capture` - takes a frame
- `preview` - opens a preview activity, scrub through and play and several speeds
- `scene+` - increments the scene number
- `scene-` - decrements the scene number (min is zero)
- `settings` - shows settings screen

feed back label at the bottom on the screen.

When a scene is selected the onionskins update to show the last frames from that scene.

### settings

sliders for:

- number of onion skins - previous pictures over the preview
- opacity of topmost layer (usually kept ~0.4)
- opacity of bottom layer (usually kept ~0.4)
- opacity of the onionskins image (usually ~0.5)
- show cross hair (vertical and horizonal lines, centrally)
- show thirds (divided into thirds)

when thinking about opacity, bear in mind the layers are drawn sequentially, you will see a sharp drop off in the lower layers if the setting is less that the topmost)


Uses kotlin and cameraX
