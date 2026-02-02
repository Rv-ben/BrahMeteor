## Turret validation checklist (manual)

### 1) Launch a dev client
From repo root:

```bash
./gradlew runClient
```

### 2) Create a test world
- Create a new Creative world (flat works best).
- (Optional) Enable cheats if prompted.

### 3) Give yourself test items
Run these commands:

```mcfunction
/give @p brahmeteor:turret
/give @p brahmeteor:meteor
/give @p minecraft:cobblestone 64
```

### 4) Validate multiblock placement + break behavior
- Place `brahmeteor:turret` on flat ground.
  - Expected: it places as a **2x2** structure.
- Switch to Survival and break **any** of the 4 blocks.
  - Expected: all 4 blocks disappear, and you get **exactly 1 turret item** back.

### 5) Validate hopper ammo intake
- Place a hopper pointing into the turret’s **controller block** (one of the 2x2 corners; the controller is the block you initially placed).
- Put cobblestone (or any block item) into the hopper.
  - Expected: the turret can accept the items (ammo).

### 6) Validate targeting + projectile item rendering
Spawn meteors near the turret:

- Use the `brahmeteor:meteor` item a few times, or summon directly:

```mcfunction
/summon brahmeteor:falling_meteor ~ ~80 ~
```

- Expected:
  - The turret fires projectiles when meteors are nearby and it has ammo.
  - The projectile is rendered as the ammo **item** (e.g., a cobblestone item).
  - On hit, the meteor **disappears midair** (no ground explosion from that meteor).

### 7) Validate “no lingering light blocks”
After shooting down meteors, run this near the fight area:

```mcfunction
/fill ~-80 ~-20 ~-80 ~80 ~150 ~80 air replace minecraft:light
```

- Expected: it replaces **0** blocks (or at least doesn’t keep accumulating), indicating meteors aren’t leaving behind `minecraft:light` blocks when destroyed midair.

