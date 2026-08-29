import Foundation
import Security
import CryptoKit
import Combine

// MARK: - Build identity

public enum HCFBuildInfo {
    private static func value(_ key: String, fallback: String) -> String {
        let value = Bundle.main.object(forInfoDictionaryKey: key) as? String
        return value?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false ? value! : fallback
    }

    public static var channelVersion: String { value("HCFChannelVersion", fallback: "1.1-hf2-a1") }
    public static var channelTag: String { value("HCFChannelTag", fallback: "v1.1-hf2-a1") }
    public static var buildNumber: Int { Int(value("HCFBuildNumber", fallback: "100000105")) ?? 100000105 }
    public static var marketingVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.1.0"
    }
    public static var releaseChannel: String { value("HCFReleaseChannel", fallback: "dev") }
    public static var brand: String { "Harley's Studios" }
    public static var buildLabel: String { "Beta / Development Build" }
    public static var displayVersion: String { "v\(channelVersion) (\(buildNumber)) • \(buildLabel)" }
    public static var userAgentMarker: String { "HarleysClanForumApp/\(channelVersion) Build/\(buildNumber) iOS" }
    public static var repository: String { "markhitchk/hcf-app" }
    public static var releasesURL: URL { URL(string: "https://api.github.com/repos/markhitchk/hcf-app/releases?per_page=30")! }
    public static var remoteDomainsURL: URL { URL(string: "https://raw.githubusercontent.com/markhitchk/hcf-app/main/configs/domains.config")! }
    public static var banConfigURL: URL { URL(string: "https://raw.githubusercontent.com/markhitchk/hcf-app/main/configs/ban-system.config")! }
    public static var appGroupID: String { value("HCFAppGroup", fallback: "group.com.harleytg.forum.dev") }
    public static var primaryHost: String { value("HCFPrimaryHost", fallback: "forum.harleytg.com") }
    public static var backupHost: String { value("HCFBackupHost", fallback: "harleysclan.freeflarum.com") }
    public static var testFlightURL: URL? {
        let raw = value("HCFTestFlightURL", fallback: "")
        return raw.isEmpty ? nil : URL(string: raw)
    }
    public static var observationProxyURL: URL? {
        let raw = value("HCFObservationProxyURL", fallback: "")
        return raw.isEmpty ? nil : URL(string: raw)
    }
}

// MARK: - Shared models

public enum HCFThemeMode: String, Codable, CaseIterable, Sendable, Identifiable {
    case system
    case light
    case dark
    case amoled
    case followForum = "follow_forum"

    public var id: String { rawValue }
    public var label: String {
        switch self {
        case .system: "System"
        case .light: "Light"
        case .dark: "Dark"
        case .amoled: "AMOLED"
        case .followForum: "Follow HCF forum theme"
        }
    }
}

public enum HCFWidgetTapAction: String, Codable, CaseIterable, Sendable, Identifiable {
    case forum, notifications, settings, latest, profile
    public var id: String { rawValue }
    public var label: String { rawValue.capitalized }
}

public enum HCFNotificationHistoryMode: String, Codable, CaseIterable, Sendable, Identifiable {
    case off, title, full
    public var id: String { rawValue }
    public var label: String {
        switch self {
        case .off: "Off"
        case .title: "Titles only"
        case .full: "Titles + message"
        }
    }
}

public struct ForumIdentity: Codable, Sendable, Equatable {
    public var id: String
    public var username: String
    public var displayName: String
    public var avatarURL: URL?
    public var isAdmin: Bool
    public var isSignedIn: Bool
    public var host: String

    public init(
        id: String = "",
        username: String = "",
        displayName: String = "Guest",
        avatarURL: URL? = nil,
        isAdmin: Bool = false,
        isSignedIn: Bool = false,
        host: String = HCFBuildInfo.primaryHost
    ) {
        self.id = id
        self.username = username
        self.displayName = displayName
        self.avatarURL = avatarURL
        self.isAdmin = isAdmin
        self.isSignedIn = isSignedIn
        self.host = host
    }

    public static func guest(host: String = HCFBuildInfo.primaryHost) -> Self {
        .init(host: host)
    }
}

public struct HCFNotificationItem: Codable, Sendable, Identifiable, Hashable {
    public var id: String
    public var title: String
    public var body: String
    public var url: URL?
    public var conversationID: String?
    public var discussionID: String?
    public var actorAvatarURL: URL?
    public var isRead: Bool
    public var replyCapable: Bool
    public var createdAt: Date?

