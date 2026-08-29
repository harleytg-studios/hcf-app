import Foundation
import HCFCore

public struct HCFBanEntry: Codable, Sendable, Equatable {
    public var active: Bool
    public var banID: String?
    public var reason: String?
    public var createdAt: String?
    public var expiresAt: String?
    public var appealAllowed: Bool?
    public var notes: String?

    enum CodingKeys: String, CodingKey {
        case active, reason, notes
        case banID = "ban_id"
        case createdAt = "created_at"
        case expiresAt = "expires_at"
        case appealAllowed = "appeal_allowed"
    }

    public var isCurrentlyActive: Bool {
        guard active else { return false }
        guard let expiresAt, !expiresAt.isEmpty, let date = ISO8601DateFormatter().date(from: expiresAt) else { return true }
        return date > .now
    }
}

public struct HCFPublicBanList: Codable, Sendable {
    public var schemaVersion: Int
    public var updatedAt: String?
    public var users: [String: HCFBanEntry]
    public var ipSHA256: [String: HCFBanEntry]

    enum CodingKeys: String, CodingKey {
        case users
        case schemaVersion = "schema_version"
        case updatedAt = "updated_at"
        case ipSHA256 = "ip_sha256"
    }
}

public struct HCFBanSystemConfig: Sendable, Equatable {
    public var enabled = true
    public var failOpen = true
    public var refreshHours = 6
    public var banListURL = URL(string: "https://raw.githubusercontent.com/markhitchk/hcf-app/main/configs/ban-list.json")!
    public var primaryIPLookup = URL(string: "https://api.ipify.org?format=json")!
    public var fallbackIPLookup = URL(string: "https://ipinfo.io/json")!

    public static func parse(_ text: String) -> Self {
        var result = Self()
        var section = ""
        for raw in text.components(separatedBy: .newlines) {
            let line = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !line.isEmpty, !line.hasPrefix("#"), !line.hasPrefix(";") else { continue }
            if line.hasPrefix("["), line.hasSuffix("]") {
                section = String(line.dropFirst().dropLast()).lowercased(); continue
            }
            guard let index = line.firstIndex(of: "=") else { continue }
            let key = String(line[..<index]).trimmingCharacters(in: .whitespaces).lowercased()
            let value = String(line[line.index(after: index)...]).trimmingCharacters(in: .whitespaces)
            switch (section, key) {
            case ("config", "enabled"): result.enabled = bool(value, fallback: true)
            case ("config", "fail_mode"): result.failOpen = value.lowercased() == "open"
            case ("config", "config_refresh_hours"): result.refreshHours = max(1, Int(value) ?? 6)
            case ("ban_list", "url"): if let url = secureURL(value) { result.banListURL = url }
            case ("ip_lookup", "primary"): if let url = secureURL(value) { result.primaryIPLookup = url }
            case ("ip_lookup", "fallback"): if let url = secureURL(value) { result.fallbackIPLookup = url }
            default: break
            }
        }
        return result
    }

    private static func secureURL(_ value: String) -> URL? {
        guard let url = URL(string: value), url.scheme?.lowercased() == "https" else { return nil }
        return url
    }
    private static func bool(_ value: String, fallback: Bool) -> Bool {
        let v = value.lowercased()
        if ["1", "true", "yes", "on"].contains(v) { return true }
        if ["0", "false", "no", "off"].contains(v) { return false }
        return fallback
    }
}

public struct HCFPublicIP: Sendable, Equatable {
    public let address: String
    public let source: String
}

public struct HCFBanCheckResult: Sendable, Equatable {
    public enum Scope: String, Sendable { case none, username, ip }
    public let banned: Bool
    public let scope: Scope
    public let banID: String?
    public let reason: String?
    public let expiresAt: String?
    public let username: String?

    public static let allowed = Self(banned: false, scope: .none, banID: nil, reason: nil, expiresAt: nil, username: nil)
}

