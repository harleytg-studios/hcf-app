// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "HCFModules",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "HCFCore", targets: ["HCFCore"]),
        .library(name: "HCFForum", targets: ["HCFForum"]),
        .library(name: "HCFUI", targets: ["HCFUI"]),
        .library(name: "HCFNotifications", targets: ["HCFNotifications"]),
        .library(name: "HCFUpdates", targets: ["HCFUpdates"]),
        .library(name: "HCFPlatform", targets: ["HCFPlatform"]),
        .library(name: "HCFWidget", targets: ["HCFWidget"])
    ],
    targets: [
        .target(name: "HCFCore"),
        .target(name: "HCFPlatform", dependencies: ["HCFCore"]),
        .target(name: "HCFForum", dependencies: ["HCFCore", "HCFPlatform"]),
        .target(name: "HCFWidget", dependencies: ["HCFCore"]),
        .target(name: "HCFNotifications", dependencies: ["HCFCore", "HCFForum"]),
        .target(name: "HCFUpdates", dependencies: ["HCFCore"]),
        .target(
            name: "HCFUI",
            dependencies: ["HCFCore", "HCFForum", "HCFNotifications", "HCFUpdates", "HCFPlatform"]
        )
    ],
    swiftLanguageModes: [.v5]
)
