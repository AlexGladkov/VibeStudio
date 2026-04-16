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
    static let windowDefaultWidth: CGFloat = 1600
    /// Default window height.
    static let windowDefaultHeight: CGFloat = 1000

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
    static let settingsLabelWidth: CGFloat = 80
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

    // MARK: Popovers

    /// Add project popover width.
    static let addProjectPopoverWidth: CGFloat = 300

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
}
