# HeadPets

This plugin adds pets as floating player heads (invisible `ArmorStand` + `Player Head`), with a GUI menu, tabs, pagination, particles and passive effects.
<img src="https://github.com/Jorget01/HeadPets/blob/main/media/0215.gif" alt="HUD" width="100%"/>
## Features

- `/pets` opens the menu.
- Tabs:
  - **All pets** — shows every pet, including locked ones (if enabled in config).
  - **My pets** — shows only pets the player has permission for.
- Pages:
  - Navigation arrows at the bottom switch pages.
- Pet activation:
  - **Left click** a pet — activate/deactivate (pet floats near the player and follows them).
  - Multiple pets can be active at once (limit is `pets-settings.max-active-per-player`).
- Particles:
  - **Particles** button — selects the player’s global particle type (**LMB** next, **RMB** previous).
  - **Shift+LMB** on **Particles** — enable/disable particles (if `particles.allow-player-disable` is enabled).
  - If `particles.per-pet-selection` is enabled, you can select particles **per pet**:
    - **RMB** on a pet — next particle
    - **Shift+RMB** on a pet — previous particle
  - Particles can be visible for everyone or only for the owner (config).
- Passive effects:
  - Each pet can apply potion effects while it is active (config).
- Languages:
  - Russian and English (`lang/ru.yml`, `lang/en.yml`)
  - `settings.language.mode: auto` selects language by player locale (ru => Russian, otherwise English).

## Commands & permissions

- `/pets` — open menu
  - permission: `pats.pets.open` (default: true)
- `/pets reload` or `/petsreload` — reload config/lang/pets (no server restart)
  - permission: `pats.pets.reload` (default: op)
- `/pets add ...` or `/petadd ...` — add a pet to `config.yml`
  - permission: `pats.pets.admin` (default: op)
- `/pets remove <id>` or `/petremove <id>` — remove a pet from `config.yml`
  - permission: `pats.pets.admin` (default: op)

Access to a specific pet is controlled via the pet’s `permission` in `config.yml` (for example: `pats.pets.head.118`).

### Command examples

Add a pet (one name will be used for both RU/EN):

```text
/petadd head_test eyJ0ZXh0dXJlcyI6... Donkey_Kong
```

Add a pet (different names for RU/EN):

```text
/petadd head_test eyJ0ZXh0dXJlcyI6... Донки_Конг Donkey_Kong
```

Remove a pet:

```text
/petremove head_test
```

## Config

Main `config.yml` sections:

- `settings.language` — language settings (`auto` / `fixed`).
- `menu.show-locked-in-all` — show locked pets in “All pets” tab.
- `menu.gui.*` — GUI settings (size, button slots, materials, pets grid area).
- `follow.*` — follow distances/speeds.
- `particles.*` — particles settings (visibility, frequency, `y-offset`, options list).
- `effects.*` — passive effects timing settings.
- `pets:` — pets list (add your own pets here).

### Colors (HEX)

You can use color codes in many strings (pet names, messages, lore):

- Legacy: `&a`, `&c`, `&7`, etc.
- HEX: `&#ff00ff` (example: `&#ff00ffDonkey Kong`)

### Pet example

```yml
pets:
  - id: head_118
    # permission is optional (default: pats.pets.<id>)
    permission: pats.pets.head.118
    display:
      ru: "Донки Конг"
      en: "Donkey Kong"
    head:
      # profile-id is optional
      profile-name: "mcheads-118"
      textures: "BASE64_TEXTURES_HERE"
    effects:
      - type: SPEED
        amplifier: 0
      # shorthand:
      - "JUMP_BOOST:0"
```

## Notes

- Player particle choice and language override are stored in `plugins/PatsPets/data.yml`.
- Active pets are stored in `plugins/PatsPets/data.yml` and restored when the player rejoins.
- By default, effects are removed when a pet is deactivated (`effects.remove-on-deactivate: true`). If disabled, effects will simply expire based on `effects.duration-ticks`.
