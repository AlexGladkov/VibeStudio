# VibeStudio Design System

**Версия:** 1.0
**Дата:** 2026-03-24
**Платформа:** macOS 14+ (Sonoma)
**Фреймворк:** SwiftUI + AppKit (NSViewRepresentable)

---

## 1. Философия дизайна

### Принципы

1. **Terminal-centric** -- терминал занимает максимум пространства, все остальное -- вспомогательный контекст
2. **Keyboard-first** -- каждое действие доступно с клавиатуры; мышь опциональна
3. **Low visual noise** -- минимум декоративных элементов; информация передается цветом и типографикой, а не рамками и тенями
4. **Density over whitespace** -- developer-tool требует высокой плотности информации; пустое пространство -- потеря контекста
5. **Native-adjacent** -- берем лучшее из macOS HIG (system fonts, NSColor semantic colors, vibrancy), но намеренно отступаем там, где HIG оптимизирован для consumer-apps, а не dev-tools

### Отступления от macOS HIG

| HIG рекомендует | VibeStudio делает иначе | Причина |
|---|---|---|
| Стандартная высота тулбара 52pt | Компактный tab bar 36pt | Каждый пиксель для терминала |
| Sidebar минимум 200pt с крупными иконками | Sidebar 240pt с compact-стилем (13pt шрифт) | Плотность информации для дерева файлов |
| Accent color из System Preferences | Фиксированная accent palette | Консистентность dev-tool независимо от системных настроек |
| System font для всего | Моноширинный шрифт в терминале и git-статусах | Выравнивание кода и статусов критично |
| Стандартные NSTabView | Кастомный tab bar в стиле browser tabs | Табы проектов ближе к Chrome/VS Code чем к Finder |
| Rounded corners 10pt на карточках | Minimal 6pt radius, плоский стиль | Убираем визуальный шум |

### Что берем из HIG

- **SF Pro** как системный шрифт для UI-элементов
- **SF Symbols** для иконок -- нативный вид, автоматическая адаптация к Dynamic Type
- **NSVisualEffectView / .ultraThinMaterial** для sidebar -- vibrancy-эффект macOS
- **Semantic colors** как fallback: `NSColor.separatorColor`, `NSColor.windowBackgroundColor`
- **Focus rings** для accessibility (Tab-навигация)
- **Стандартное контекстное меню** (NSMenu) -- знакомый паттерн, не переизобретаем

---

## 2. Цветовая схема

### Dark Theme (основная)

#### Поверхности (Surfaces)

| Токен | Hex | Описание |
|---|---|---|
| `surface.base` | `#1A1B1E` | Фон терминальной области |
| `surface.raised` | `#212225` | Фон sidebar |
| `surface.overlay` | `#2A2B2F` | Фон dropdown, popover, context menu |
| `surface.tabBar` | `#17181B` | Фон tab bar (темнее base -- визуальный якорь сверху) |
| `surface.tabActive` | `#1A1B1E` | Фон активного таба (совпадает с terminal area) |
| `surface.tabInactive` | `#17181B` | Фон неактивного таба (совпадает с tab bar) |
| `surface.tabHover` | `#1F2023` | Hover-состояние неактивного таба |
| `surface.input` | `#16171A` | Фон текстовых полей (commit message и др.) |
| `surface.selection` | `#264F78` | Выделение текста в терминале |

#### Текст (Text)

| Токен | Hex | Описание |
|---|---|---|
| `text.primary` | `#D4D4D8` | Основной текст, имена файлов, содержимое терминала |
| `text.secondary` | `#8B8B93` | Вторичный текст, подписи, пути, timestamps |
| `text.muted` | `#55565C` | Неактивные элементы, placeholder |
| `text.inverse` | `#1A1B1E` | Текст на ярких фонах (badges) |

#### Рамки и разделители (Borders)

| Токен | Hex | Описание |
|---|---|---|
| `border.default` | `#2E2F33` | Разделитель sidebar / terminal, split divider |
| `border.subtle` | `#252629` | Разделитель секций внутри sidebar |
| `border.focus` | `#4A9EFF` | Focus ring для keyboard navigation |

#### Accent (действия)

| Токен | Hex | Описание |
|---|---|---|
| `accent.primary` | `#4A9EFF` | Основной accent: активный таб индикатор, selected items |
| `accent.primaryHover` | `#5BABFF` | Hover на primary accent |
| `accent.secondary` | `#7C3AED` | Вторичный accent (пока зарезервирован) |

#### Git статусы

| Токен | Hex | Описание |
|---|---|---|
| `git.modified` | `#E2B93D` | Измененные файлы (M) |
| `git.added` | `#3FB950` | Добавленные файлы (A) |
| `git.deleted` | `#F85149` | Удаленные файлы (D) |
| `git.untracked` | `#8B8B93` | Неотслеживаемые файлы (?) |
| `git.conflicted` | `#F09000` | Конфликт слияния (U) |
| `git.renamed` | `#58A6FF` | Переименованные файлы (R) |

#### Индикаторы активности (Tab Indicator)

| Токен | Hex | Описание |
|---|---|---|
| `indicator.idle` | `#3FB950` | Терминал ожидает ввода (idle) |
| `indicator.running` | `#E2B93D` | Процесс выполняется |
| `indicator.error` | `#F85149` | Процесс завершился с ошибкой (exit code != 0) |
| `indicator.success` | `#3FB950` | Процесс завершился успешно (совпадает с idle) |

