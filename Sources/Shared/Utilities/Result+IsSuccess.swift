// MARK: - Result + IsSuccess
// Extension on the standard `Result` type with a Bool projection of its
// case. Lives in `Shared/Utilities/` rather than in the Remote Control
// HTTP router so the helper can be re-used (and reasoned about) outside
// of one network module.
//
// macOS 14+, Swift 5.10

import Foundation

extension Result {
    /// Boolean projection: `true` if this is a `.success`, `false` if
    /// `.failure`.
    ///
    /// Convenience for sites that need a side-effecting predicate (audit
    /// logs, metrics) but don't want to bind the success value.
    var isSuccess: Bool {
        if case .success = self { return true }
        return false
    }
}
