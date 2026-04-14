# CodeSpeak Run Commands

## Контекст

VibeStudio — macOS 14+ десктопное приложение (SwiftUI + AppKit).
CodeSpeak CLI v0.3.9. Текущая интеграция: только `codespeak build`.

## Цель

Добавить в режим CodeSpeak поддержку всех ключевых команд CLI помимо `build`:
`run`, `impl`, `test`, `task`, `change`.

---

## Функциональные требования

### 1. Команды

| Команда  | CLI                                    | Параметры в UI          |
|----------|----------------------------------------|-------------------------|
| `build`  | `codespeak build [spec]`               | —                       |
| `run`    | `codespeak run [spec]`                 | —                       |
| `impl`   | `codespeak impl [spec]`                | —                       |
| `test`   | `codespeak test [spec]`                | —                       |
| `task`   | `codespeak task <task_name> [--spec]`  | Поле: task name         |
| `change` | `codespeak change [spec] -m MESSAGE`   | Поле: message           |

Все команды принимают необязательный путь к спеку: если в редакторе открыт спек — передаётся его путь, иначе команда запускается без аргументов (все спеки).

### 2. Stop-функция

- Пока любая команда выполняется: кнопка ▶ трансформируется в ■ Stop.
- Нажатие Stop → отправить SIGTERM процессу → `process.terminate()` → `process.waitUntilExit()`.
- Dropdown выбора команды заблокирован (disabled) пока процесс активен.
- После остановки: `exitCode = -15` (SIGTERM), `isRunning = false`, dropdown разблокируется.

### 3. Breadcrumb в центральной колонке

Шапка центральной колонки (редактора) должна показывать:
```
Projects › <ProjectName> › <spec-name.cs.md>
```
- Если проект не выбран: только `Projects`.
- Если спек не выбран: `Projects › <ProjectName>`.
- Текст `Projects` — кликабельный (возвращает к списку проектов через `navigationCoordinator`).

### 4. Поля ввода для `task` и `change`

- При выборе `task` в dropdown — под dropdown появляется текстовое поле `Task name` (placeholder: "deploy, seed-db, …"). Поле пустое → кнопка ▶ disabled.
- При выборе `change` в dropdown — текстовое поле `Change message` (placeholder: "Add input validation…"). Поле пустое → кнопка ▶ disabled.
- Поля пропадают при выборе других команд.

---

## Архитектурные изменения

### Новые файлы

```
Sources/Features/Specs/Models/CodeSpeakCommand.swift
```

```swift
/// All codespeak CLI commands exposed in VibeStudio UI.
enum CodeSpeakCommand: String, CaseIterable, Identifiable {
    case build, run, impl, test, task, change
    var id: String { rawValue }
    var displayName: String { rawValue }
    var cliArgs: [String] { [rawValue] } // overridden per-command as needed
    var requiresTextField: Bool { self == .task || self == .change }
    var textFieldLabel: String {
        switch self {
        case .task: "Task name"
        case .change: "Change message"
        default: ""
        }
    }
    var textFieldPlaceholder: String {
        switch self {
        case .task: "deploy, seed-db, …"
        case .change: "Add input validation…"
        default: ""
        }
    }
}
```

### Изменения в `CodeSpeakProcessRunner`

- Добавить хранение `private var currentProcess: Process?` (nonisolated(unsafe) или через actor isolation).
- Метод `func stop()`: если `currentProcess != nil` — вызвать `terminate()` + `waitUntilExit()`.
- При запуске нового процесса — сохранить ссылку в `currentProcess`.
- После выхода — очистить `currentProcess = nil`.

### Изменения в `SpecBuildPanelViewModel`

Новые поля:
```swift
var selectedCommand: CodeSpeakCommand = .build
var taskName: String = ""
var changeMessage: String = ""
var canRun: Bool {
    !isRunning &&
    !(selectedCommand == .task && taskName.isEmpty) &&
    !(selectedCommand == .change && changeMessage.isEmpty)
}
```

Метод `runBuild(at:)` переименовать в `run(at:)` (backward compat — сохранить alises или обновить call sites).

Новый метод `stop()`:
```swift
func stop() {
    Task { await processRunner.stop() }
}
```

