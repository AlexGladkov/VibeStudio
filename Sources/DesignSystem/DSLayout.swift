// MARK: - DSLayout
// Layout constants for VibeStudio.
// macOS 14+, Swift 5.10

import SwiftUI

/// Fixed layout dimensions for all components.
enum DSLayout {

    // MARK: Toolbar

    /// Quick-action toolbar height above tab bar.
    static let toolbarHeight: CGFloat = 26

    // MARK: Tab Bar

    /// Total tab bar height.
    static let tabBarHeight: CGFloat = 36
    /// Individual tab height.
    static let tabHeight: CGFloat = 28
    /// Minimum tab width.
    static let tabMinWidth: CGFloat = 120
    /// Maximum tab width.
    static let tabMaxWidth: CGFloat = 200
    /// Horizontal padding inside a tab.
    static let tabHorizontalPadding: CGFloat = 12
    /// Gap between tabs.
    static let tabGap: CGFloat = 2
    /// Close button size on tab.
    static let tabCloseSize: CGFloat = 16
    /// Close button icon size.
    static let tabCloseIconSize: CGFloat = 9
    /// Add button size.
    static let tabAddButtonSize: CGFloat = 28

    // MARK: Sidebar

    /// Default sidebar width.
    static let sidebarDefaultWidth: CGFloat = 240
    /// Minimum sidebar width.
    static let sidebarMinWidth: CGFloat = 180
    /// Maximum sidebar width.
    static let sidebarMaxWidth: CGFloat = 400
    /// Horizontal padding inside sidebar.
    static let sidebarHorizontalPadding: CGFloat = 12

    // MARK: File Tree

    /// Row height in file tree.
    static let treeRowHeight: CGFloat = 28
    /// Indent per nesting level.
    static let treeIndent: CGFloat = 16
    /// Base indent for root level.
    static let treeBaseIndent: CGFloat = 4

    // MARK: Git Section

    /// Section header height (GIT, FILES).
    static let gitSectionHeaderHeight: CGFloat = 28
    /// File row height in git panel.
    static let gitFileRowHeight: CGFloat = 28
    /// Button height in git panel.
    static let gitButtonHeight: CGFloat = 28
    /// Minimum commit message input height.
    static let commitInputMinHeight: CGFloat = 60
    /// Maximum commit message input height.
    static let commitInputMaxHeight: CGFloat = 120

    // MARK: Terminal

    /// Padding around terminal content.
    static let terminalPadding = EdgeInsets(top: 4, leading: 8, bottom: 4, trailing: 8)
    /// Split divider total hit area (1pt line + 4pt each side).
    static let splitDividerHitArea: CGFloat = 9
    /// Minimum panel size when splitting.
    static let splitMinPanelSize: CGFloat = 120

    // MARK: Indicator

    /// Activity indicator dot diameter.
    static let indicatorSize: CGFloat = 6

    // MARK: Changes Panel

    /// Default width for the right-side git changes panel.
    static let changesPanelDefaultWidth: CGFloat = 280
    /// Minimum width for the changes panel.
    static let changesPanelMinWidth: CGFloat = 220
    /// Maximum width for the changes panel.
    static let changesPanelMaxWidth: CGFloat = 450
    /// Row height for a file entry in the changes list.
    static let changesFileRowHeight: CGFloat = 26
    /// Line height for diff view rows.
    static let diffLineHeight: CGFloat = 18

    // MARK: Spec Panel

    /// Default width for the right-side CodeSpeak spec build panel.
    static let specPanelDefaultWidth: CGFloat = 320
    /// Minimum width for the spec panel.
    static let specPanelMinWidth: CGFloat = 240
    /// Maximum width for the spec panel.
    static let specPanelMaxWidth: CGFloat = 500

    // MARK: Traceability Panel

    /// Default width for the traceability panel.
    static let traceabilityPanelDefaultWidth: CGFloat = 280

    // MARK: CodeSpeak

    static let codeSpeakAccentLineHeight: CGFloat = 2

    // MARK: Code Editor

    /// Width of the line number gutter in the syntax-highlighted editor.
    static let lineNumberGutterWidth: CGFloat = 44

    // MARK: Titlebar

    /// Approximate end of the traffic lights (close/minimize/zoom) cluster.
    ///
    /// Used in `WindowToolbarRemover` and `ToolbarView` to align content
    /// just after the standard macOS window buttons.
    /// Verified on macOS 14 Sonoma + macOS 15 Sequoia.
    static let trafficLightsEndFallback: CGFloat = 84

    // MARK: Window

    /// Minimum window width.
    static let windowMinWidth: CGFloat = 640
    /// Minimum window height.
    static let windowMinHeight: CGFloat = 400
    /// Default window width.
    static let windowDefaultWidth: CGFloat = 2100
    /// Default window height.
    static let windowDefaultHeight: CGFloat = 1312

    // MARK: Toolbar (extended)