#### Кнопки (Buttons)

| Токен | Hex | Описание |
|---|---|---|
| `button.primaryBg` | `#4A9EFF` | Фон primary кнопки (Commit, Push) |
| `button.primaryText` | `#FFFFFF` | Текст primary кнопки |
| `button.primaryHoverBg` | `#5BABFF` | Hover primary кнопки |
| `button.secondaryBg` | `#2A2B2F` | Фон secondary кнопки (Stage All, Pull) |
| `button.secondaryText` | `#D4D4D8` | Текст secondary кнопки |
| `button.secondaryHoverBg` | `#333438` | Hover secondary кнопки |
| `button.dangerBg` | `#3D1214` | Фон danger кнопки (Discard Changes) |
| `button.dangerText` | `#F85149` | Текст danger кнопки |
| `button.dangerHoverBg` | `#4D1719` | Hover danger кнопки |

### Light Theme (v2, зарезервировано)

Светлая тема запланирована на v2. Основные принципы:
- `surface.base` -> `#FFFFFF`
- `surface.raised` -> `#F5F5F7`
- `surface.tabBar` -> `#EBEBED`
- `text.primary` -> `#1D1D1F`
- Git-цвета сохраняются с увеличенной насыщенностью для контраста на светлом фоне
- Используются те же токены -- только значения меняются

---

## 3. Типографика

### Шрифты

| Контекст | Шрифт | Fallback | Обоснование |
|---|---|---|---|
| UI-элементы | **SF Pro Text** (system font) | `-apple-system` | Нативный macOS, отличная читаемость на Retina |
| Терминал | **JetBrains Mono** | SF Mono, Menlo | Лигатуры (!=, =>, ->), отличное различение 0/O, 1/l/I |
| Git статусы, пути файлов | **SF Mono** | Menlo | Моноширинный для выравнивания, но без лигатур |

### Размеры шрифта (в pt)

| Токен | Размер | Weight | Line Height | Где используется |
|---|---|---|---|---|
| `type.tabTitle` | 12pt | Medium (500) | 16pt | Название проекта на табе |
| `type.tabBranch` | 10pt | Regular (400) | 14pt | Имя ветки на табе |
| `type.sidebarSection` | 11pt | Semibold (600) | 14pt | Заголовки секций: FILES, GIT |
| `type.sidebarItem` | 13pt | Regular (400) | 20pt | Элементы файлового дерева |
| `type.sidebarItemSmall` | 11pt | Regular (400) | 16pt | Вторичная информация в sidebar |
| `type.gitStatus` | 11pt | Medium (500) | 16pt | Литеры M/A/D/? рядом с файлами (SF Mono) |
| `type.gitBranch` | 13pt | Medium (500) | 20pt | Название ветки в git-секции |
| `type.gitAheadBehind` | 11pt | Regular (400) | 16pt | Текст "^2 v0" в git-секции |
| `type.terminal` | 13pt | Regular (400) | 18pt | Терминальный текст (по умолчанию) |
| `type.buttonLabel` | 12pt | Medium (500) | 16pt | Текст кнопок |
| `type.commitInput` | 13pt | Regular (400) | 18pt | Поле ввода commit message |
| `type.tooltip` | 11pt | Regular (400) | 14pt | Всплывающие подсказки |

### Примечания по терминальному шрифту

- Размер по умолчанию 13pt, диапазон масштабирования: 9pt -- 24pt (Cmd+/Cmd-)
- Шаг масштабирования: 1pt
- Line height терминала = font size * 1.4 (стандарт для терминалов)
- Лигатуры JetBrains Mono включены по умолчанию, отключаемы в настройках
- Letter spacing: 0 (стандартный для моноширинного)

---

## 4. Иконки (SF Symbols)

### Sidebar -- секция Files

| Элемент | SF Symbol | Rendering | Примечание |
|---|---|---|---|
| Папка (закрыта) | `folder.fill` | Hierarchical, tint `#E2B93D` | Привычный желтый цвет папки |
| Папка (открыта) | `folder.fill` | Hierarchical, tint `#E2B93D` | macOS не различает визуально, используем disclosure triangle |
| Файл Swift | `swift` | Monochrome, tint `#F05138` | Оригинальный цвет Swift |
| Файл код (прочие) | `doc.text.fill` | Monochrome, tint `#8B8B93` | Серый для некодовых файлов |
| Файл конфиг | `gearshape.fill` | Monochrome, tint `#8B8B93` | .gitignore, .json, .yaml |
| Disclosure triangle | `chevron.right` | Monochrome, 9pt | Поворачивается на 90 при раскрытии |

### Sidebar -- секция Git

