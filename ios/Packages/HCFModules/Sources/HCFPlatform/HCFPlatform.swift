import Foundation
import Network
import Combine
import HCFCore

public enum HCFWindowMode: String, Codable, Sendable {
    case phone = "Phone"
    case tablet = "Tablet"
    case desktop = "Desktop"
}

public enum HCFAdaptiveLayout {
    public static let tabletMinimum: CGFloat = 600
    public static let desktopMinimum: CGFloat = 840

    public static func mode(for width: CGFloat) -> HCFWindowMode {
        if width >= desktopMinimum { return .desktop }
        if width >= tabletMinimum { return .tablet }
        return .phone
    }
}

@MainActor
public final class HCFNetworkMonitor: ObservableObject {
    public static let shared = HCFNetworkMonitor()

    @Published public private(set) var isConnected = true
    @Published public private(set) var isExpensive = false
    @Published public private(set) var isConstrained = false
    @Published public private(set) var interface = "unknown"

    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "com.harleytg.hcf.network-monitor", qos: .utility)

    public init() {
        monitor.pathUpdateHandler = { [weak self] path in
            let connected = path.status == .satisfied
            let expensive = path.isExpensive
            let constrained = path.isConstrained
            let interface: String
            if path.usesInterfaceType(.wifi) { interface = "wifi" }
            else if path.usesInterfaceType(.cellular) { interface = "cellular" }
            else if path.usesInterfaceType(.wiredEthernet) { interface = "ethernet" }
            else { interface = "other" }
            Task { @MainActor [weak self] in
                self?.isConnected = connected
                self?.isExpensive = expensive
                self?.isConstrained = constrained
                self?.interface = interface
            }
        }
        monitor.start(queue: queue)
    }

    deinit { monitor.cancel() }
}

public enum HCFPlatformInfo {
    public static var deviceDescription: String {
        #if targetEnvironment(macCatalyst)
        return "Mac Catalyst"
        #elseif os(iOS)
        return "iOS"
        #else
        return "Apple platform"
        #endif
    }

    public static var systemVersion: String { ProcessInfo.processInfo.operatingSystemVersionString }
    public static var lowPowerModeEnabled: Bool { ProcessInfo.processInfo.isLowPowerModeEnabled }
    public static var thermalState: String {
        switch ProcessInfo.processInfo.thermalState {
        case .nominal: "nominal"
        case .fair: "fair"
        case .serious: "serious"
        case .critical: "critical"
        @unknown default: "unknown"
        }
    }
}