    /// Toolbar icon button height (toolbarHeight - 4).
    static let toolbarButtonHeight: CGFloat = 22
    /// Toolbar icon button width.
    static let toolbarIconButtonWidth: CGFloat = 26
    /// Toolbar text field minimum width (CodeSpeak run bar).
    static let toolbarTextFieldMinWidth: CGFloat = 100
    /// Toolbar text field maximum width (CodeSpeak run bar).
    static let toolbarTextFieldMaxWidth: CGFloat = 200
    /// Minimum popover width.
    static let popoverMinWidth: CGFloat = 200

    // MARK: Tab Bar (extended)

    /// Active tab underline indicator height (same as codeSpeakAccentLineHeight).
    static let tabActiveIndicatorHeight: CGFloat = 2

    // MARK: Sidebar (extended)

    /// Icon strip width (left edge of sidebar).
    static let iconStripWidth: CGFloat = 32
    /// Icon strip button hit area.
    static let iconStripButtonSize: CGFloat = 24
    /// Settings/gear button size in sidebar headers.
    static let sidebarActionButtonSize: CGFloat = 20
    /// Chevron frame width (for indent alignment).
    static let chevronFrameWidth: CGFloat = 14

    // MARK: Diff Panel

    /// Gutter width in diff view (line numbers).
    static let diffGutterWidth: CGFloat = 32
    /// Prefix (+/-/space) column width in diff view.
    static let diffPrefixWidth: CGFloat = 12
    /// Status letter (M/A/D) column width in changes panel.
    static let statusLetterWidth: CGFloat = 16

    // MARK: Traceability Panel (extended)

    /// Minimum width for the traceability panel.
    static let traceabilityPanelMinWidth: CGFloat = 240
    /// Maximum width for the traceability panel.
    static let traceabilityPanelMaxWidth: CGFloat = 400

    // MARK: Indicators / Badges

    /// Small status dot diameter (install status, etc).
    static let statusDotSize: CGFloat = 8
    /// Small icon button size (copy, action buttons inside rows).
    static let smallIconButtonSize: CGFloat = 16
    /// Step indicator circle diameter (install wizard).
    static let stepIndicatorSize: CGFloat = 20
    /// Close button hit area.
    static let closeButtonSize: CGFloat = 24

    // MARK: Empty States

    /// Standard empty state icon size.
    static let emptyStateIconSize: CGFloat = 24
    /// Large empty state icon size.
    static let emptyStateIconLargeSize: CGFloat = 32

    // MARK: Welcome Screen

    /// Max width for welcome screen project/action lists.
    static let welcomeListMaxWidth: CGFloat = 420

    // MARK: Settings Window

    /// Settings window minimum width.
    static let settingsWindowMinWidth: CGFloat = 860
    /// Settings window ideal width.
    static let settingsWindowIdealWidth: CGFloat = 960
    /// Settings window minimum height.
    static let settingsWindowMinHeight: CGFloat = 680
    /// Settings window ideal height.
    static let settingsWindowIdealHeight: CGFloat = 760
    /// Settings sidebar width.
    static let settingsSidebarWidth: CGFloat = 200
    /// Settings label column width (for aligned form rows).
    static let settingsLabelWidth: CGFloat = 170
    /// Settings picker width.
    static let settingsPickerWidth: CGFloat = 240
    /// Max height for large scrollable settings lists.
    static let settingsListMaxHeightLarge: CGFloat = 320
    /// Max height for small scrollable settings lists.
    static let settingsListMaxHeightSmall: CGFloat = 200

    // MARK: Sheets

    /// Small sheet size.
    static let sheetSmallWidth: CGFloat = 320
    static let sheetSmallHeight: CGFloat = 240
    /// Medium sheet size.
    static let sheetMediumWidth: CGFloat = 380
    static let sheetMediumHeight: CGFloat = 320
    /// Large sheet size (spec editor, install wizard).
    static let sheetLargeWidth: CGFloat = 900
    static let sheetLargeHeight: CGFloat = 600

    /// Form-sheet width (install-agent wizard, new-project form).
    static let formSheetMinWidth: CGFloat = 400
    static let formSheetIdealWidth: CGFloat = 480
    /// Install-agent wizard sheet height.
    static let installSheetMinHeight: CGFloat = 360
    static let installSheetIdealHeight: CGFloat = 420
    /// New-project form sheet minimum height.
    static let newProjectSheetMinHeight: CGFloat = 240

    // MARK: Popovers

    /// Add project popover width (min / ideal / max).
    static let addProjectPopoverMinWidth: CGFloat = 280
    static let addProjectPopoverWidth: CGFloat = 300
    static let addProjectPopoverMaxWidth: CGFloat = 360

    // MARK: Forms

    /// Input label column width.
    static let inputLabelWidth: CGFloat = 50
    /// Input row height.
    static let inputRowHeight: CGFloat = 32
    /// Loading spinner row height.
    static let spinnerRowHeight: CGFloat = 32

    // MARK: Content Area

    /// Minimum content area width (right of sidebar).
    static let contentMinWidth: CGFloat = 300

    // MARK: Code Viewer