| Элемент | SF Symbol | Rendering | Примечание |
|---|---|---|---|
| Ветка | `arrow.triangle.branch` | Monochrome, tint `text.secondary` | |
| Ahead | `arrow.up` | Monochrome, tint `#3FB950` | Рядом с числом коммитов |
| Behind | `arrow.down` | Monochrome, tint `#F85149` | Рядом с числом коммитов |
| Stage file | `plus.circle` | Monochrome, tint `#3FB950` | При hover на unstaged файле |
| Unstage file | `minus.circle` | Monochrome, tint `#E2B93D` | При hover на staged файле |
| Commit | `arrow.up.doc.fill` | Monochrome | Иконка кнопки Commit |
| Push | `arrow.up.circle.fill` | Monochrome | Иконка кнопки Push |
| Pull | `arrow.down.circle.fill` | Monochrome | Иконка кнопки Pull |
| Refresh | `arrow.clockwise` | Monochrome | Обновить git-статус вручную |

### Tab Bar

| Элемент | SF Symbol | Rendering | Примечание |
|---|---|---|---|
| Закрыть таб | `xmark` | Monochrome, 9pt | Появляется при hover на табе |
| Добавить проект | `plus` | Monochrome, 12pt | Кнопка [+] |
| Индикатор | Нет (кастомный Circle) | -- | Рисуется программно (6pt circle + опционально glow) |

### Toolbar / прочее

| Элемент | SF Symbol | Rendering | Примечание |
|---|---|---|---|
| Split horizontal | `rectangle.split.1x2` | Monochrome | Cmd+D |
| Split vertical | `rectangle.split.2x1` | Monochrome | Cmd+Shift+D |
| Toggle sidebar | `sidebar.left` | Monochrome | Cmd+B |
| Settings | `gearshape` | Monochrome | Настройки приложения |
| Search | `magnifyingglass` | Monochrome | Будущий Cmd+P |

---

## 5. Компонент: Tab Bar

### Layout

```
 ┌──────────────────────────────────────────────────────────────────────────────┐
 │ 8px                                                                    8px  │
 │  ┌─────────────┐ 2px ┌─────────────┐ 2px ┌─────────────┐        ┌────┐    │
 │  │  proj-a     │     │● proj-b main│     │  proj-c     │   ...  │ +  │    │
 │  └─────────────┘     └─────────────┘     └─────────────┘        └────┘    │
 │                                                                             │
 └──────────────────────────────────────────────────────────────────────────────┘
```

### Размеры

| Параметр | Значение |
|---|---|
| Высота tab bar | 36pt |
| Высота таба | 28pt |
| Вертикальный отступ tab bar (top/bottom) | 4pt |
| Горизонтальный отступ tab bar (left/right) | 8pt |
| Зазор между табами | 2pt |
| Padding внутри таба (horizontal) | 12pt |
| Padding внутри таба (vertical) | 0pt (центрирование по высоте) |
| Минимальная ширина таба | 120pt |
| Максимальная ширина таба | 200pt |
| Border radius таба | 6pt |
| Кнопка [+] | 28x28pt |
| Кнопка close (x) | 16x16pt, иконка 9pt |

### Состояния таба

#### Active

- Фон: `surface.tabActive` (`#1A1B1E`)
- Нижняя граница: 2pt линия цветом `accent.primary` (`#4A9EFF`)
- Текст: `text.primary` (`#D4D4D8`), weight Medium
- Визуально "сливается" с terminal area -- создает ощущение что таб и терминал одна поверхность
- Кнопка close видна всегда (opacity 0.6, hover -> 1.0)

#### Inactive

- Фон: `surface.tabInactive` (`#17181B`)
- Текст: `text.secondary` (`#8B8B93`), weight Regular
- Кнопка close скрыта, появляется при hover на таб

#### Hover (на неактивном табе)

- Фон: `surface.tabHover` (`#1F2023`)
- Текст: `text.primary` (`#D4D4D8`)
- Кнопка close видна (opacity 0.4)
- Transition: 120ms ease-out

#### Attention (есть новый output в терминале)

- Фон: как у inactive
- Индикатор активности: цветная точка (см. секцию 6)
- Текст: `text.primary` (`#D4D4D8`) -- привлекаем внимание без раздражения

### Внутренняя структура таба

```
┌─ 12pt ─┬───────────────────────────────┬── 4pt ──┬──────┬─ 8pt ─┐
│        │  ● proj-name  main            │         │  x   │       │
└────────┴───────────────────────────────┴─────────┴──────┴───────┘
           ^                 ^
           indicator 6pt     branch (type.tabBranch, text.muted)
           + 6pt gap         8pt gap от названия
```

- Индикатор (6pt circle) + 6pt gap
- Название проекта (type.tabTitle) -- truncate с ellipsis если не влезает
- 8pt gap
- Имя ветки (type.tabBranch, text.muted) -- скрывается первой при сжатии
- 4pt gap
- Кнопка close (16x16pt)

---

## 6. Индикатор активности на табе

### Размер и позиция

- Диаметр: 6pt
- Позиция: слева от названия проекта, вертикально по центру
- Gap от текста: 6pt

### Состояния и цвета

| Состояние | Цвет | Анимация | Когда |
|---|---|---|---|
| **Idle** | `indicator.idle` (`#3FB950`) | Нет (статичная точка) | Shell ожидает ввода, нет активных процессов |
| **Running** | `indicator.running` (`#E2B93D`) | Пульсация opacity 0.4 -> 1.0, период 1.2s, ease-in-out | Процесс выполняется (компиляция, тесты, etc.) |
| **Error** | `indicator.error` (`#F85149`) | Одиночный flash (быстрое появление), затем статика | Процесс завершился с exit code != 0 |
| **Hidden** | -- | -- | Активный таб (индикатор скрыт -- пользователь уже видит терминал) |

