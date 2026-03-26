# VibeStudio — Спецификация

**Дата:** 2026-03-24
**Платформа:** macOS 14+ (Sonoma и выше)
**Язык:** Swift 5.10
**Тип:** Desktop-приложение

---

## Концепция

Десктопное macOS-приложение для разработчиков, работающих одновременно с несколькими проектами в терминале. Вдохновение: JetBrains (боковая панель, git, проект-менеджмент), Cursor (современный минималистичный UI), Neovim (скорость, keyboard-first, не мешает работать).

**Ключевая идея:** терминал — это центр, а не дополнение. Всё остальное (файлы, git) — контекст вокруг него.

---

## Архитектура приложения

### UI-фреймворк
- **SwiftUI** для общего лейаута (NavigationSplitView, TabView, sidebar)
- **AppKit (NSView / NSViewRepresentable)** для терминальных панелей, где нужен низкоуровневый контроль
- Минимальная версия macOS: 14.0 (Sonoma)

### Терминальный движок
- **SwiftTerm** — open-source PTY-эмулятор (MIT, используется в Warp, SSH Files)
- `LocalProcessTerminalView` запускает `/bin/zsh` (или shell из $SHELL)
- Поддержка: xterm-256color, лигатуры, true color, scrollback, mouse reporting
- Каждый терминал — отдельный PTY-процесс

### Git-backend
- **git CLI через `Process`** — запуск git как subprocess
- Использует пользовательский `.gitconfig` и SSH-ключи автоматически
- Нет зависимостей от libgit2

### Хранение состояния
- **UserDefaults + JSON** для списка проектов, настроек, последних открытых
- Сессия (открытые табы) восстанавливается при перезапуске приложения

---

## Структура окна

```
┌─────────────────────────────────────────────────────────────┐
│ [proj-a] [proj-b ●] [proj-c] [+]          ← Tab Bar        │
├──────────┬──────────────────────────────────────────────────┤
│          │                                                    │
│ Sidebar  │  Terminal Area                                     │
│          │                                                    │
│ 📁 Files │  $ git status                                      │
│ ├─ src/  │  On branch main                                    │
│ ├─ M pkg │  ...                                               │
│ └─ A new │                                                    │
│          │  $  ▌                                              │
│ ── git ──│                                                    │
│ main ↑2  │                                                    │
│ M file.k │                                                    │
│ A new.kt │                                                    │
└──────────┴──────────────────────────────────────────────────┘
```

---

## Компоненты

### 1. Tab Bar (верхняя панель)

- Каждый таб = один проект
- На табе отображается: название проекта, текущая git-ветка (`main`), индикатор активности
- **Индикатор внимания** (когда таб не активен):
  - Цветная точка (●) — есть новый вывод
  - Пульсация/анимация пока процесс активен
  - Цвет точки: зелёный (успех/idle), жёлтый (работает), красный (ошибка/ненулевой exit code)
- Добавить новый проект: кнопка [+]
- Drag-to-reorder табов

### 2. Sidebar (левая панель)

Фиксированная ширина ~240px, можно скрыть (Cmd+B как в VS Code).

**Секция "Files":**
- Дерево файлов текущего проекта
- Git-статус рядом с каждым файлом: `M` (modified), `A` (added), `D` (deleted), `?` (untracked) — цветные как в JetBrains
- Двойной клик по файлу → `cd <dir>` + открывает файл в $EDITOR через терминал
- Правый клик → контекстное меню: Copy Path, Reveal in Finder, Open in Terminal

**Секция "Git":**
- Текущая ветка + кнопка переключения (dropdown со списком веток)
- Статус: `↑2 ↓0` (commits ahead/behind origin)
- Список изменённых файлов (staged / unstaged)
- Кнопки: Stage All, Commit (с полем для сообщения), Push
- Кнопка Pull

### 3. Terminal Area (центральная область)

- Одна терминальная панель на проект (full-size)
- **Split-панели внутри проекта** — горизонтальный или вертикальный сплит (Cmd+D / Cmd+Shift+D как в iTerm2)
- Каждая панель — независимый PTY-процесс
- При открытии нового проекта — автоматически `cd` в папку проекта
- **Scrollback buffer** — 10 000 строк по умолчанию
- Поддержка шрифта с лигатурами (JetBrains Mono, Fira Code)
- Размер шрифта меняется Cmd+/Cmd-

### 4. Управление проектами