Формирование аргументов:
```swift
private func buildArgs(specPath: URL?) -> [String] {
    var args: [String] = [selectedCommand.rawValue]
    switch selectedCommand {
    case .task:
        args = ["task", taskName]
        if let spec = specPath { args += ["--spec", spec.path] }
    case .change:
        if let spec = specPath { args.append(spec.path) }
        args += ["-m", changeMessage]
    default:
        if let spec = specPath { args.append(spec.path) }
    }
    return args
}
```

### Изменения в `CodeSpeakModeView`

**Центральная колонка — шапка** (`specEditorHeader`):
- Заменить `Text(vm.selectedSpec?.name ?? "Editor")` на breadcrumb:
  ```
  Button("Projects") { navigationCoordinator.goToProjects() }   // пока без навигации — просто текст
  › ProjectName
  › spec.cs.md
  ```
- Стиль: `DSFont.sidebarItemSmall`, цвет `DSColor.textMuted` для неактивных сегментов.

**Правая колонка — шапка** (`buildHeader`):
- Убрать Text "CodeSpeak Build".
- Добавить Menu (dropdown) для выбора команды:
  ```swift
  Menu {
      ForEach(CodeSpeakCommand.allCases) { cmd in
          Button(cmd.displayName) { vm.buildVM.selectedCommand = cmd }
      }
  } label: {
      HStack(spacing: 2) {
          Text(vm.buildVM.selectedCommand.displayName)
              .font(DSFont.sidebarSection)
          Image(systemName: "chevron.down")
              .font(.system(size: 9))
      }
  }
  .disabled(vm.buildVM.isRunning)
  ```
- Кнопка ▶/■:
  ```swift
  Button {
      if vm.buildVM.isRunning { vm.buildVM.stop() }
      else { Task { await vm.buildVM.run(at: project.path, specPath: vm.selectedSpec?.url) } }
  } label: {
      Image(systemName: vm.buildVM.isRunning ? "stop.fill" : "play.fill")
  }
  .foregroundStyle(vm.buildVM.isRunning ? DSColor.gitDeleted : DSColor.actionRun)
  .disabled(!vm.buildVM.canRun && !vm.buildVM.isRunning)
  ```

**Правая колонка — поля ввода** (между шапкой и output):
- Если `selectedCommand.requiresTextField`:
  ```swift
  HStack {
      Text(selectedCommand.textFieldLabel)
          .font(DSFont.sidebarItemSmall)
          .foregroundStyle(DSColor.textSecondary)
      TextField(selectedCommand.textFieldPlaceholder, text: $vm.buildVM.taskName / changeMessage)
          .textFieldStyle(.plain)
          .font(.system(size: 12))
  }
  .padding(.horizontal, DSSpacing.md)
  .padding(.vertical, DSSpacing.xs)
  ```

### Изменения в `SpecBuildPanelView`

Те же изменения в заголовке (dropdown + stop), те же поля ввода.

---

## UX детали

- **Очистка вывода**: при каждом новом запуске `outputLines = []`, `exitCode = nil`.
- **Auto-scroll**: поведение без изменений — скролл к последней строке при добавлении.
- **Статус-бейдж**: PASS/FAIL отображается только для `build` и `run` (когда можно парсить stats). Для остальных команд — только exit code.
- **Stats parsing**: только для `build` команды. Для остальных `stats = nil`.
- **Keyboard shortcut**: Cmd+Return по-прежнему запускает текущую выбранную команду.

---

## Затронутые файлы

| Файл | Тип изменения |
|------|---------------|
| `Sources/Features/Specs/Models/CodeSpeakCommand.swift` | Новый |
| `Sources/Services/CodeSpeak/CodeSpeakProcessRunner.swift` | Stop method + process reference |
| `Sources/Features/Specs/ViewModels/SpecBuildPanelViewModel.swift` | Multi-command + stop + canRun |
| `Sources/Features/Specs/Views/CodeSpeakModeView.swift` | Breadcrumb + dropdown + stop button + text fields |
| `Sources/Features/Specs/Views/SpecBuildPanelView.swift` | Dropdown + stop button + text fields |

---

## Что НЕ входит в скоуп

- `init`, `takeover`, `whitelist`, `coverage`, `update-managed-files` — не выносятся в UI.
- `change --new` (создать файл change-request) — не реализовывать.
- Навигация по клику на "Projects" в breadcrumb — только отображение, без перехода.
- `--no-interactive`, `--skip-tests` флаги — не выносить в UI (команды запускаются с `--no-interactive` всегда, т.к. PTY недоступен в ProcessRunner).