### Анимация Running (пульсация)

```swift
// SwiftUI анимация
Circle()
    .fill(Color(hex: "#E2B93D"))
    .frame(width: 6, height: 6)
    .opacity(isRunning ? pulseOpacity : 1.0)
    .animation(
        .easeInOut(duration: 0.6)
        .repeatForever(autoreverses: true),
        value: isRunning
    )
```

- Opacity диапазон: 0.4 -- 1.0
- Полный цикл: 1.2s (0.6s dim + 0.6s brighten)
- Easing: ease-in-out
- Не используем scale-анимацию -- занимает больше пространства, мешает тексту

### Анимация Error (flash)

```swift
// При появлении ошибки
Circle()
    .fill(Color(hex: "#F85149"))
    .frame(width: 6, height: 6)
    .shadow(color: Color(hex: "#F85149").opacity(0.6), radius: 4)
    // shadow убирается через 2s
```

- При смене состояния на Error: добавляется мягкий красный glow (shadow radius 4pt, opacity 0.6)
- Через 2 секунды glow плавно убирается (300ms fade-out), точка остается статичной
- Glow привлекает внимание однократно, не раздражает постоянно

### Индикатор на активном табе

На активном табе индикатор **скрыт** -- пользователь уже видит терминал и сам наблюдает процесс. Индикатор предназначен только для неактивных табов, чтобы сигнализировать о событиях "за кадром".

---

## 7. Компонент: Sidebar

### Общие параметры

| Параметр | Значение |
|---|---|
| Ширина по умолчанию | 240pt |
| Минимальная ширина | 180pt |
| Максимальная ширина | 400pt |
| Фон | `surface.raised` (`#212225`) с `NSVisualEffectView.ultraThinMaterial` |
| Правая граница | 1pt, `border.default` (`#2E2F33`) |
| Горизонтальный padding (left/right) | 12pt |
| Toggle | Cmd+B, плавное скольжение 200ms ease-out |

### Секция: FILES

#### Заголовок секции

```
┌─ 12pt ──────────────────────────────── 12pt ─┐
│  FILES                              [refresh] │ <- 28pt высота
├──────────────────────────────────────────────┤
│  8pt top padding                              │
│  > src/                                       │ <- 28pt высота строки
│    > main/                                    │
│      M  ViewModel.swift                       │
│      A  NewFile.swift                         │
│    > test/                                    │
│  > build.gradle.kts                           │
└──────────────────────────────────────────────┘
```

| Параметр | Значение |
|---|---|
| Высота заголовка секции | 28pt |
| Шрифт заголовка | `type.sidebarSection` (SF Pro, 11pt, Semibold) |
| Цвет заголовка | `text.secondary` (`#8B8B93`) |
| Трансформация заголовка | UPPERCASE |
| Gap после заголовка | 8pt |
| Высота строки дерева | 28pt |
| Левый отступ (indent) на уровень | 16pt |
| Базовый левый отступ (level 0) | 4pt |
| Disclosure triangle | `chevron.right` 9pt, вращение 90 для раскрытого |
| Gap: disclosure -> иконка | 4pt |
| Gap: иконка -> имя файла | 6pt |
| Иконка файла | 14pt (SF Symbol point size) |

#### Строка файла с git-статусом

```
┌── indent ──┬─ 4pt ─┬─ 14pt ─┬─ 6pt ─┬──────────────────────┬─ gap ─┬─ git ─┐
│            │  >    │  icon  │       │  filename.swift      │       │  M   │
└────────────┴───────┴────────┴───────┴──────────────────────┴───────┴──────┘
```

- Git-статус литера (M/A/D/?) отображается **справа** от имени файла, прижата к правому краю sidebar
- Шрифт статуса: `type.gitStatus` (SF Mono, 11pt, Medium)
- Цвет по типу: `git.modified`, `git.added`, `git.deleted`, `git.untracked`
- Если у файла нет git-изменений -- литера не показывается

#### Состояния строки файла

| Состояние | Фон | Текст |
|---|---|---|
| Default | Прозрачный | `text.primary` |
| Hover | `#2A2B2F` (8% white overlay) | `text.primary` |
| Selected | `accent.primary` с opacity 0.15 (`#4A9EFF26`) | `text.primary` |
| Active (focused) | `accent.primary` с opacity 0.25 (`#4A9EFF40`) | `#FFFFFF` |

- Border radius при hover/selected: 4pt
- Transition: 80ms ease-out

### Секция: GIT

#### Разделитель между секциями

- Горизонтальная линия 1pt, цвет `border.subtle` (`#252629`)
- Вертикальный margin: 8pt сверху, 8pt снизу

#### Layout git-секции

