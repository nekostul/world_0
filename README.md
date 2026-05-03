# World_0

`World_0` — хоррор-мод для Minecraft Java Edition на Forge `1.20.1`.

По задумке это не “мод на одного моба” и не линейный сценарий, а набор аномалий, ложных состояний мира, поддельных присутствий, чат-сцен, визуальных сбоев и отдельных событий, которые постепенно делают обычное выживание всё менее надёжным.

Официальное описание мода в проекте:

> Standard world maintenance module. If a chunk blinks, proceed as usual.

## Что уже есть в моде

В проекте уже реализованы несколько независимых систем, которые могут срабатывать в разное время игры:

- чатовые и нарративные события (`double chat`, `late chat`, `sky-watch`);
- подземные события (`footsteps`, `last block`, `mine presence`);
- состояния контроля игрока (`freeze`, `paralysis`, `fall`);
- крупные события и фазы хоррора (`watching`, `stalker`, `void call`, `growth`, `swarm`, `time loop`, `glitch rain`);
- пространственные аномалии и структуры;
- кастомные измерения: `house`, `house_bad`, `koridor`, `void`, `voidportal`;
- кастомные сущности;
- клиентские контроллеры для звука, экранов, оверлеев и спецэффектов;

Мод опирается не только на один триггер, а на комбинацию серверной логики, клиентских контроллеров, сетевых пакетов, миксинов, кастомных измерений, структур и звуковых ресурсов.

## Техническая база

- Minecraft: `1.20.1`
- Forge: `47.4.10`
- Java: `17`
- Gradle: wrapper в репозитории
- Mappings: `official`
- Mixin: `SpongePowered Mixin`

## Сборка

Основная команда сборки:

```powershell
.\gradlew.bat compileJava
```

Полная сборка jar:

```powershell
.\gradlew.bat build
```

Готовый jar после `build` лежит в:

```text
build/libs/
```

## Запуск в dev-режиме

Клиент:

```powershell
.\gradlew.bat runClient
```

Игровая директория для dev-запуска:

```text
run/
```

Там же будут:

- логи: `run/logs/`
- generated config после запуска Forge
- миры и временные данные dev-сессий

## Конфиг

В проекте сейчас явно вынесен общий конфиг:

- `house_event` в [WorldZeroConfig.java](\src\main\java\ru\nekostul\worldzero\config\WorldZeroConfig.java)

Через него настраиваются, например:

- окно активности события дома;
- задержки и интервалы повторов;
- дистанции срабатывания;
- параметры детектора дома;
- поведение сцен с ложным строительством и исчезновением.

Forge создаёт итоговый `.toml` после запуска игры.

## Структура проекта

Ключевые директории:

- [src/main/java/ru/nekostul/worldzero](\src\main\java\ru\nekostul\worldzero) — основной код мода;
- [src/main/java/ru/nekostul/worldzero/event](\src\main\java\ru\nekostul\worldzero\event) — серверные игровые события;
- [src/main/java/ru/nekostul/worldzero/client](\src\main\java\ru\nekostul\worldzero\client) — клиентские контроллеры, экраны, оверлеи и рендер;
- [src/main/java/ru/nekostul/worldzero/network](\src\main\java\ru\nekostul\worldzero\network) — пакеты и регистрация сети;
- [src/main/java/ru/nekostul/worldzero/dimension](\src\main\java\ru\nekostul\worldzero\dimension) — кастомные измерения и их логика;
- [src/main/java/ru/nekostul/worldzero/entity](\src\main\java\ru\nekostul\worldzero\entity) — сущности и связанная логика;
- [src/main/java/ru/nekostul/worldzero/mixin](\src\main\java\ru\nekostul\worldzero\mixin) — mixin-слой;
- [src/main/resources/assets/worldzero](\src\main\resources\assets\worldzero) — звуки, текстуры, локализация, модели;
- [src/main/resources/data/worldzero](\src\main\resources\data\worldzero) — структуры, измерения и прочие data-driven ресурсы.

## Ресурсы

В моде уже есть:

- кастомные `.ogg`-звуки;
- локализации `ru_ru` и `en_us`;
- структуры `portal`, `portal2`, `dom`, `dom2`, `house`, `house_bad`, `koridor`;
- item-модель `blank_disc`;
- собственный `worldzero.mixins.json`.

## Лицензия

Текущая лицензия проекта: `MIT`.

См. также [LICENSE](LICENSE).

## Для разработчика

Если ты правишь механику мода, полезно держать в голове текущее правило проекта:

- изменения лучше делать точечно;
- не переписывать существующую архитектуру без необходимости;
- после правок проверять хотя бы `.\gradlew.bat compileJava`.