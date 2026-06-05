// MARK: - KeychainHelperTests
// Roundtrip set/get/delete with a unique account namespace per run.
//
// CI NOTE: The macOS test host runs with the app bundle ID; SecItemAdd may fail
// (errSecMissingEntitlement) if signed without keychain-access-groups, or if the
// keychain is locked. Tests skip when save() returns false to remain green
// in headless CI environments without keychain access.

import XCTest
@testable import VibeStudio

final class KeychainHelperTests: XCTestCase {

    private var account: String!

    override func setUp() {
        super.setUp()
        account = "VS_TEST_\(UUID().uuidString)"
    }

    override func tearDown() {
        if let account { _ = KeychainHelper.delete(account: account) }
        super.tearDown()
    }

    func test_save_andLoad_roundTripsValue() throws {
        let value = "sk-ant-test-\(UUID().uuidString)"
        let saved = KeychainHelper.save(account: account, value: value)
        try XCTSkipUnless(saved, "Keychain unavailable in this environment (likely unsigned/headless CI)")
        XCTAssertEqual(KeychainHelper.load(account: account), value)
    }

    func test_save_overwritesExistingValue() throws {
        try XCTSkipUnless(KeychainHelper.save(account: account, value: "first"), "Keychain unavailable")
        XCTAssertTrue(KeychainHelper.save(account: account, value: "second"))
        XCTAssertEqual(KeychainHelper.load(account: account), "second")
    }

    func test_load_missingKey_returnsNil() {
        let missing = "VS_MISSING_\(UUID().uuidString)"
        XCTAssertNil(KeychainHelper.load(account: missing))
    }

    func test_delete_existingKey_succeeds_andRemovesIt() throws {
        try XCTSkipUnless(KeychainHelper.save(account: account, value: "x"), "Keychain unavailable")
        XCTAssertTrue(KeychainHelper.delete(account: account))
        XCTAssertNil(KeychainHelper.load(account: account))
    }

    func test_delete_missingKey_returnsTrue_idempotent() {
        let missing = "VS_NOPE_\(UUID().uuidString)"
        // delete() treats errSecItemNotFound as success per its contract.
        XCTAssertTrue(KeychainHelper.delete(account: missing))
    }

    func test_resolver_returnsKeychainValue() throws {
        try XCTSkipUnless(KeychainHelper.save(account: account, value: "resolved"), "Keychain unavailable")
        let resolver = KeychainAPIKeyResolver()
        XCTAssertEqual(resolver.resolve(for: account), "resolved")
    }

    func test_resolver_returnsNil_forUnknownAccount() {
        let resolver = KeychainAPIKeyResolver()
        XCTAssertNil(resolver.resolve(for: "VS_UNKNOWN_\(UUID().uuidString)"))
    }
}
