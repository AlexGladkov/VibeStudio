// MARK: - NetworkUtility
// LAN IP address discovery for QR code generation.
// macOS 14+, Swift 5.10

import Foundation

enum NetworkUtility {

    /// Returns the first non-loopback IPv4 address on an `en*` interface, or `nil`
    /// if the machine has no active LAN connection.
    static func localLANIPAddress() -> String? {
        allLocalIPv4Addresses().first
    }

    /// Returns every non-loopback IPv4 address on `en*` interfaces.
    ///
    /// Macs typically have Ethernet (`en0`) + Wi-Fi (`en1`) simultaneously; both
    /// IPs need to appear in the TLS SAN list so the iPhone can connect over
    /// either interface without triggering a cert error (SEC-H2).
    ///
    /// Order matches the order returned by `getifaddrs`, which roughly tracks
    /// interface priority on macOS.
    static func allLocalIPv4Addresses() -> [String] {
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0, let firstAddr = ifaddr else { return [] }
        defer { freeifaddrs(ifaddr) }

        var seen = Set<String>()
        var result: [String] = []
        for ptr in sequence(first: firstAddr, next: { $0.pointee.ifa_next }) {
            let sa = ptr.pointee.ifa_addr.pointee
            guard sa.sa_family == UInt8(AF_INET) else { continue }

            let name = String(cString: ptr.pointee.ifa_name)
            guard name.hasPrefix("en") else { continue }

            var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            let rc = getnameinfo(
                ptr.pointee.ifa_addr,
                socklen_t(sa.sa_len),
                &hostname,
                socklen_t(hostname.count),
                nil, 0,
                NI_NUMERICHOST
            )
            guard rc == 0 else { continue }

            let address = String(cString: hostname)
            if address != "127.0.0.1" && seen.insert(address).inserted {
                result.append(address)
            }
        }
        return result
    }
}
