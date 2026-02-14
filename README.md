# PatsPets (Paper 1.21.x)

Плагин добавляет питомцев в виде летающих голов игроков (ArmorStand + Player Head), меню с вкладками и выбором партиклов/эффектов.

## Возможности

- Команда `/pets` открывает меню.
- Вкладки:
  - **Все питомцы** — показывает всех питомцев, включая недоступных (если включено в конфиге).
  - **Мои питомцы** — показывает только питомцев, к которым у игрока есть доступ (permission).
- Страницы:
  - Стрелки внизу меню перелистывают список питомцев.
- Активация питомца:
  - ЛКМ по питомцу — активировать/деактивировать (питомец летает рядом и следует за игроком).
  - Можно активировать несколько питомцев (лимит настраивается в `pets-settings.max-active-per-player`).
- Партиклы:
  - Кнопка **Партиклы** в меню — выбирает общий тип партиклов игрока (ЛКМ следующий, ПКМ предыдущий).
  - Shift+ЛКМ по кнопке **Партиклы** — вкл/выкл партиклы (если включено `particles.allow-player-disable`).
  - Если включено `particles.per-pet-selection`, то можно выбрать партиклы **для конкретного питомца**:
    - ПКМ по питомцу — следующий партикл
    - Shift+ПКМ по питомцу — предыдущий партикл
  - Партиклы могут быть видны всем или только владельцу (настраивается в конфиге).
- Пассивные эффекты:
  - Каждый питомец может давать игроку potion-эффекты, пока активен (настраивается в конфиге).
- Языки:
  - Русский и английский (`lang/ru.yml`, `lang/en.yml`)
  - `settings.language.mode: auto` выбирает язык по locale игрока (ru => русский, иначе английский).

## Установка

1. Собери или возьми готовый jar.
2. Положи jar в `plugins/` Paper сервера.
3. Перезапусти сервер.

Файлы появятся здесь:
- `plugins/PatsPets/config.yml`
- `plugins/PatsPets/data.yml`
- `plugins/PatsPets/lang/ru.yml`
- `plugins/PatsPets/lang/en.yml`

## Сборка

Windows:
```bat
.\gradlew.bat clean build
```

Готовый jar:
- `build/libs/pats-plugin-1.0.0.jar`

## Команды и permissions

- `/pets` — открыть меню
  - permission: `pats.pets.open` (default: true)
- `/pets reload` или `/petsreload` — перезагрузить конфиг/языки/питомцев (без перезапуска сервера)
  - permission: `pats.pets.reload` (default: op)
- `/pets add ...` или `/petadd ...` — добавить питомца в `config.yml`
  - permission: `pats.pets.admin` (default: op)
- `/pets remove <id>` или `/petremove <id>` — удалить питомца из `config.yml`
  - permission: `pats.pets.admin` (default: op)

Доступ к конкретным питомцам задаётся через `permission` у питомца в `config.yml` (например `pats.pets.head.118`).

### Примеры команд

Добавить питомца:

```text
/petadd head_test eyJ0ZXh0dXJlcyI6... Донки_Конг Donkey_Kong
```

Удалить питомца:

```text
/petremove head_test
```

## Конфиг

Основные секции `config.yml`:

- `settings.language` — выбор языка (`auto` / `fixed`).
- `menu.show-locked-in-all` — показывать ли недоступных питомцев во вкладке “Все”.
- `menu.gui.*` — настройка GUI (размер, слоты кнопок, материалы, область сетки питомцев).
- `follow.*` — дистанции/скорости следования питомца.
- `particles.*` — настройки партиклов (видимость, частота, `y-offset`, список опций).
- `effects.*` — частота/длительность пассивных эффектов.
- `pets:` — список питомцев (можно добавлять новые).

### Цвета (HEX)

Во многих строках (например названия питомцев, сообщения, лор) можно использовать цвет-коды:

- `&a`, `&c`, `&7` и т.п.
- HEX: `&#ff00ff` (пример: `&#ff00ffДонки Конг`)

### Пример питомца

```yml
pets:
  - id: head_118
    # permission optional (default: pats.pets.<id>)
    permission: pats.pets.head.118
    display:
      ru: "Донки Конг"
      en: "Donkey Kong"
    head:
      # profile-id optional
      profile-name: "mcheads-118"
      textures: "BASE64_TEXTURES_HERE"
    effects:
      - type: SPEED
        amplifier: 0
      # shorthand:
      - "JUMP:0"
```

## Примечания

- Выбранные партиклы и языковые настройки игрока сохраняются в `plugins/PatsPets/data.yml`.
- Активные питомцы сохраняются в `plugins/PatsPets/data.yml` и восстанавливаются при перезаходе.
- По умолчанию эффекты снимаются при деактивации питомца (`effects.remove-on-deactivate: true`), но можно выключить и тогда они будут просто заканчиваться по `effects.duration-ticks`.