- Добавить проект: "Open Folder" (File menu или кнопка [+] в табах)
- Список недавних проектов в меню File → Recent
- У каждого проекта опциональный цвет — подсвечивает таб
- Проекты хранятся в `~/Library/Application Support/VibeStudio/projects.json`

---

## Модели данных

```swift
struct Project: Codable, Identifiable {
    let id: UUID
    var name: String
    var path: URL
    var color: String?      // hex color для таба
    var lastOpened: Date
    var shellPath: String   // default: /bin/zsh или $SHELL
}

struct TerminalSession: Identifiable {
    let id: UUID
    let projectId: UUID
    var title: String       // можно переименовать
    var hasActivity: Bool   // для индикатора на табе
    var exitCode: Int?      // nil = процесс жив
}

struct GitStatus {
    var branch: String
    var aheadCount: Int
    var behindCount: Int
    var stagedFiles: [GitFile]
    var unstagedFiles: [GitFile]
}

struct GitFile {
    var path: String
    var status: GitFileStatus  // .modified, .added, .deleted, .untracked
}
```

---

## Поведение и UX

### Keyboard-first
- `Cmd+T` — новый проект/таб
- `Cmd+W` — закрыть таб
- `Cmd+1..9` — переключение между табами
- `Cmd+D` — split горизонтально
- `Cmd+Shift+D` — split вертикально
- `Cmd+B` — toggle sidebar
- `Cmd+K` — очистить терминал (clear)
- `Ctrl+Tab` — следующий таб

### Восстановление сессии
- При перезапуске приложения восстанавливаются все открытые проекты
- Терминальные сессии рестартуют (PTY нельзя сериализовать), но история scrollback сохраняется в файл

### Git polling
- Git-статус обновляется каждые 3 секунды для активного проекта
- Используется `FSEventStream` для watch директории — обновление по изменению файлов

---

## Технический стек

| Компонент | Решение |
|-----------|---------|
| UI | SwiftUI + AppKit (NSViewRepresentable) |
| Терминал | SwiftTerm (LocalProcessTerminalView) |
| PTY | POSIX PTY через SwiftTerm |
| Git | git CLI через Process/Foundation |
| File watch | FSEventStream (CoreServices) |
| Хранение | UserDefaults + JSON файл |
| Dependency manager | Swift Package Manager |
| Min OS | macOS 14.0 |

### Swift Package зависимости
- `github.com/migueldeicaza/SwiftTerm` — терминальный эмулятор
- Всё остальное — стандартные Apple фреймворки

---

## Scope MVP

### Входит в MVP
- [x] Табы с проектами (открыть папку → создать таб)
- [x] Встроенный терминал (SwiftTerm, zsh, PTY)
- [x] Индикатор активности на табе
- [x] Сайдбар: дерево файлов с git-статусом файлов
- [x] Сайдбар: git-ветка + список изменений
- [x] Базовые git-операции: stage, commit, push, pull
- [x] Split-панели (горизонтальный)
- [x] Keyboard shortcuts
- [x] Восстановление сессии при рестарте

### За пределами MVP (v2)
- [ ] Вертикальный split
- [ ] Граф коммитов (git log visual)
- [ ] Темы (светлая/тёмная кастомные)
- [ ] SSH-подключения к удалённым серверам
- [ ] Плагины / extensions
- [ ] AI-интеграция (как в Cursor)
- [ ] Поиск по файлам (Cmd+P fuzzy finder)

---

## Точки интеграции и риски

### Риски
1. **SwiftTerm + SwiftUI** — `LocalProcessTerminalView` это `NSView`, нужна обёртка `NSViewRepresentable`. Потенциальные проблемы с фокусом и responder chain.
2. **Sandbox** — если публиковать в Mac App Store, нужен entitlement для `com.apple.security.cs.allow-unsigned-executable-memory` и `com.apple.security.automation.apple-events`. Вероятно, лучше **не использовать sandbox** (прямая дистрибуция через DMG).
3. **FSEventStream polling** — при большом количестве файлов (node_modules) возможна нагрузка. Нужно игнорировать `.gitignore`-паттерны.
4. **PTY размеры** — при resize окна нужно передавать новый TIOCSWINSZ в PTY иначе vim/htop ломаются.

### Архитектурные решения
- **Прямая дистрибуция (DMG)**, не Mac App Store — нет sandbox-ограничений, полный доступ к shell и файловой системе
- **No sandbox** — критично для запуска произвольных процессов в терминале