```
┌─ 12pt ──────────────────────────────── 12pt ─┐
│  GIT                                          │ <- заголовок 28pt
├──────────────────────────────────────────────┤
│  8pt                                          │
│  [branch-icon] main          [branch-picker] │ <- 28pt, ветка
│  [arrow-up] 2  [arrow-down] 0                │ <- 20pt, ahead/behind
│  8pt                                          │
│  ─────── Staged Changes (2) ───────          │ <- 24pt, подзаголовок
│    M  src/ViewModel.swift          [-]       │ <- 28pt, файл + unstage
│    A  src/NewFile.swift            [-]       │
│  8pt                                          │
│  ─────── Changes (3) ───────                 │ <- 24pt, подзаголовок
│    M  src/Service.swift            [+]       │ <- 28pt, файл + stage
│    ?  TODO.md                      [+]       │
│    D  old/Legacy.swift             [+]       │
│  12pt                                         │
│  ┌──────────────────────────────────────────┐ │
│  │  Commit message...                       │ │ <- 60pt min, auto-grow
│  └──────────────────────────────────────────┘ │
│  8pt                                          │
│  ┌──────────┐ 8pt ┌───────┐ 8pt ┌──────┐    │
│  │ Stage All│     │Commit │     │ Push │    │ <- 28pt, кнопки
│  └──────────┘     └───────┘     └──────┘    │
│  8pt                                          │
│  ┌──────────────────────────────────────────┐ │
│  │              Pull                        │ │ <- 28pt, кнопка Pull
│  └──────────────────────────────────────────┘ │
│  12pt                                         │
└──────────────────────────────────────────────┘
```

#### Ветка

- Иконка: `arrow.triangle.branch` 14pt, tint `text.secondary`
- Имя ветки: `type.gitBranch` (SF Pro, 13pt, Medium), цвет `text.primary`
- Кнопка branch-picker справа: `chevron.up.chevron.down` 10pt, при клике dropdown со списком веток
- Ahead/Behind: `type.gitAheadBehind` (11pt), иконки `arrow.up` / `arrow.down` 10pt
  - Ahead > 0: цвет `#3FB950`
  - Behind > 0: цвет `#F85149`
  - Если 0: цвет `text.muted`

#### Подзаголовки (Staged Changes / Changes)

- Шрифт: `type.sidebarItemSmall` (11pt, Regular)
- Цвет: `text.secondary`
- Число в скобках: count файлов
- Линии-разделители слева и справа от текста (тонкая 1pt, `border.subtle`)

#### Список файлов в git-секции

- Формат идентичен файловому дереву, но без вложенности (flat list, полный путь)
- Hover на строке показывает кнопку stage (+) или unstage (-) справа
- Кнопка stage: `plus.circle` 14pt, tint `git.added`
- Кнопка unstage: `minus.circle` 14pt, tint `git.modified`

#### Commit message input

- Фон: `surface.input` (`#16171A`)
- Рамка: 1pt, `border.default` (`#2E2F33`), при фокусе `border.focus` (`#4A9EFF`)
- Шрифт: `type.commitInput` (13pt, Regular)
- Placeholder: "Commit message..." цвет `text.muted`
- Border radius: 6pt
- Padding: 8pt horizontal, 6pt vertical
- Минимальная высота: 60pt
- Максимальная высота: 120pt (scroll)
- Multiline: да

#### Кнопки git-действий

**Ряд 1: Stage All / Commit / Push (горизонтальный)**

| Кнопка | Тип | Ширина | Высота |
|---|---|---|---|
| Stage All | Secondary | Flexible (1fr) | 28pt |
| Commit | Primary | Flexible (1fr) | 28pt |
| Push | Primary | Flexible (1fr) | 28pt |

- Gap между кнопками: 8pt
- Распределение: flexbox с равными долями

**Ряд 2: Pull (полная ширина)**

| Кнопка | Тип | Ширина | Высота |
|---|---|---|---|
| Pull | Secondary | 100% | 28pt |

#### Стиль кнопок

**Primary (Commit, Push):**
- Фон: `button.primaryBg` (`#4A9EFF`)
- Текст: `button.primaryText` (`#FFFFFF`), 12pt Medium
- Hover: `button.primaryHoverBg` (`#5BABFF`)
- Border radius: 6pt
- Disabled: opacity 0.4 (Commit disabled когда нет staged + пустой message; Push disabled когда ahead = 0)

**Secondary (Stage All, Pull):**
- Фон: `button.secondaryBg` (`#2A2B2F`)
- Текст: `button.secondaryText` (`#D4D4D8`), 12pt Medium
- Hover: `button.secondaryHoverBg` (`#333438`)
- Border radius: 6pt
- Рамка: 1pt, `border.default`

---

## 8. Компонент: Terminal Area

### Layout

| Параметр | Значение |
|---|---|
| Фон | `surface.base` (`#1A1B1E`) |
| Padding (left/top/right/bottom) | 8pt / 4pt / 8pt / 4pt |
| Cursor | Block (мигающий), цвет `text.primary` |
| Cursor blink rate | 530ms (стандарт xterm) |
| Selection color | `surface.selection` (`#264F78`) |
| Scrollbar | macOS нативный (overlay), появляется при скролле |

### Split Panels

#### Split Divider

| Параметр | Значение |
|---|---|
| Ширина divider (горизонтальный split) | 1pt видимая линия + 4pt hit area с каждой стороны = 9pt total |
| Высота divider (вертикальный split) | 1pt видимая линия + 4pt hit area = 9pt total |
| Цвет линии | `border.default` (`#2E2F33`) |
| Цвет линии при hover | `accent.primary` (`#4A9EFF`) |
| Цвет линии при drag | `accent.primary` (`#4A9EFF`) |
| Курсор при hover | `NSCursor.resizeLeftRight` / `NSCursor.resizeUpDown` |
| Минимальный размер панели | 120pt |
| Transition подсветки | 100ms ease-out |