    public init(
        id: String,
        title: String,
        body: String,
        url: URL? = nil,
        conversationID: String? = nil,
        discussionID: String? = nil,
        actorAvatarURL: URL? = nil,
        isRead: Bool = false,
        replyCapable: Bool = false,
        createdAt: Date? = nil
    ) {
        self.id = id
        self.title = title
        self.body = body
        self.url = url
        self.conversationID = conversationID
        self.discussionID = discussionID
        self.actorAvatarURL = actorAvatarURL
        self.isRead = isRead
        self.replyCapable = replyCapable
        self.createdAt = createdAt
    }
}

public struct WidgetNotificationPreview: Codable, Sendable, Equatable {
    public var title: String
    public var body: String
    public var url: URL?
    public var date: Date

    public init(title: String, body: String, url: URL?, date: Date = .now) {
        self.title = title
        self.body = body
        self.url = url
        self.date = date
    }
}

public struct WidgetSnapshot: Codable, Sendable, Equatable {
    public var unreadCount: Int
    public var username: String
    public var lastUpdated: Date?
    public var lastNotification: WidgetNotificationPreview?
    public var theme: HCFThemeMode
    public var backgroundAlpha: Int
    public var textSize: Int
    public var compact: Bool
    public var showUsername: Bool
    public var showUnread: Bool
    public var showLastUpdated: Bool
    public var showPreview: Bool
    public var defaultTap: HCFWidgetTapAction

    public init(
        unreadCount: Int = 0,
        username: String = "",
        lastUpdated: Date? = nil,
        lastNotification: WidgetNotificationPreview? = nil,
        theme: HCFThemeMode = .system,
        backgroundAlpha: Int = 96,
        textSize: Int = 12,
        compact: Bool = false,
        showUsername: Bool = true,
        showUnread: Bool = true,
        showLastUpdated: Bool = true,
        showPreview: Bool = true,
        defaultTap: HCFWidgetTapAction = .forum
    ) {
        self.unreadCount = unreadCount
        self.username = username
        self.lastUpdated = lastUpdated
        self.lastNotification = lastNotification
        self.theme = theme
        self.backgroundAlpha = min(100, max(20, backgroundAlpha))
        self.textSize = min(18, max(10, textSize))
        self.compact = compact
        self.showUsername = showUsername
        self.showUnread = showUnread
        self.showLastUpdated = showLastUpdated
        self.showPreview = showPreview
        self.defaultTap = defaultTap
    }
}

public struct HCFSettingsArchive: Codable, Sendable {
    public static let schemaVersion = 1
    public var schemaVersion: Int
    public var exportedAt: Date
    public var appVersion: String
    public var preferences: [String: HCFPreferenceValue]

    public init(preferences: [String: HCFPreferenceValue]) {
        schemaVersion = Self.schemaVersion
        exportedAt = .now
        appVersion = HCFBuildInfo.displayVersion
        self.preferences = preferences
    }
}

public enum HCFPreferenceValue: Codable, Sendable, Equatable {
    case string(String)
    case bool(Bool)
    case integer(Int)
    case double(Double)

    private enum CodingKeys: String, CodingKey { case type, string, bool, integer, double }
    private enum Kind: String, Codable { case string, bool, integer, double }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        switch try c.decode(Kind.self, forKey: .type) {
        case .string: self = .string(try c.decode(String.self, forKey: .string))
        case .bool: self = .bool(try c.decode(Bool.self, forKey: .bool))
        case .integer: self = .integer(try c.decode(Int.self, forKey: .integer))
        case .double: self = .double(try c.decode(Double.self, forKey: .double))
        }
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .string(let v): try c.encode(Kind.string, forKey: .type); try c.encode(v, forKey: .string)
        case .bool(let v): try c.encode(Kind.bool, forKey: .type); try c.encode(v, forKey: .bool)
        case .integer(let v): try c.encode(Kind.integer, forKey: .type); try c.encode(v, forKey: .integer)
        case .double(let v): try c.encode(Kind.double, forKey: .type); try c.encode(v, forKey: .double)
        }
    }
}

// MARK: - Preferences

