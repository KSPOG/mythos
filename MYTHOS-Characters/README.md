# MYTHOS Characters 0.1.6

Experimental client-side anatomical player renderer. Minecraft 1.21.1 / Forge 52.1.5+ / Java 21.
Press K in game to configure quality, local body profile, optional hair volume, secondary motion and renderer enablement. Preferences persist locally. Remote players retain default body geometry. Existing vanilla skin atlas and six bone pivots are retained. The optional hair cap uses the player's existing hat texture and visibility; it is not an independent hairstyle. Secondary motion is disabled during constrained action poses.

Not yet implemented: authored HD skin/face textures, independent gender anatomy, independent elbow/knee/finger articulation, multiplayer customization synchronization, first-person hands, weapon-specific animations and armor refitting. Runtime verification is required. Disable with -Dmythoscharacters.disable=true.

0.1.6: reset all mutable model poses before animation; coherent pelvis-centered upper-body motion, unarmored knee deformation and fuller torso/shoulder connections. This is not a full skeletal rig. In-game rendering and armor fit still need validation.

0.1.6: procedural idle, walk, run, jump rise, fall, landing, crouch and crouch-walk clips with per-player transitions and two-bone limb skinning. Existing armor and held item attachment constraints mean elbow bending is only available with empty hands and no chest armor; knee bending only without leg/foot armor. No foot IK or authored motion capture yet.