### Terminal Colors (ANSI palette -- Dark theme)

Терминал использует собственную ANSI-палитру. Значения подобраны для контраста на `surface.base` и гармонии с общей цветовой схемой.

| ANSI Color | Normal | Bright |
|---|---|---|
| Black | `#1A1B1E` | `#55565C` |
| Red | `#F85149` | `#FF7B72` |
| Green | `#3FB950` | `#56D364` |
| Yellow | `#E2B93D` | `#E3C04B` |
| Blue | `#4A9EFF` | `#79C0FF` |
| Magenta | `#BC8CFF` | `#D2A8FF` |
| Cyan | `#39C5CF` | `#56D4DD` |
| White | `#D4D4D8` | `#FFFFFF` |

| Special | Color |
|---|---|
| Foreground | `#D4D4D8` |
| Background | `#1A1B1E` |
| Cursor | `#D4D4D8` |
| Selection | `#264F78` |

---

## 9. Spacing System

### Базовая единица

Вся система spacing построена на **шаге 4pt**. Допустимые значения: 0, 2, 4, 8, 12, 16, 20, 24, 32, 40, 48.

Значение 2pt используется только для микро-зазоров (между табами, между иконкой и текстом внутри кнопки).

### Токены spacing

| Токен | Значение | Где |
|---|---|---|
| `space.xxs` | 2pt | Зазор между табами, micro-gaps |
| `space.xs` | 4pt | Padding внутри маленьких элементов, gap иконка-текст в компактных элементах |
| `space.sm` | 8pt | Стандартный gap между элементами, padding секций |
| `space.md` | 12pt | Horizontal padding sidebar, padding кнопок |
| `space.lg` | 16pt | Indent уровня в дереве, крупные gap-ы |
| `space.xl` | 20pt | Vertical padding между крупными блоками |
| `space.xxl` | 24pt | Отступы от краев окна (не используется в MVP) |

### Border Radius

| Токен | Значение | Где |
|---|---|---|
| `radius.sm` | 4pt | Hover/selection на строках sidebar |
| `radius.md` | 6pt | Табы, кнопки, input fields, dropdown |
| `radius.lg` | 8pt | Modals, popovers |
| `radius.full` | 50% | Индикатор активности (круг) |

---

## 10. Компонент: Context Menu

Используется стандартный `NSMenu` для нативного ощущения. Стилизация минимальная.

### Контекстное меню файла (правый клик в sidebar)

```
┌─────────────────────────────────┐
│  Open in Terminal               │
│  Open in Editor                 │
│  ─────────────────────────────  │
│  Copy Path                      │
│  Copy Relative Path             │
│  ─────────────────────────────  │
│  Reveal in Finder               │
│  ─────────────────────────────  │
│  Git: Stage File                │
│  Git: Discard Changes           │  <- danger, цвет text #F85149
└─────────────────────────────────┘
```

---

## 11. Компонент: Branch Picker Dropdown

Активируется кликом на имя ветки или chevron в git-секции sidebar.

### Layout

```
┌──────────────────────────────────┐
│  [search] Filter branches...     │ <- 32pt, sticky top
├──────────────────────────────────┤
│  Local Branches                  │ <- section header
│    main                     [v]  │ <- current branch checkmark
│    feature/auth                  │
│    fix/memory-leak               │
├──────────────────────────────────┤
│  Remote Branches                 │ <- section header
│    origin/main                   │
│    origin/develop                │
└──────────────────────────────────┘
```

| Параметр | Значение |
|---|---|
| Ширина | 280pt |
| Максимальная высота | 320pt (scroll) |
| Фон | `surface.overlay` (`#2A2B2F`) |
| Рамка | 1pt `border.default`, shadow: 0 4pt 12pt rgba(0,0,0,0.3) |
| Border radius | `radius.lg` (8pt) |
| Строка ветки | 28pt высота |
| Padding строки | 12pt horizontal |
| Текущая ветка | checkmark (`checkmark` SF Symbol) справа, цвет `accent.primary` |
| Search input | 32pt высота, `surface.input` фон |

---

## 12. Motion Design

### Принципы анимации

1. **Функциональность > декоративность** -- анимация объясняет пространственные изменения, не привлекает внимание ради красоты
2. **Быстро** -- разработчики не ждут; максимальная длительность любой анимации 300ms
3. **Interruptible** -- все анимации могут быть прерваны новым действием

### Длительности

| Действие | Длительность | Easing | Примечание |
|---|---|---|---|
| Hover state | 80ms | ease-out | Фон, цвет текста |
| Tab switch | 0ms | -- | Мгновенное, без перехода |
| Sidebar toggle (Cmd+B) | 200ms | ease-out | Ширина sidebar: 240 -> 0 или 0 -> 240 |
| Sidebar resize (drag) | 0ms | -- | Realtime tracking, без интерполяции |
| Split divider drag | 0ms | -- | Realtime tracking |
| Disclosure triangle (folder open/close) | 150ms | ease-out | Rotation 0 -> 90 deg |
| Branch picker open | 120ms | ease-out | Opacity 0->1, scale 0.96->1.0 |
| Branch picker close | 80ms | ease-in | Opacity 1->0 |
| Indicator pulse (running) | 600ms | ease-in-out | Opacity loop 0.4->1.0, autoreverses |
| Indicator error glow | 2000ms visible, 300ms fade | ease-out | Shadow appears then fades |
| Git status update | 150ms | ease-out | Цвет литеры M/A/D fade-in |
| File list add/remove | 200ms | ease-out | Height collapse/expand |
| Tooltip appear | 400ms delay, 120ms fade-in | ease-out | macOS стандарт для тултипов |