@MainActor
public final class PreferencesStore: ObservableObject {
    public enum Key {
        public static let appTheme = "app_theme"
        public static let forumAutoTheme = "forum_auto_theme"
        public static let activeHost = "active_host"
        public static let autoFailover = "auto_failover"
        public static let backgroundNotificationSync = "background_notification_sync"
        public static let externalLinks = "external_links"
        public static let sessionUserID = "session_user_id"
        public static let lastNotificationCount = "last_notification_count"
        public static let compactHeader = "compact_header"
        public static let desktopMode = "desktop_mode"
        public static let setupCompleted = "setup_completed_ios"
        public static let welcomeSeen = "welcome_seen_ios"
        public static let widgetFollowTheme = "widget_follow_app_theme"
        public static let widgetShowUsername = "widget_show_connected_username"
        public static let widgetShowUnread = "widget_show_unread_count"
        public static let widgetCompact = "widget_compact_mode"
        public static let widgetShowUpdated = "widget_show_last_updated"
        public static let widgetDefaultTap = "widget_default_tap_action"
        public static let widgetBackgroundAlpha = "widget_background_alpha"
        public static let widgetTextSize = "widget_text_size_sp"
        public static let widgetRefreshInterval = "widget_refresh_interval_min"
        public static let widgetShowPreview = "widget_show_last_notification_preview"
        public static let historyMode = "native_notification_history_mode"
        public static let historyLimit = "native_notification_history_limit"
    }

    public static let transferableKeys: Set<String> = [
        Key.appTheme, Key.forumAutoTheme, Key.activeHost, Key.autoFailover,
        Key.backgroundNotificationSync, Key.externalLinks, Key.compactHeader,
        Key.desktopMode, Key.widgetFollowTheme, Key.widgetShowUsername,
        Key.widgetShowUnread, Key.widgetCompact, Key.widgetShowUpdated,
        Key.widgetDefaultTap, Key.widgetBackgroundAlpha, Key.widgetTextSize,
        Key.widgetRefreshInterval, Key.widgetShowPreview, Key.historyMode, Key.historyLimit
    ]

    public let defaults: UserDefaults
    private var cancellables = Set<AnyCancellable>()

    @Published public var theme: HCFThemeMode
    @Published public var activeHost: String
    @Published public var autoFailover: Bool
    @Published public var backgroundNotificationSync: Bool
    @Published public var unreadCount: Int

    public init(suiteName: String = HCFBuildInfo.appGroupID) {
        defaults = UserDefaults(suiteName: suiteName) ?? .standard
        theme = HCFThemeMode(rawValue: defaults.string(forKey: Key.appTheme) ?? "system") ?? .system
        activeHost = defaults.string(forKey: Key.activeHost) ?? HCFBuildInfo.primaryHost
        autoFailover = defaults.object(forKey: Key.autoFailover) as? Bool ?? true
        backgroundNotificationSync = defaults.object(forKey: Key.backgroundNotificationSync) as? Bool ?? true
        unreadCount = defaults.integer(forKey: Key.lastNotificationCount)

        $theme.dropFirst().sink { [weak self] in self?.defaults.set($0.rawValue, forKey: Key.appTheme) }.store(in: &cancellables)
        $activeHost.dropFirst().sink { [weak self] in self?.defaults.set($0, forKey: Key.activeHost) }.store(in: &cancellables)
        $autoFailover.dropFirst().sink { [weak self] in self?.defaults.set($0, forKey: Key.autoFailover) }.store(in: &cancellables)
        $backgroundNotificationSync.dropFirst().sink { [weak self] in self?.defaults.set($0, forKey: Key.backgroundNotificationSync) }.store(in: &cancellables)
        $unreadCount.dropFirst().sink { [weak self] in self?.defaults.set(max(0, $0), forKey: Key.lastNotificationCount) }.store(in: &cancellables)
    }

    public func value<T>(forKey key: String, default fallback: T) -> T {
        defaults.object(forKey: key) as? T ?? fallback
    }

    public func set(_ value: Any?, forKey key: String) { defaults.set(value, forKey: key) }

    public func exportArchive() -> HCFSettingsArchive {
        var output: [String: HCFPreferenceValue] = [:]
        for key in Self.transferableKeys {
            guard let value = defaults.object(forKey: key) else { continue }
            if let value = value as? String { output[key] = .string(value) }
            else if let value = value as? Bool { output[key] = .bool(value) }
            else if let value = value as? Int { output[key] = .integer(value) }
            else if let value = value as? Double { output[key] = .double(value) }
        }
        return HCFSettingsArchive(preferences: output)
    }

