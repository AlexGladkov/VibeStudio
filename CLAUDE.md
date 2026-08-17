# VibeStudio Project Invariants

## CodeSpeak Mode — Layout (STRICT)

### Breadcrumb `Projects › projectName › spec.cs.md`
- **Место: ВЕРХ ЦЕНТРАЛЬНОЙ ПАНЕЛИ** (в SwiftUI view hierarchy, `editorColumn()` в `CodeSpeakModeView`)
- **Выравнивание: по ЛЕВОМУ КРАЮ** центральной колонки
- **НЕ в titlebar** — ни слева, ни по центру, ни справа
- **НЕ в левой панели** (spec list sidebar)
- **НЕ в правой панели** (build output)
- Реализовано: `editorBreadcrumb(spec:)` в `CodeSpeakModeView`, первый элемент `editorColumn()` при `selectedSpec != nil`

### Controls `🔍 ▼ Command ▶ ⚙️`
- **Место: ПРАВЫЙ КРАЙ titlebar** (ToolbarView в WindowToolbarRemover, trailingAnchor)
- `ToolbarView.body` CodeSpeak ветка: `Spacer + codeSpeakStatsBadge + codeSpeakRunBar + settingsButton`
- Без breadcrumb в ToolbarView

## Regular Mode — Toolbar Layout

Controls (`configPicker + playStopButton + ...`) позиционируются через `leadingAnchor = trafficLightsEnd` + `Spacer`-в-HStack → controls справа. Без изменений.

## Version Indicator

Версия запущенного билда `vX.Y.Z (build)` показывается в **Settings ▸ Updates**
(`UpdateSettingsPane`, строка «Current version»). Источник — `Bundle.appVersionDisplay`.
НЕ в titlebar (наезжает на sidebar-разделитель при узком сайдбаре), НЕ в сайдбаре.

## Agents

Проект чисто Swift/macOS. Фаза Resolve в Workflow ОБЯЗАНА брать имена агентов РОВНО отсюда
(не выдумывать `swift-architect`/`security-swift` и т.п. — их не существует).

### Консилиум
| Role        | Agent                          |
|-------------|--------------------------------|
| architect   | voltagent-lang:swift-expert    |
| developer   | voltagent-lang:swift-expert    |
| diagnostics | voltagent-lang:swift-expert    |
| test        | voltagent-lang:swift-expert    |
| api         | voltagent-core-dev:api-designer |
| ui          | voltagent-core-dev:ui-designer |
| frontend    | voltagent-lang:swift-expert    |
| security    | voltagent-infra:security-engineer |
| devops      | devops-orchestrator            |

### Executing
| Agent                       | Scope         |
|-----------------------------|---------------|
| voltagent-lang:swift-expert | **/*.swift    |