### Reduced Motion

При включенном `NSWorkspace.shared.accessibilityDisplayShouldReduceMotion`:
- Все transition длительности = 0ms (мгновенные)
- Indicator pulse заменяется на статичную точку (без анимации)
- Sidebar toggle -- мгновенное появление/скрытие
- Branch picker -- мгновенное появление без scale

---

## 13. Accessibility

### Контраст

Все текстовые элементы соответствуют WCAG 2.1 AA:

| Элемент | Foreground | Background | Contrast Ratio | Level |
|---|---|---|---|---|
| Primary text on base | `#D4D4D8` on `#1A1B1E` | -- | 10.2:1 | AAA |
| Secondary text on base | `#8B8B93` on `#1A1B1E` | -- | 4.6:1 | AA |
| Muted text on base | `#55565C` on `#1A1B1E` | -- | 2.5:1 | Decorative only |
| Primary text on raised | `#D4D4D8` on `#212225` | -- | 9.1:1 | AAA |
| Button text on primary | `#FFFFFF` on `#4A9EFF` | -- | 3.4:1 | AA Large Text |
| Git modified on raised | `#E2B93D` on `#212225` | -- | 5.8:1 | AA |
| Git added on raised | `#3FB950` on `#212225` | -- | 5.2:1 | AA |
| Git deleted on raised | `#F85149` on `#212225` | -- | 4.7:1 | AA |

### Keyboard Navigation

- Все интерактивные элементы доступны через Tab
- Focus ring: 2pt, `border.focus` (`#4A9EFF`), offset 2pt
- Sidebar tree: Arrow keys для навигации, Enter для открытия, Space для stage/unstage
- Tab bar: Cmd+1-9, Ctrl+Tab/Ctrl+Shift+Tab
- Git buttons: Tab order: Stage All -> Commit message -> Commit -> Push -> Pull

### VoiceOver

- Tab: "Project {name}, {branch}, {status indicator description}"
- File row: "{filename}, {git status name}, {file type}"
- Git indicator: "Terminal idle" / "Process running" / "Process failed with error"
- Кнопки: стандартные accessibility labels

### Dynamic Type

- Терминальный шрифт не следует Dynamic Type (пользователь управляет через Cmd+/-)
- UI-шрифты: зарезервировано для v2 (в MVP фиксированные размеры)

---

## 14. Responsive Behavior

### Минимальный размер окна

| Параметр | Значение |
|---|---|
| Минимальная ширина | 640pt |
| Минимальная высота | 400pt |
| Рекомендуемый размер | 1280 x 800pt |

### Адаптация при уменьшении ширины

1. **> 900pt** -- полный layout: sidebar 240pt + terminal
2. **640-900pt** -- sidebar сужается до минимума 180pt, tab truncation более агрессивный
3. **< 640pt** -- не поддерживается (minWidth)

### Адаптация при уменьшении высоты

1. **> 600pt** -- полный layout
2. **400-600pt** -- git кнопки сжимаются в одну строку (иконки без текста), commit input уменьшается до 40pt min height
3. **< 400pt** -- не поддерживается (minHeight)

---

## 15. Реализация Design Tokens в Swift

### Рекомендуемая структура