    public func importArchive(_ archive: HCFSettingsArchive) throws {
        guard archive.schemaVersion == HCFSettingsArchive.schemaVersion else {
            throw HCFError.invalidData("Unsupported settings schema \(archive.schemaVersion)")
        }
        for (key, value) in archive.preferences where Self.transferableKeys.contains(key) {
            switch value {
            case .string(let v): defaults.set(v, forKey: key)
            case .bool(let v): defaults.set(v, forKey: key)
            case .integer(let v): defaults.set(v, forKey: key)
            case .double(let v): defaults.set(v, forKey: key)
            }
        }
        theme = HCFThemeMode(rawValue: defaults.string(forKey: Key.appTheme) ?? "system") ?? .system
        activeHost = defaults.string(forKey: Key.activeHost) ?? HCFBuildInfo.primaryHost
        autoFailover = defaults.object(forKey: Key.autoFailover) as? Bool ?? true
        backgroundNotificationSync = defaults.object(forKey: Key.backgroundNotificationSync) as? Bool ?? true
        unreadCount = defaults.integer(forKey: Key.lastNotificationCount)
    }
}

// MARK: - App Group cache

public actor SharedContainerStore {
    public static let shared = SharedContainerStore()
    private let defaults: UserDefaults
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    private let widgetSnapshotKey = "ios_widget_snapshot_v1"
    private let identityKey = "ios_forum_identity_v1"

    public init(suiteName: String = HCFBuildInfo.appGroupID) {
        defaults = UserDefaults(suiteName: suiteName) ?? .standard
        encoder.dateEncodingStrategy = .iso8601
        decoder.dateDecodingStrategy = .iso8601
    }

    public func saveWidgetSnapshot(_ snapshot: WidgetSnapshot) throws {
        defaults.set(try encoder.encode(snapshot), forKey: widgetSnapshotKey)
    }

    public func widgetSnapshot() -> WidgetSnapshot {
        guard let data = defaults.data(forKey: widgetSnapshotKey),
              let value = try? decoder.decode(WidgetSnapshot.self, from: data) else { return .init() }
        return value
    }

    public func saveIdentity(_ identity: ForumIdentity) throws {
        defaults.set(try encoder.encode(identity), forKey: identityKey)
    }

    public func identity() -> ForumIdentity {
        guard let data = defaults.data(forKey: identityKey),
              let value = try? decoder.decode(ForumIdentity.self, from: data) else { return .guest() }
        return value
    }
}

// MARK: - Keychain

public actor KeychainStore {
    public static let shared = KeychainStore(service: "com.harleytg.forum.dev.session")
    private let service: String

    public init(service: String) { self.service = service }

    public func set(_ data: Data, account: String) throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
        var add = query
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let status = SecItemAdd(add as CFDictionary, nil)
        guard status == errSecSuccess else { throw HCFError.keychain(status) }
    }

    public func data(account: String) throws -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess else { throw HCFError.keychain(status) }
        return item as? Data
    }

    public func setString(_ value: String, account: String) throws {
        try set(Data(value.utf8), account: account)
    }

    public func string(account: String) throws -> String? {
        guard let data = try data(account: account) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    public func delete(account: String) throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw HCFError.keychain(status) }
    }
}

// MARK: - Networking

public enum HCFHTTPMethod: String, Sendable { case get = "GET", post = "POST", patch = "PATCH", delete = "DELETE" }

public struct HCFHTTPResponse: Sendable {
    public let data: Data
    public let statusCode: Int
    public let headers: [String: String]
}

