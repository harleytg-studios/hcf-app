import Foundation
import HCFCore

public struct HCFDomainRegistry: Sendable, Equatable {
    public var primary: String
    public var backups: [String]
    public var httpsOnly: Bool
    public var preservePath: Bool
    public var preserveQuery: Bool
    public var preserveFragment: Bool

    public init(
        primary: String = HCFBuildInfo.primaryHost,
        backups: [String] = [HCFBuildInfo.backupHost],
        httpsOnly: Bool = true,
        preservePath: Bool = true,
        preserveQuery: Bool = true,
        preserveFragment: Bool = true
    ) {
        self.primary = primary.lowercased()
        self.backups = backups.map { $0.lowercased() }
        self.httpsOnly = httpsOnly
        self.preservePath = preservePath
        self.preserveQuery = preserveQuery
        self.preserveFragment = preserveFragment
    }

    public var trustedHosts: Set<String> { Set([primary] + backups) }
    public func contains(_ host: String?) -> Bool { host.map { trustedHosts.contains($0.lowercased()) } ?? false }
}

public actor DomainRegistryService {
    public static let shared = DomainRegistryService()
    private let client: HTTPClient
    private var cached = HCFDomainRegistry()
    private var lastRefresh: Date?

    public init(client: HTTPClient = .shared) { self.client = client }

    public func registry(force: Bool = false) async -> HCFDomainRegistry {
        if !force, let lastRefresh, Date().timeIntervalSince(lastRefresh) < 6 * 3600 { return cached }
        do {
            let response = try await client.request(HCFBuildInfo.remoteDomainsURL)
            guard let text = String(data: response.data, encoding: .utf8) else { return cached }
            cached = Self.parse(text, fallback: cached)
            lastRefresh = .now
            await DiagnosticLogger.shared.info("domain_registry", "loaded primary=\(cached.primary) backups=\(cached.backups.count)")
        } catch {
            await DiagnosticLogger.shared.warning("domain_registry", "fallback • \(error.localizedDescription)")
        }
        return cached
    }

    public static func parse(_ source: String, fallback: HCFDomainRegistry = .init()) -> HCFDomainRegistry {
        var section = ""
        var values: [String: [String: String]] = [:]
        for rawLine in source.components(separatedBy: .newlines) {
            let line = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !line.isEmpty, !line.hasPrefix("#"), !line.hasPrefix(";") else { continue }
            if line.hasPrefix("["), line.hasSuffix("]") {
                section = String(line.dropFirst().dropLast()).lowercased()
                continue
            }
            guard let split = line.firstIndex(of: "=") else { continue }
            let key = String(line[..<split]).trimmingCharacters(in: .whitespaces).lowercased()
            let value = String(line[line.index(after: split)...]).trimmingCharacters(in: .whitespaces)
            values[section, default: [:]][key] = value
        }
        let primary = values["primary"]?["domain"]?.lowercased() ?? fallback.primary
        let backupPairs = values["backups"] ?? [:]
        let backups = backupPairs
            .filter { $0.key.hasPrefix("domain_") }
            .sorted { $0.key < $1.key }
            .map { $0.value.lowercased() }
            .filter { !$0.isEmpty }
        let config = values["config"] ?? [:]
        func yes(_ key: String, _ defaultValue: Bool) -> Bool {
            guard let raw = config[key]?.lowercased() else { return defaultValue }
            return ["1", "true", "yes", "on"].contains(raw)
        }
        return .init(
            primary: primary,
            backups: backups.isEmpty ? fallback.backups : backups,
            httpsOnly: yes("https_only", true),
            preservePath: yes("preserve_path", true),
            preserveQuery: yes("preserve_query", true),
            preserveFragment: yes("preserve_fragment", true)
        )
    }
}

public enum ForumRoute: Hashable, Sendable {
    case home
    case latest
    case compose
    case notifications
    case profile(username: String?)
    case settings
    case url(URL)

    public var path: String {
        switch self {
        case .home: "/"
        case .latest: "/all"
        case .compose: "/compose"
        case .notifications: "/notifications"
        case .profile(let username): username.map { "/u/\($0)" } ?? "/settings"
        case .settings: "/settings"
        case .url(let url): url.path.isEmpty ? "/" : url.path
        }
    }
}

public struct ForumRouter: Sendable {
    public var registry: HCFDomainRegistry

    public init(registry: HCFDomainRegistry = .init()) { self.registry = registry }

    public func isTrusted(_ url: URL?) -> Bool {
        guard let url, url.scheme?.lowercased() == "https" else { return false }
        return registry.contains(url.host)
    }

    public func home(host: String? = nil) -> URL {
        URL(string: "https://\(validatedHost(host))/")!
    }

    public func url(for route: ForumRoute, host: String? = nil) -> URL {
        if case .url(let original) = route, isTrusted(original) { return original }
        let safeHost = validatedHost(host)
        var components = URLComponents()
        components.scheme = "https"
        components.host = safeHost
        components.path = route.path
        return components.url ?? home(host: safeHost)
    }

    public func equivalent(_ original: URL, on host: String) -> URL {
        let safeHost = validatedHost(host)
        guard var components = URLComponents(url: original, resolvingAgainstBaseURL: false) else { return home(host: safeHost) }
        components.scheme = "https"
        components.host = safeHost
        if !registry.preservePath { components.path = "/" }
        if !registry.preserveQuery { components.query = nil }
        if !registry.preserveFragment { components.fragment = nil }
        return components.url ?? home(host: safeHost)
    }

    public func route(fromIncoming url: URL) -> URL? {
        if url.scheme?.lowercased() == "hcf" {
            switch url.host?.lowercased() {
            case "forum": return self.url(for: .home)
            case "notifications": return self.url(for: .notifications)
            case "latest": return self.url(for: .latest)
            case "profile": return self.url(for: .profile(username: nil))
            case "settings": return nil
            case "widget":
                let action = url.pathComponents.dropFirst().first?.lowercased()
                if action == "reload" { return self.url(for: .notifications) }
                return self.url(for: .home)
            default: return self.url(for: .home)
            }
        }
        return isTrusted(url) ? url : nil
    }

    public func validatedHost(_ host: String?) -> String {
        let normalized = host?.lowercased().trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return registry.trustedHosts.contains(normalized) ? normalized : registry.primary
    }
}
