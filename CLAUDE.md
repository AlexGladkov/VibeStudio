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

## Version Badge — Titlebar Leading (STRICT)

Индикатор запущенного билда `vX.Y.Z (build)` — **titlebar, LEADING, сразу справа от traffic lights**.
- Компонент: `AppVersionBadge` (ghost 9pt, `Bundle.appVersionDisplay`)
- **Regular**: первый элемент HStack в `RegularToolbarView` (перед `Spacer`)
- **CodeSpeak**: `.overlay(alignment: .leading)` на Box1 в `CodeSpeakToolbarView` — Box1 ширину НЕ меняет, breadcrumb-центрирование сохранено
- НЕ в сайдбаре, НЕ в trailing-контролах. Один источник — `AppVersionBadge`.
