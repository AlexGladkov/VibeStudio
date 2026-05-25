// MARK: - NetworkUtility
// LAN IP address discovery for QR code generation.
// macOS 14+, Swift 5.10

import Foundation

enum NetworkUtility {

    /// Returns the first non-loopback IPv4 address on an `en*` interface, or `nil`
    /// if the machine has no active LAN connection.
    static func localLANIPAddress() -> String? {
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0, let firstAddr = ifaddr else { return nil }
        defer { freeifaddrs(ifaddr) }

        for ptr in sequence(first: firstAddr, next: { $0.pointee.ifa_next }) {
            let sa = ptr.pointee.ifa_addr.pointee
            guard sa.sa_family == UInt8(AF_INET) else { continue }

            let name = String(cString: ptr.pointee.ifa_name)
            guard name.hasPrefix("en") else { continue }

            var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            let result = getnameinfo(
                ptr.pointee.ifa_addr,
                socklen_t(sa.sa_len),
                &hostname,
                socklen_t(hostname.count),
                nil, 0,
                NI_NUMERICHOST
            )
            guard result == 0 else { continue }

            let address = String(cString: hostname)
            if address != "127.0.0.1" {
                return address
            }
        }
        return nil
    }
}