    /// Character width estimate for gutter calculation.
    static let codeDigitWidth: CGFloat = 8

    // MARK: Settings — Compact Inputs (M23)

    /// Compact port input field width (RemoteControlSettingsPane).
    static let portInputWidth: CGFloat = 80
    /// Compact picker width (CodeSpeak default-command picker, etc.).
    static let compactPickerWidth: CGFloat = 90

    // MARK: Editor Sheets (M24)

    /// Default width for editor sheets (Skill/Text/Command/Claude/Agent editors).
    static let editorSheetWidth: CGFloat = 680
    /// Default height for editor sheets.
    static let editorSheetHeight: CGFloat = 520

    // MARK: Progress Spinner Scales (M26 + M27)

    /// Tiny ProgressView scale (~0.5) — branch row, inline AI sparkle.
    static let progressScaleTiny: CGFloat = 0.5
    /// Small ProgressView scale (~0.6) — spec loading.
    static let progressScaleSmall: CGFloat = 0.6
    /// Medium ProgressView scale (~0.7) — diff/traceability loading.
    static let progressScaleMedium: CGFloat = 0.7
    /// Large ProgressView scale (~0.8) — file-column loading.
    static let progressScaleLarge: CGFloat = 0.8
    /// Inline button ProgressView scale (~0.65) — commit-button loading.
    static let progressScaleButton: CGFloat = 0.65

    // MARK: CodeSpeak HSplitView Columns (M28)

    /// CodeSpeak specs (left) column: minimum width.
    static let codeSpeakSpecsColumnMin: CGFloat = 180
    /// CodeSpeak specs (left) column: ideal width.
    static let codeSpeakSpecsColumnIdeal: CGFloat = 240
    /// CodeSpeak specs (left) column: maximum width.
    static let codeSpeakSpecsColumnMax: CGFloat = 320
    /// CodeSpeak editor (center) column: minimum width.
    static let codeSpeakEditorColumnMin: CGFloat = 300
    /// CodeSpeak build-output (right) column: minimum width.
    static let codeSpeakBuildColumnMin: CGFloat = 240
    /// CodeSpeak build-output (right) column: ideal width.
    static let codeSpeakBuildColumnIdeal: CGFloat = 320
    /// CodeSpeak build-output (right) column: maximum width.
    static let codeSpeakBuildColumnMax: CGFloat = 480

    // MARK: Settings — Label Column Widths (L17)

    /// Short settings label column (~80pt).
    static let settingsLabelWidthShort: CGFloat = 80
    /// Medium settings label column (~90pt).
    static let settingsLabelWidthMedium: CGFloat = 90
    /// Long settings label column (~160pt) — e.g. inline TextFields.
    static let settingsLabelWidthLong: CGFloat = 160

    // MARK: Editor / Tree Misc (L22..L25)

    /// Inset for NSTextView-based editors (CodeSpeakEditorView).
    static let editorContentInset = NSSize(width: 12, height: 12)
    /// Placeholder column width for missing tree disclosure chevron (matches iconSM).
    static let treeChevronPlaceholderWidth: CGFloat = 9
    /// Drop-target indicator stroke width (intentional thicker line).
    static let dropIndicatorStrokeWidth: CGFloat = 1.5
    /// Glow shadow radius for activity indicator pulse.
    static let indicatorGlowRadius: CGFloat = 4

    // MARK: Remote QR / Popover Sizes (L26)

    /// QR code image side (square).
    static let qrCodeSize: CGFloat = 200
    /// QR popover total width.
    static let qrPopoverWidth: CGFloat = 260
    /// Remote-status popover minimum width.
    static let remotePopoverMinWidth: CGFloat = 240
    /// Remote-status popover maximum width.
    static let remotePopoverMaxWidth: CGFloat = 280

    // MARK: Sheets (extended L27)

    /// Small wizard sheet width (spec wizard).
    static let wizardSmallWidth: CGFloat = 400
    /// Small wizard sheet height.
    static let wizardSmallHeight: CGFloat = 220
    /// Large sheet minimum width (spec editor).
    static let sheetLargeMinWidth: CGFloat = 600
    /// Large sheet minimum height (spec editor).
    static let sheetLargeMinHeight: CGFloat = 400
    /// Small sheet min width = sheetSmallWidth - 20 (used in create-branch sheet).
    static let sheetSmallMinWidth: CGFloat = sheetSmallWidth - 20
    /// Small sheet min height = sheetSmallHeight - 40 (used in create-branch sheet).
    static let sheetSmallMinHeight: CGFloat = sheetSmallHeight - 40

    // MARK: Animation durations (ARCH-L2)

    /// Delay (ms) before showing a follow-up popover/sheet after dismissing
    /// the previous one. Gives macOS time to retract the existing popover
    /// before opening the next; otherwise the new sheet may be cancelled.
    static let popoverDismissAnimationMS: Int = 100

    /// Duration (ms) a "Copied!" toast remains visible after a clipboard
    /// copy action.
    static let clipboardConfirmationMS: Int = 1_500
}