public actor HCFBanService {
    public static let shared = HCFBanService()
    private let http: HTTPClient
    private var config = HCFBanSystemConfig()
    private var configFetchedAt: Date?
    private var banList: HCFPublicBanList?
    private var banListFetchedAt: Date?
    private let bypassUsernames: Set<String>
    private let bypassIPHashes: Set<String>

    /// Production defaults contain no privileged hard-coded bypass. Internal test targets may
    /// inject bypasses explicitly; this avoids shipping a client-readable moderation escape hatch.
    public init(http: HTTPClient = .shared, bypassUsernames: Set<String> = [], bypassIPHashes: Set<String> = []) {
        self.http = http
        self.bypassUsernames = Set(bypassUsernames.map { $0.lowercased() })
        self.bypassIPHashes = Set(bypassIPHashes.map { $0.lowercased() })
    }

    public func checkAccess(identity: ForumIdentity) async -> HCFBanCheckResult {
        do {
            let config = try await currentConfig()
            guard config.enabled else { return .allowed }
            let list = try await currentList(config: config)
            let username = identity.isSignedIn ? normalizeUsername(identity.username) : ""
            if !username.isEmpty, !bypassUsernames.contains(username), let entry = list.users[username], entry.isCurrentlyActive {
                return .init(banned: true, scope: .username, banID: entry.banID, reason: entry.reason, expiresAt: entry.expiresAt, username: identity.username)
            }
            guard let publicIP = await lookupPublicIP(config: config) else {
                return config.failOpen ? .allowed : .init(banned: true, scope: .ip, banID: "HCF-IP-CHECK", reason: "Unable to verify network access.", expiresAt: nil, username: identity.username)
            }
            let hash = HCFHash.sha256Hex(publicIP.address).lowercased()
            if !bypassIPHashes.contains(hash), let entry = list.ipSHA256[hash], entry.isCurrentlyActive {
                return .init(banned: true, scope: .ip, banID: entry.banID, reason: entry.reason, expiresAt: entry.expiresAt, username: identity.username)
            }
            return .allowed
        } catch {
            await DiagnosticLogger.shared.warning("ban_check", error.localizedDescription)
            return config.failOpen ? .allowed : .init(banned: true, scope: .ip, banID: "HCF-BAN-CONFIG", reason: "Unable to verify access.", expiresAt: nil, username: identity.username)
        }
    }

    public func lookupPublicIP() async -> HCFPublicIP? {
        let config = (try? await currentConfig()) ?? self.config
        return await lookupPublicIP(config: config)
    }

    private func currentConfig() async throws -> HCFBanSystemConfig {
        if let fetched = configFetchedAt, Date().timeIntervalSince(fetched) < TimeInterval(config.refreshHours * 3600) { return config }
        let response = try await http.request(HCFBuildInfo.banConfigURL, headers: ["Accept": "text/plain"])
        guard let text = String(data: response.data, encoding: .utf8) else { throw HCFError.invalidData("Invalid ban configuration") }
        config = .parse(text)
        configFetchedAt = .now
        return config
    }

    private func currentList(config: HCFBanSystemConfig) async throws -> HCFPublicBanList {
        if let list = banList, let fetched = banListFetchedAt, Date().timeIntervalSince(fetched) < TimeInterval(config.refreshHours * 3600) { return list }
        let response = try await http.request(config.banListURL)
        let decoder = JSONDecoder()
        let list = try decoder.decode(HCFPublicBanList.self, from: response.data)
        banList = list
        banListFetchedAt = .now
        return list
    }

    private func lookupPublicIP(config: HCFBanSystemConfig) async -> HCFPublicIP? {
        if let ip = await lookup(config.primaryIPLookup, source: "ipify") { return ip }
        return await lookup(config.fallbackIPLookup, source: "IPinfo")
    }

    private func lookup(_ url: URL, source: String) async -> HCFPublicIP? {
        do {
            let response = try await http.request(url, headers: ["Accept": "application/json,text/plain;q=0.9"])
            var raw = String(data: response.data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if let object = try? JSONSerialization.jsonObject(with: response.data) as? [String: Any], let ip = object["ip"] as? String { raw = ip }
            guard let normalized = normalizeIP(raw) else { return nil }
            return .init(address: normalized, source: source)
        } catch { return nil }
    }

    private func normalizeUsername(_ value: String) -> String {
        value.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func normalizeIP(_ value: String) -> String? {
        var raw = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !raw.isEmpty, raw.count <= 64 else { return nil }
        if raw.hasPrefix("::ffff:") { raw = String(raw.dropFirst(7)) }
        if raw.contains(":") {
            let allowed = CharacterSet(charactersIn: "0123456789abcdefABCDEF:")
            guard raw.unicodeScalars.allSatisfy({ allowed.contains($0) }) else { return nil }
            return raw.lowercased()
        }
        let parts = raw.split(separator: ".")
        guard parts.count == 4, parts.allSatisfy({ Int($0).map { (0...255).contains($0) } ?? false }) else { return nil }
        return raw
    }
}

// MARK: - Security observation transport

public struct HCFObservationRecord: Codable, Sendable {
    public let schemaVersion: Int
    public let type: String
    public let username: String?
    public let publicIP: String
    public let ipSHA256: String
    public let ipSource: String
    public let observedAt: String
    public let visitorStatus: String
    public let observationCount: Int
    public let appVersion: String
    public let platform: String

    enum CodingKeys: String, CodingKey {
        case type, username, platform
        case schemaVersion = "schema_version"
        case publicIP = "ip"
        case ipSHA256 = "ip_sha256"
        case ipSource = "ip_source"
        case observedAt = "observed_at"
        case visitorStatus = "visitor_status"
        case observationCount = "observation_count"
        case appVersion = "app_version"
    }
}

public protocol HCFObservationUploader: Sendable {
    func upload(_ record: HCFObservationRecord) async throws
}

public struct HCFProxyObservationUploader: HCFObservationUploader {
    private let endpoint: URL
    private let http: HTTPClient

    public init(endpoint: URL, http: HTTPClient = .shared) throws {
        guard endpoint.scheme?.lowercased() == "https", endpoint.host != nil else { throw HCFError.untrustedURL(endpoint) }
        self.endpoint = endpoint; self.http = http
    }

    public func upload(_ record: HCFObservationRecord) async throws {
        let body = try JSONEncoder().encode(record)
        _ = try await http.request(endpoint, method: .post, headers: ["Content-Type": "application/json", "Accept": "application/json"], body: body)
    }
}

#if HCF_INTERNAL_DISTRIBUTION
public struct HCFDirectDiscordObservationUploader: HCFObservationUploader {
    private let webhook: URL
    public init(webhook: URL) throws {
        guard webhook.scheme?.lowercased() == "https",
              ["discord.com", "www.discord.com", "discordapp.com", "www.discordapp.com"].contains(webhook.host?.lowercased() ?? ""),
              webhook.path.hasPrefix("/api/webhooks/") else { throw HCFError.untrustedURL(webhook) }
        self.webhook = webhook
    }

    public func upload(_ record: HCFObservationRecord) async throws {
        let encoder = JSONEncoder(); encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let attachment = try encoder.encode(record)
        let boundary = "HCFDiscord-\(UUID().uuidString)"
        var body = Data()
        func append(_ text: String) { body.append(Data(text.utf8)) }
        let summary = "HCF iOS security observation: \(record.type) • visitor=\(record.visitorStatus) • ip_sha256=\(record.ipSHA256)"
        let payload = try JSONSerialization.data(withJSONObject: ["username": "HCF Ban Uplink", "content": summary])
        append("--\(boundary)\r\nContent-Disposition: form-data; name=\"payload_json\"\r\nContent-Type: application/json\r\n\r\n")
        body.append(payload); append("\r\n")
        append("--\(boundary)\r\nContent-Disposition: form-data; name=\"files[0]\"; filename=\"hcf-ios-observation.json\"\r\nContent-Type: application/json\r\n\r\n")
        body.append(attachment); append("\r\n--\(boundary)--\r\n")
        var request = URLRequest(url: webhook)
        request.httpMethod = "POST"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.httpBody = body
        let (_, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { throw HCFError.invalidResponse }
    }
}
#endif

public actor HCFDiscordObservationService {
    public static let shared = HCFDiscordObservationService()
    private let banService: HCFBanService
    private let defaults: UserDefaults
    private let touchInterval: TimeInterval = 6 * 3600

    public init(banService: HCFBanService = .shared) {
        self.banService = banService
        self.defaults = UserDefaults(suiteName: HCFBuildInfo.appGroupID) ?? .standard
    }

    public func observe(identity: ForumIdentity) async {
        guard let uploader = uploader() else {
            await DiagnosticLogger.shared.info("discord_observation", "secure uploader not configured; skipped")
            return
        }
        guard let ip = await banService.lookupPublicIP() else {
            await DiagnosticLogger.shared.warning("discord_observation", "public IP unavailable")
            return
        }
        let hash = HCFHash.sha256Hex(ip.address)
        let subject = identity.isSignedIn && !identity.username.isEmpty ? "user:\(safe(identity.username.lowercased()))" : "guest:\(hash)"
        let touchKey = "ios_observation_touch_\(subject)_\(hash)"
        let now = Date()
        let last = defaults.object(forKey: touchKey) as? Date
        if let last, now.timeIntervalSince(last) < touchInterval { return }
        let countKey = "ios_observation_count_\(subject)"
        let previous = defaults.integer(forKey: countKey)
        let formatter = ISO8601DateFormatter()
        let record = HCFObservationRecord(
            schemaVersion: 1,
            type: identity.isSignedIn ? "user" : "guest",
            username: identity.isSignedIn ? identity.username : nil,
            publicIP: ip.address,
            ipSHA256: hash,
            ipSource: ip.source,
            observedAt: formatter.string(from: now),
            visitorStatus: previous > 0 ? "returning" : "new",
            observationCount: previous + 1,
            appVersion: HCFBuildInfo.displayVersion,
            platform: "iOS"
        )
        do {
            try await uploader.upload(record)
            defaults.set(now, forKey: touchKey)
            defaults.set(previous + 1, forKey: countKey)
            await DiagnosticLogger.shared.info("discord_observation", "delivered • \(record.visitorStatus)")
        } catch {
            await DiagnosticLogger.shared.warning("discord_observation", error.localizedDescription)
        }
    }

    private func uploader() -> (any HCFObservationUploader)? {
        if let endpoint = HCFBuildInfo.observationProxyURL,
           let proxy = try? HCFProxyObservationUploader(endpoint: endpoint) { return proxy }
        #if HCF_INTERNAL_DISTRIBUTION
        if let raw = Bundle.main.object(forInfoDictionaryKey: "HCFDiscordWebhookURL") as? String,
           let url = URL(string: raw), let direct = try? HCFDirectDiscordObservationUploader(webhook: url) { return direct }
        #endif
        return nil
    }

    private func safe(_ value: String) -> String {
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "._-"))
        return String(value.unicodeScalars.map { allowed.contains($0) ? Character(String($0)) : "-" }.prefix(80))
    }
}
