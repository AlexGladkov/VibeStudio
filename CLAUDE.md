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