public actor HTTPClient {
    public static let shared = HTTPClient()
    private let session: URLSession

    public init(configuration: URLSessionConfiguration = .default) {
        configuration.timeoutIntervalForRequest = 15
        configuration.timeoutIntervalForResource = 30
        configuration.waitsForConnectivity = true
        configuration.httpAdditionalHeaders = [
            "Accept": "application/json",
            "User-Agent": HCFBuildInfo.userAgentMarker
        ]
        session = URLSession(configuration: configuration)
    }

    public func request(
        _ url: URL,
        method: HCFHTTPMethod = .get,
        headers: [String: String] = [:],
        body: Data? = nil,
        acceptedStatus: Range<Int> = 200..<300
    ) async throws -> HCFHTTPResponse {
        guard url.scheme?.lowercased() == "https" else { throw HCFError.untrustedURL(url) }
        var request = URLRequest(url: url)
        request.httpMethod = method.rawValue
        request.httpBody = body
        request.cachePolicy = .reloadRevalidatingCacheData
        headers.forEach { request.setValue($1, forHTTPHeaderField: $0) }
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw HCFError.invalidResponse }
        guard acceptedStatus.contains(http.statusCode) else {
            throw HCFError.httpStatus(http.statusCode, retryAfter: Self.retryAfter(http))
        }
        var output: [String: String] = [:]
        for (key, value) in http.allHeaderFields {
            if let key = key as? String { output[key] = String(describing: value) }
        }
        return .init(data: data, statusCode: http.statusCode, headers: output)
    }

    public func decode<T: Decodable & Sendable>(_ type: T.Type, from url: URL, decoder: JSONDecoder = .init()) async throws -> T {
        let response = try await request(url)
        do { return try decoder.decode(type, from: response.data) }
        catch { throw HCFError.invalidData("JSON decode failed: \(String(describing: error))") }
    }

    private static func retryAfter(_ response: HTTPURLResponse) -> TimeInterval? {
        guard let raw = response.value(forHTTPHeaderField: "Retry-After") else { return nil }
        if let seconds = TimeInterval(raw) { return seconds }
        return nil
    }
}

// MARK: - Diagnostics

public struct DiagnosticEvent: Codable, Sendable, Identifiable {
    public let id: UUID
    public let date: Date
    public let level: String
    public let code: String
    public let detail: String

    public init(level: String, code: String, detail: String) {
        id = UUID(); date = .now; self.level = level; self.code = code
        self.detail = DiagnosticSanitizer.clean(detail)
    }
}

public enum DiagnosticSanitizer {
    public static func clean(_ input: String) -> String {
        var value = String(input.prefix(600))
        value = value.replacingOccurrences(of: #"(?i)(authorization|cookie|token|password|secret|webhook)\s*[:=]\s*[^\s,;]+"#, with: "$1=[REDACTED]", options: .regularExpression)
        value = value.replacingOccurrences(of: #"https://[^\s?]+\?[^\s]+"#, with: "[URL QUERY REDACTED]", options: .regularExpression)
        value = value.replacingOccurrences(of: #"\b[A-Fa-f0-9]{32,}\b"#, with: "[HASH/TOKEN REDACTED]", options: .regularExpression)
        return value
    }
}

public actor DiagnosticLogger {
    public static let shared = DiagnosticLogger()
    private var events: [DiagnosticEvent] = []
    private let maxCount = 250

    public func info(_ code: String, _ detail: String = "") { append(.init(level: "INFO", code: code, detail: detail)) }
    public func warning(_ code: String, _ detail: String = "") { append(.init(level: "WARN", code: code, detail: detail)) }
    public func error(_ code: String, _ detail: String = "") { append(.init(level: "ERROR", code: code, detail: detail)) }
    public func snapshot() -> [DiagnosticEvent] { events }
    public func clear() { events.removeAll(keepingCapacity: true) }

    private func append(_ event: DiagnosticEvent) {
        events.append(event)
        if events.count > maxCount { events.removeFirst(events.count - maxCount) }
    }
}

// MARK: - Hashing and errors

public enum HCFHash {
    public static func sha256Hex(_ value: String) -> String {
        SHA256.hash(data: Data(value.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}

public enum HCFError: LocalizedError, Sendable {
    case untrustedURL(URL)
    case invalidResponse
    case invalidData(String)
    case httpStatus(Int, retryAfter: TimeInterval?)
    case keychain(OSStatus)
    case notAuthenticated
    case unsupported(String)

    public var errorDescription: String? {
        switch self {
        case .untrustedURL(let url): "Untrusted or insecure URL: \(url.absoluteString)"
        case .invalidResponse: "The server returned an invalid response."
        case .invalidData(let detail): detail
        case .httpStatus(let code, _): "Server returned HTTP \(code)."
        case .keychain(let status): "Keychain error \(status)."
        case .notAuthenticated: "The forum session is not signed in."
        case .unsupported(let detail): detail
        }
    }
}
