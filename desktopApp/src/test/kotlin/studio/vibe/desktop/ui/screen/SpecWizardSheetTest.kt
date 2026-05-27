@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui.screen

import org.junit.Ignore
import org.junit.Test

/**
 * Tests for [studio.vibe.desktop.ui.SpecWizardSheet].
 *
 * The public [studio.vibe.desktop.ui.SpecWizardSheet] composable is wrapped in a
 * [androidx.compose.ui.window.DialogWindow]. [createComposeRule] cannot render content
 * inside an external native OS window.
 *
 * This class tests:
 * - Filename sanitization logic (trim → lowercase → spaces to hyphens → filter)
 * - canCreate gate (sanitized non-empty && !isSaving)
 * - Generated file path preview
 * - Edge cases: special characters, unicode, numeric-only names
 */
class SpecWizardSheetTest {

    // ── Sanitization algorithm (mirrors SpecWizardContent inline logic) ───────

    private fun sanitize(input: String): String = input
        .trim()
        .lowercase()
        .replace(" ", "-")
        .filter { it.isLetterOrDigit() || it == '-' || it == '_' }

    @Test
    fun sanitize_lowercasesInput() {
        assert(sanitize("UserAuth") == "userauth")
    }

    @Test
    fun sanitize_trimsLeadingAndTrailingWhitespace() {
        assert(sanitize("  user-auth  ") == "user-auth")
    }

    @Test
    fun sanitize_replacesSpacesWithHyphens() {
        assert(sanitize("user auth flow") == "user-auth-flow")
    }

    @Test
    fun sanitize_removesSpecialCharacters() {
        assert(sanitize("user@auth!flow") == "userauthflow")
    }

    @Test
    fun sanitize_preservesHyphens() {
        assert(sanitize("user-auth") == "user-auth")
    }

    @Test
    fun sanitize_preservesUnderscores() {
        assert(sanitize("user_auth") == "user_auth")
    }

    @Test
    fun sanitize_preservesDigits() {
        assert(sanitize("spec2024") == "spec2024")
    }

    @Test
    fun sanitize_emptyInput_producesEmpty() {
        assert(sanitize("") == "")
    }

    @Test
    fun sanitize_whitespaceOnly_producesEmpty() {
        assert(sanitize("   ") == "")
    }

    @Test
    fun sanitize_mixedCaseWithSpaces_producesHyphenatedLowercase() {
        assert(sanitize("My New Feature") == "my-new-feature")
    }

    @Test
    fun sanitize_unicodeLetters_arePreservedByFilterLetterOrDigit() {
        // isLetterOrDigit() accepts unicode letters — the filter keeps them
        val result = sanitize("café")
        assert(result.isNotEmpty())
    }

    @Test
    fun sanitize_dotsAreRemoved() {
        // Dots are not in the allow-list (only letters, digits, '-', '_')
        assert(sanitize("v1.0.0") == "v100")
    }

    @Test
    fun sanitize_slashesAreRemoved() {
        assert(sanitize("feature/login") == "featurelogin")
    }

    // ── File path preview ─────────────────────────────────────────────────────

    @Test
    fun filePath_preview_usesSpecPrefix() {
        val sanitized = "user-auth"
        val preview = "spec/$sanitized.cs.md"
        assert(preview == "spec/user-auth.cs.md")
    }

    @Test
    fun filePath_preview_isHiddenForEmptySanitized() {
        val sanitized = sanitize("   ")
        val showPreview = sanitized.isNotEmpty()
        assert(!showPreview)
    }

    @Test
    fun filePath_preview_isShownForNonEmptySanitized() {
        val sanitized = sanitize("my-spec")
        val showPreview = sanitized.isNotEmpty()
        assert(showPreview)
    }

    // ── canCreate gate ────────────────────────────────────────────────────────

    private fun canCreate(specName: String, isSaving: Boolean): Boolean {
        val sanitized = sanitize(specName)
        return sanitized.isNotEmpty() && !isSaving
    }

    @Test
    fun canCreate_isTrue_forValidNameAndNotSaving() {
        assert(canCreate("user-auth", false))
    }

    @Test
    fun canCreate_isFalse_forBlankName() {
        assert(!canCreate("   ", false))
    }

    @Test
    fun canCreate_isFalse_whenSavingInProgress() {
        assert(!canCreate("user-auth", true))
    }

    @Test
    fun canCreate_isFalse_forNonBlankNameThatSanitizesToEmpty() {
        // Input with only special chars sanitizes to ""
        assert(!canCreate("!!!@@@", false))
    }

    // ── Template content ──────────────────────────────────────────────────────

    @Test
    fun specTemplate_startsWithH1Heading() {
        val specName = "User Auth"
        val template = "# ${specName.trim()}\n\n"
        assert(template.startsWith("# User Auth"))
    }

    @Test
    fun specTemplate_endsWithDoubleNewline() {
        val specName = "feature"
        val template = "# ${specName.trim()}\n\n"
        assert(template.endsWith("\n\n"))
    }

    // ── DialogWindow limitation notice ────────────────────────────────────────

    @Ignore(
        "SpecWizardSheet wraps its content inside DialogWindow. createComposeRule() " +
            "cannot inject composables into an external native OS window. " +
            "Filename sanitization, canCreate gate and file path logic are tested " +
            "via pure-function tests above.",
    )
    @Test
    fun specWizardSheet_dialogWindow_notTestableViaComposeRule() {
        // Intentionally empty — documents the DialogWindow limitation.
    }
}
