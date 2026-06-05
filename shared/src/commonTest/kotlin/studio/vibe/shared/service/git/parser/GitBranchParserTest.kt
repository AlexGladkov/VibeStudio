package studio.vibe.shared.service.git.parser

import studio.vibe.shared.model.GitServiceError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Unit tests for [GitBranchParser.validateBranchName].
 *
 * Each test exercises exactly one invalid character class or edge-case so that
 * failures are immediately diagnosable without reading test body context.
 *
 * Covers:
 * - Empty string
 * - Leading `-` (would be interpreted as CLI flag)
 * - `..` (git refspec range operator)
 * - `~` (ancestor ref operator)
 * - `^` (parent ref operator)
 * - `:` (refspec separator / path separator on Windows)
 * - `\` (path separator on Windows / injection risk)
 * - Valid branch name passes without throwing
 */
class GitBranchParserTest {

    // -------------------------------------------------------------------------
    // Invalid names — each must throw GitServiceError.CommandFailed
    // -------------------------------------------------------------------------

    @Test
    fun validateBranchName_emptyString_throws() {
        // Arrange
        val name = ""

        // Act & Assert
        val ex = assertFailsWith<GitServiceError.CommandFailed> {
            GitBranchParser.validateBranchName(name)
        }
        assertNotNull(ex.stderr)
        assertEquals("validate", ex.command)
    }

    @Test
    fun validateBranchName_leadingDash_throws() {
        // Arrange: leading `-` would make git interpret the name as a flag
        val name = "-feature"

        // Act & Assert
        assertFailsWith<GitServiceError.CommandFailed> {
            GitBranchParser.validateBranchName(name)
        }
    }

    @Test
    fun validateBranchName_doubleDot_throws() {
        // Arrange: `..` is the git range operator, forbidden in branch names
        val name = "feature..branch"

        // Act & Assert
        assertFailsWith<GitServiceError.CommandFailed> {
            GitBranchParser.validateBranchName(name)
        }
    }

    @Test
    fun validateBranchName_tilde_throws() {
        // Arrange: `~` is the git ancestor operator
        val name = "feature~1"

        // Act & Assert
        assertFailsWith<GitServiceError.CommandFailed> {
            GitBranchParser.validateBranchName(name)
        }
    }

    @Test
    fun validateBranchName_caret_throws() {
        // Arrange: `^` is the git parent operator
        val name = "release^0"

        // Act & Assert
        assertFailsWith<GitServiceError.CommandFailed> {
            GitBranchParser.validateBranchName(name)
        }
    }

    @Test
    fun validateBranchName_colon_throws() {
        // Arrange: `:` is the refspec separator
        val name = "origin:main"

        // Act & Assert
        assertFailsWith<GitServiceError.CommandFailed> {
            GitBranchParser.validateBranchName(name)
        }
    }

    @Test
    fun validateBranchName_backslash_throws() {
        // Arrange: `\` is a path separator on Windows and an injection risk
        val name = "feature\\branch"

        // Act & Assert
        assertFailsWith<GitServiceError.CommandFailed> {
            GitBranchParser.validateBranchName(name)
        }
    }

    @Test
    fun validateBranchName_space_throws() {
        // Arrange: spaces are not allowed in git branch names
        val name = "feature branch"

        // Act & Assert
        assertFailsWith<GitServiceError.CommandFailed> {
            GitBranchParser.validateBranchName(name)
        }
    }

    @Test
    fun validateBranchName_semicolonInjection_throws() {
        // Arrange: shell injection attempt
        val name = "main;rm -rf /"

        // Act & Assert
        assertFailsWith<GitServiceError.CommandFailed> {
            GitBranchParser.validateBranchName(name)
        }
    }

    // -------------------------------------------------------------------------
    // Valid names — must NOT throw
    // -------------------------------------------------------------------------

    @Test
    fun validateBranchName_simpleName_doesNotThrow() {
        // Arrange
        val name = "main"

        // Act & Assert: no exception expected
        GitBranchParser.validateBranchName(name)
    }

    @Test
    fun validateBranchName_featureSlashName_doesNotThrow() {
        // Arrange: slash-separated prefixed branch (common convention)
        val name = "feature/my-feature"

        // Act & Assert
        GitBranchParser.validateBranchName(name)
    }

    @Test
    fun validateBranchName_releaseWithDot_doesNotThrow() {
        // Arrange: single dot is fine, only `..` is forbidden
        val name = "release/1.2.3"

        // Act & Assert
        GitBranchParser.validateBranchName(name)
    }

    @Test
    fun validateBranchName_alphanumericWithDashUnderscore_doesNotThrow() {
        // Arrange
        val name = "fix_some-bug_123"

        // Act & Assert
        GitBranchParser.validateBranchName(name)
    }

    @Test
    fun validateBranchName_atSymbol_doesNotThrow() {
        // Arrange: `@` is explicitly allowed by the validBranchRegex
        val name = "feature@v2"

        // Act & Assert
        GitBranchParser.validateBranchName(name)
    }
}
