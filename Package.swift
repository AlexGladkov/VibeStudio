// swift-tools-version: 5.10
// Package.swift — SPM manifest for VibeStudio
// Mirrors the app package dependencies so `swift test` can run locally.
// Release app builds still use the Xcode project / xcodebuild.

import PackageDescription

let package = Package(
    name: "VibeStudio",
    defaultLocalization: "en",
    platforms: [
        .macOS(.v14)
    ],
    products: [
        .executable(
            name: "VibeStudio",
            targets: ["VibeStudio"]
        )
    ],
    dependencies: [
        // Terminal emulator engine — PTY, xterm-256color, mouse reporting
        .package(
            url: "https://github.com/migueldeicaza/SwiftTerm.git",
            from: "1.12.0"
        ),
        .package(
            url: "https://github.com/apple/swift-nio.git",
            from: "2.76.0"
        ),
        .package(
            url: "https://github.com/apple/swift-nio-ssl.git",
            from: "2.29.0"
        ),
        .package(
            url: "https://github.com/apple/swift-certificates.git",
            from: "1.7.0"
        ),
        .package(
            url: "https://github.com/sparkle-project/Sparkle.git",
            from: "2.6.0"
        )
    ],
    targets: [
        .executableTarget(
            name: "VibeStudio",
            dependencies: [
                .product(name: "SwiftTerm", package: "SwiftTerm"),
                .product(name: "NIOCore", package: "swift-nio"),
                .product(name: "NIOPosix", package: "swift-nio"),
                .product(name: "NIOHTTP1", package: "swift-nio"),
                .product(name: "NIOWebSocket", package: "swift-nio"),
                .product(name: "NIOSSL", package: "swift-nio-ssl"),
                .product(name: "X509", package: "swift-certificates"),
                .product(name: "Sparkle", package: "Sparkle")
            ],
            path: "Sources",
            resources: [
                .process("Assets.xcassets")
            ],
            swiftSettings: [
                .enableUpcomingFeature("BareSlashRegexLiterals")
            ]
        ),
        .testTarget(
            name: "VibeStudioTests",
            dependencies: [
                "VibeStudio",
                .product(name: "NIOHTTP1", package: "swift-nio")
            ],
            path: "Tests",
            swiftSettings: [
                .enableUpcomingFeature("BareSlashRegexLiterals")
            ]
        )
    ]
)
