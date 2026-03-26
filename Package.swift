// swift-tools-version: 5.10
// Package.swift — SPM manifest for VibeStudio
// Used for dependency resolution (SwiftTerm).
// The actual build uses Xcode project / xcodebuild.

import PackageDescription

let package = Package(
    name: "VibeStudio",
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
        )
    ],
    targets: [
        .executableTarget(
            name: "VibeStudio",
            dependencies: [
                .product(name: "SwiftTerm", package: "SwiftTerm")
            ],
            path: "Sources"
        ),
        .testTarget(
            name: "VibeStudioTests",
            dependencies: ["VibeStudio"],
            path: "VibeStudioTests"
        )
    ]
)