```swift
// MARK: - Color Tokens

enum DSColor {
    // Surfaces
    static let surfaceBase = Color(hex: "#1A1B1E")
    static let surfaceRaised = Color(hex: "#212225")
    static let surfaceOverlay = Color(hex: "#2A2B2F")
    static let surfaceTabBar = Color(hex: "#17181B")
    static let surfaceTabActive = Color(hex: "#1A1B1E")
    static let surfaceTabInactive = Color(hex: "#17181B")
    static let surfaceTabHover = Color(hex: "#1F2023")
    static let surfaceInput = Color(hex: "#16171A")
    static let surfaceSelection = Color(hex: "#264F78")

    // Text
    static let textPrimary = Color(hex: "#D4D4D8")
    static let textSecondary = Color(hex: "#8B8B93")
    static let textMuted = Color(hex: "#55565C")
    static let textInverse = Color(hex: "#1A1B1E")

    // Borders
    static let borderDefault = Color(hex: "#2E2F33")
    static let borderSubtle = Color(hex: "#252629")
    static let borderFocus = Color(hex: "#4A9EFF")

    // Accent
    static let accentPrimary = Color(hex: "#4A9EFF")
    static let accentPrimaryHover = Color(hex: "#5BABFF")

    // Git
    static let gitModified = Color(hex: "#E2B93D")
    static let gitAdded = Color(hex: "#3FB950")
    static let gitDeleted = Color(hex: "#F85149")
    static let gitUntracked = Color(hex: "#8B8B93")
    static let gitConflicted = Color(hex: "#F09000")
    static let gitRenamed = Color(hex: "#58A6FF")

    // Indicators
    static let indicatorIdle = Color(hex: "#3FB950")
    static let indicatorRunning = Color(hex: "#E2B93D")
    static let indicatorError = Color(hex: "#F85149")

    // Buttons
    static let buttonPrimaryBg = Color(hex: "#4A9EFF")
    static let buttonPrimaryText = Color.white
    static let buttonPrimaryHoverBg = Color(hex: "#5BABFF")
    static let buttonSecondaryBg = Color(hex: "#2A2B2F")
    static let buttonSecondaryText = Color(hex: "#D4D4D8")
    static let buttonSecondaryHoverBg = Color(hex: "#333438")
    static let buttonDangerBg = Color(hex: "#3D1214")
    static let buttonDangerText = Color(hex: "#F85149")
}

// MARK: - Spacing Tokens

enum DSSpacing {
    static let xxs: CGFloat = 2
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 20
    static let xxl: CGFloat = 24
}

// MARK: - Radius Tokens

enum DSRadius {
    static let sm: CGFloat = 4
    static let md: CGFloat = 6
    static let lg: CGFloat = 8
}

// MARK: - Typography

enum DSFont {
    static let tabTitle = Font.system(size: 12, weight: .medium)
    static let tabBranch = Font.system(size: 10)
    static let sidebarSection = Font.system(size: 11, weight: .semibold)
    static let sidebarItem = Font.system(size: 13)
    static let sidebarItemSmall = Font.system(size: 11)
    static let gitStatus = Font.custom("SF Mono", size: 11).weight(.medium)
    static let gitBranch = Font.system(size: 13, weight: .medium)
    static let gitAheadBehind = Font.system(size: 11)
    static let buttonLabel = Font.system(size: 12, weight: .medium)
    static let commitInput = Font.system(size: 13)
    static let tooltip = Font.system(size: 11)

    static func terminal(size: CGFloat = 13) -> Font {
        Font.custom("JetBrains Mono", size: size)
    }
}

// MARK: - Layout Constants

enum DSLayout {
    // Tab Bar
    static let tabBarHeight: CGFloat = 36
    static let tabHeight: CGFloat = 28
    static let tabMinWidth: CGFloat = 120
    static let tabMaxWidth: CGFloat = 200
    static let tabHorizontalPadding: CGFloat = 12
    static let tabGap: CGFloat = 2
    static let tabCloseSize: CGFloat = 16

    // Sidebar
    static let sidebarDefaultWidth: CGFloat = 240
    static let sidebarMinWidth: CGFloat = 180
    static let sidebarMaxWidth: CGFloat = 400
    static let sidebarHorizontalPadding: CGFloat = 12

    // File Tree
    static let treeRowHeight: CGFloat = 28
    static let treeIndent: CGFloat = 16
    static let treeBaseIndent: CGFloat = 4

    // Git Section
    static let gitSectionHeaderHeight: CGFloat = 28
    static let gitFileRowHeight: CGFloat = 28
    static let gitButtonHeight: CGFloat = 28
    static let commitInputMinHeight: CGFloat = 60
    static let commitInputMaxHeight: CGFloat = 120

    // Terminal
    static let terminalPadding = EdgeInsets(top: 4, leading: 8, bottom: 4, trailing: 8)
    static let splitDividerHitArea: CGFloat = 9
    static let splitMinPanelSize: CGFloat = 120

    // Indicator
    static let indicatorSize: CGFloat = 6

    // Window
    static let windowMinWidth: CGFloat = 640
    static let windowMinHeight: CGFloat = 400
}
```

### Color extension для hex

```swift
extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 6:
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8:
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (255, 0, 0, 0)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}
```

---

## 16. ANSI Terminal Palette (SwiftTerm Integration)

Массив цветов для передачи в SwiftTerm `TerminalView.installColors()`:

```swift
enum DSTerminalColors {
    static let palette: [Color] = [
        // Normal (0-7)
        Color(hex: "#1A1B1E"),  // Black
        Color(hex: "#F85149"),  // Red
        Color(hex: "#3FB950"),  // Green
        Color(hex: "#E2B93D"),  // Yellow
        Color(hex: "#4A9EFF"),  // Blue
        Color(hex: "#BC8CFF"),  // Magenta
        Color(hex: "#39C5CF"),  // Cyan
        Color(hex: "#D4D4D8"),  // White

        // Bright (8-15)
        Color(hex: "#55565C"),  // Bright Black
        Color(hex: "#FF7B72"),  // Bright Red
        Color(hex: "#56D364"),  // Bright Green
        Color(hex: "#E3C04B"),  // Bright Yellow
        Color(hex: "#79C0FF"),  // Bright Blue
        Color(hex: "#D2A8FF"),  // Bright Magenta
        Color(hex: "#56D4DD"),  // Bright Cyan
        Color(hex: "#FFFFFF"),  // Bright White
    ]

    static let foreground = Color(hex: "#D4D4D8")
    static let background = Color(hex: "#1A1B1E")
    static let cursor = Color(hex: "#D4D4D8")
    static let selection = Color(hex: "#264F78")
}
```

---

## Changelog

| Версия | Дата | Изменения |
|---|---|---|
| 1.0 | 2026-03-24 | Initial design system specification |
