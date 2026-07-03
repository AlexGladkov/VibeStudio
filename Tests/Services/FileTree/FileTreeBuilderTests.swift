import XCTest
@testable import VibeStudio

/// Tests for ``FileTreeBuilder`` against a real temporary directory tree.
///
/// Each test builds a disposable structure under a unique temp directory in
/// `setUp` and tears it down in `tearDown`, so the tests are fully idempotent
/// and never touch the user's file system state.
final class FileTreeBuilderTests: XCTestCase {

    private var root: URL!
    private let fm = FileManager.default

    override func setUpWithError() throws {
        try super.setUpWithError()
        root = fm.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try fm.createDirectory(at: root, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        if let root, fm.fileExists(atPath: root.path) {
            try? fm.removeItem(at: root)
        }
        root = nil
        try super.tearDownWithError()
    }

    // MARK: - Fixture Helpers

    private func makeDir(_ relativePath: String) throws {
        let url = root.appendingPathComponent(relativePath, isDirectory: true)
        try fm.createDirectory(at: url, withIntermediateDirectories: true)
    }

    private func makeFile(_ relativePath: String, contents: String = "x") throws {
        let url = root.appendingPathComponent(relativePath)
        try fm.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try contents.data(using: .utf8)!.write(to: url)
    }

    /// Names of the top-level nodes, in the order produced by the builder.
    private func topLevelNames(_ nodes: [FileTreeNode]) -> [String] {
        nodes.map(\.name)
    }

    private func node(named name: String, in nodes: [FileTreeNode]) -> FileTreeNode? {
        nodes.first { $0.name == name }
    }

    // MARK: - Excluded Names

    func testBuildTree_excludedDirectories_areFiltered() throws {
        try makeDir("node_modules")
        try makeDir(".git")
        try makeDir("src")
        try makeFile("README.md")

        let tree = FileTreeBuilder.buildTree(at: root)
        let names = topLevelNames(tree)

        XCTAssertFalse(names.contains("node_modules"), "node_modules must be excluded")
        XCTAssertFalse(names.contains(".git"), ".git must be excluded")
        XCTAssertTrue(names.contains("src"))
        XCTAssertTrue(names.contains("README.md"))
    }

    func testBuildTree_hiddenExcludedEntries_areFiltered() throws {
        // Dot-prefixed entries that appear in PathConstants.excludedDirectoryNames.
        try makeDir(".build")
        try makeFile(".DS_Store")
        try makeFile("main.swift")

        let tree = FileTreeBuilder.buildTree(at: root)
        let names = topLevelNames(tree)

        XCTAssertFalse(names.contains(".build"), ".build must be excluded")
        XCTAssertFalse(names.contains(".DS_Store"), ".DS_Store must be excluded")
        XCTAssertTrue(names.contains("main.swift"))
    }

    func testBuildTree_nonExcludedDotfile_isIncluded() throws {
        // Documents the real contract: only names in the excluded set are
        // filtered. A generic dotfile that is NOT in the set is surfaced.
        try makeFile(".env")

        let tree = FileTreeBuilder.buildTree(at: root)

        XCTAssertTrue(topLevelNames(tree).contains(".env"),
                      "Only excluded names are filtered; other dotfiles remain visible")
    }

    // MARK: - Sorting

    func testBuildTree_sortsDirectoriesBeforeFiles_caseInsensitive() throws {
        // Mixed-case directories and files to exercise both ordering rules.
        try makeDir("Zebra")
        try makeDir("apple")
        try makeFile("Banana.txt")
        try makeFile("alpha.txt")

        let tree = FileTreeBuilder.buildTree(at: root)

        // Directories first (alpha, case-insensitive), then files (same rule).
        XCTAssertEqual(topLevelNames(tree), ["apple", "Zebra", "alpha.txt", "Banana.txt"])
    }

    // MARK: - maxDepth

    func testBuildTree_maxDepth_limitsRecursion() throws {
        try makeFile("level1/level2/level3/deep.txt")

        // Unlimited depth: full nesting materialises.
        let full = FileTreeBuilder.buildTree(at: root, maxDepth: nil)
        guard case .directory(let l1Full)? = node(named: "level1", in: full) else {
            return XCTFail("Expected level1 directory")
        }
        XCTAssertFalse(l1Full.children.isEmpty, "Unlimited depth should expand level1's children")

        // maxDepth = 1: level1 present at depth 0, but its children are pruned.
        let shallow = FileTreeBuilder.buildTree(at: root, maxDepth: 1)
        guard case .directory(let l1Shallow)? = node(named: "level1", in: shallow) else {
            return XCTFail("Expected level1 directory at depth 0")
        }
        XCTAssertTrue(l1Shallow.children.isEmpty, "maxDepth=1 must prune below the first level")
    }

    // MARK: - Symlink Loop (R-14)

    func testBuildTree_symlinkLoopToParent_doesNotRecurseInfinitely() throws {
        // child/loop -> root (parent). Naive recursion would loop forever.
        try makeDir("child")
        let loopLink = root.appendingPathComponent("child/loop")
        try fm.createSymbolicLink(at: loopLink, withDestinationURL: root)

        // The guarantee under test: buildTree terminates (R-14 visited-path guard).
        let tree = FileTreeBuilder.buildTree(at: root)

        // Sanity: the structure is finite and 'child' is present.
        XCTAssertTrue(topLevelNames(tree).contains("child"))
        guard case .directory(let child)? = node(named: "child", in: tree) else {
            return XCTFail("Expected child directory")
        }
        // The symlink resolves back to an already-visited path, so it contributes
        // no further descendants (empty children), not an infinite tree.
        if case .directory(let loop)? = node(named: "loop", in: child.children) {
            XCTAssertTrue(loop.children.isEmpty, "Loop symlink must not re-expand the parent")
        }
    }

    // MARK: - Git Status Annotation

    func testBuildTree_annotatesGitStatus_byRelativePath() throws {
        try makeFile("tracked.txt")
        try makeFile("clean.txt")

        let status = GitStatus(
            branch: "main",
            aheadCount: 0,
            behindCount: 0,
            stagedFiles: [],
            unstagedFiles: [GitFile(path: "tracked.txt", status: .modified)],
            untrackedFiles: []
        )

        let tree = FileTreeBuilder.buildTree(at: root, gitStatus: status)

        guard case .file(let tracked)? = node(named: "tracked.txt", in: tree) else {
            return XCTFail("Expected tracked.txt file node")
        }
        XCTAssertEqual(tracked.gitStatus, .modified, "Matching relative path must be annotated")

        guard case .file(let clean)? = node(named: "clean.txt", in: tree) else {
            return XCTFail("Expected clean.txt file node")
        }
        XCTAssertNil(clean.gitStatus, "File with no git entry must have nil status")
    }

    func testBuildTree_annotatesGitStatus_forNestedFile() throws {
        try makeFile("sub/nested.txt")

        let status = GitStatus(
            branch: "main",
            aheadCount: 0,
            behindCount: 0,
            stagedFiles: [GitFile(path: "sub/nested.txt", status: .added)],
            unstagedFiles: [],
            untrackedFiles: []
        )

        let tree = FileTreeBuilder.buildTree(at: root, gitStatus: status)

        guard case .directory(let sub)? = node(named: "sub", in: tree),
              case .file(let nested)? = node(named: "nested.txt", in: sub.children) else {
            return XCTFail("Expected sub/nested.txt file node")
        }
        XCTAssertEqual(nested.gitStatus, .added,
                       "Nested relative path 'sub/nested.txt' must be annotated")
    }
}
