# MYTHOS Characters 0.1.4

Experimental client-side anatomical player renderer. Minecraft 1.21.1 / Forge 52.1.5+ / Java 21.
Press K in game to configure quality, local body profile, optional hair volume, secondary motion and renderer enablement. Preferences persist locally. Remote players retain default body geometry. Existing vanilla skin atlas and six bone pivots are retained. The optional hair cap uses the player's existing hat texture and visibility; it is not an independent hairstyle. Secondary motion is disabled during constrained action poses.

Not yet implemented: authored HD skin/face textures, independent gender anatomy, independent elbow/knee/finger articulation, multiplayer customization synchronization, first-person hands, weapon-specific animations and armor refitting. Runtime verification is required. Disable with -Dmythoscharacters.disable=true.
