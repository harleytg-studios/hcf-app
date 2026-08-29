import Foundation
import WebKit
import HCFCore

// MARK: - WebKit session persistence

private struct HCFCookieRecord: Codable, Sendable {
    let name: String
    let value: String
    let domain: String
    let path: String
    let expires: Date?
    let secure: Bool

    init?(_ cookie: HTTPCookie, trustedHosts: Set<String>) {
        let normalizedDomain = cookie.domain.trimmingCharacters(in: CharacterSet(charactersIn: ".")).lowercased()
        guard trustedHosts.contains(normalizedDomain) else { return nil }
        name = cookie.name
        value = cookie.value
        domain = cookie.domain
        path = cookie.path
        expires = cookie.expiresDate
        secure = cookie.isSecure
    }

    var cookie: HTTPCookie? {
        var properties: [HTTPCookiePropertyKey: Any] = [
            .name: name,
            .value: value,
            .domain: domain,
            .path: path,
            .secure: secure ? "TRUE" : "FALSE"
        ]
        if let expires { properties[.expires] = expires }
        return HTTPCookie(properties: properties)
    }
}

@MainActor
public final class ForumSessionManager {
    public static let shared = ForumSessionManager()

    private let keychain: KeychainStore
    private let cookieAccount = "trusted-webkit-cookies-v1"
    private let websiteDataStore: WKWebsiteDataStore
    private var trustedHosts: Set<String>

    public init(
        keychain: KeychainStore = .shared,
        websiteDataStore: WKWebsiteDataStore = .default(),
        trustedHosts: Set<String> = [HCFBuildInfo.primaryHost, HCFBuildInfo.backupHost]
    ) {
        self.keychain = keychain
        self.websiteDataStore = websiteDataStore
        self.trustedHosts = trustedHosts
    }

    public func updateTrustedHosts(_ hosts: Set<String>) { trustedHosts = hosts }

    public func restoreCookies() async {
        do {
            guard let data = try await keychain.data(account: cookieAccount) else { return }
            let records = try JSONDecoder().decode([HCFCookieRecord].self, from: data)
            for record in records {
                guard let cookie = record.cookie else { continue }
                await set(cookie)
            }
            await DiagnosticLogger.shared.info("session_restore", "restored \(records.count) trusted cookies")
        } catch {
            await DiagnosticLogger.shared.warning("session_restore", error.localizedDescription)
        }
    }

    public func persistCookies() async {
        let cookies = await allCookies()
        let records = cookies.compactMap { HCFCookieRecord($0, trustedHosts: trustedHosts) }
        do {
            let data = try JSONEncoder().encode(records)
            try await keychain.set(data, account: cookieAccount)
            await DiagnosticLogger.shared.info("session_persist", "stored \(records.count) trusted cookies")
        } catch {
            await DiagnosticLogger.shared.warning("session_persist", error.localizedDescription)
        }
    }

    public func clearSession() async {
        let cookies = await allCookies()
        for cookie in cookies {
            let domain = cookie.domain.trimmingCharacters(in: CharacterSet(charactersIn: ".")).lowercased()
            if trustedHosts.contains(domain) { await delete(cookie) }
        }
        try? await keychain.delete(account: cookieAccount)
        try? await SharedContainerStore.shared.saveIdentity(.guest())
        let defaults = UserDefaults(suiteName: HCFBuildInfo.appGroupID) ?? .standard
        defaults.removeObject(forKey: PreferencesStore.Key.sessionUserID)
        defaults.removeObject(forKey: PreferencesStore.Key.lastNotificationCount)
        await DiagnosticLogger.shared.info("session_clear", "trusted forum session cleared")
    }

    public func cookieHeader(for host: String) async -> String {
        let normalized = host.lowercased()
        guard trustedHosts.contains(normalized) else { return "" }
        let cookies = await allCookies().filter {
            $0.domain.trimmingCharacters(in: CharacterSet(charactersIn: ".")).lowercased() == normalized
                && ($0.expiresDate == nil || $0.expiresDate! > .now)
        }
        return cookies.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
    }

    public func saveIdentity(_ identity: ForumIdentity) async {
        let defaults = UserDefaults(suiteName: HCFBuildInfo.appGroupID) ?? .standard
        if identity.isSignedIn, !identity.id.isEmpty {
            defaults.set(identity.id, forKey: PreferencesStore.Key.sessionUserID)
        } else {
            defaults.removeObject(forKey: PreferencesStore.Key.sessionUserID)
            defaults.removeObject(forKey: PreferencesStore.Key.lastNotificationCount)
        }
        try? await SharedContainerStore.shared.saveIdentity(identity)
    }

    public func currentIdentity() async -> ForumIdentity { await SharedContainerStore.shared.identity() }

    private func allCookies() async -> [HTTPCookie] {
        await withCheckedContinuation { continuation in
            websiteDataStore.httpCookieStore.getAllCookies { continuation.resume(returning: $0) }
        }
    }

    private func set(_ cookie: HTTPCookie) async {
        await withCheckedContinuation { continuation in
            websiteDataStore.httpCookieStore.setCookie(cookie) { continuation.resume() }
        }
    }

    private func delete(_ cookie: HTTPCookie) async {
        await withCheckedContinuation { continuation in
            websiteDataStore.httpCookieStore.delete(cookie) { continuation.resume() }
        }
    }
}

// MARK: - Flarum API parity

public actor ForumAPIClient {
    public static let shared = ForumAPIClient()
    private let http: HTTPClient
    private let session: ForumSessionManager

    public init(http: HTTPClient = .shared, session: ForumSessionManager = .shared) {
        self.http = http
        self.session = session
    }

    public func unreadCount(host: String, userID: String) async throws -> Int {
        guard userID.allSatisfy(\.isNumber), !userID.isEmpty else { throw HCFError.notAuthenticated }
        let base = try trustedBase(host)
        var components = URLComponents(url: base.appending(path: "api/users/\(userID)"), resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: "fields[users]", value: "unreadNotificationCount,newNotificationCount")]
        guard let url = components.url else { throw HCFError.invalidResponse }
        let response = try await authenticatedRequest(url, host: host)
        let object = try jsonObject(response.data)
        let attributes = ((object["data"] as? [String: Any])?["attributes"] as? [String: Any]) ?? [:]
        let unread = intValue(attributes["unreadNotificationCount"])
        let new = intValue(attributes["newNotificationCount"])
        return max(0, max(unread, new))
    }

    public func latestNotifications(host: String, limit: Int) async throws -> [HCFNotificationItem] {
        let base = try trustedBase(host)
        let safeLimit = max(1, min(20, limit))
        var components = URLComponents(url: base.appending(path: "api/notifications"), resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "include", value: "fromUser,subject"),
            URLQueryItem(name: "page[limit]", value: String(safeLimit))
        ]
        guard let url = components.url else { throw HCFError.invalidResponse }
        do {
            let response = try await authenticatedRequest(url, host: host)
            return try parseNotifications(response.data, baseURL: base, limit: safeLimit)
        } catch let HCFError.httpStatus(code, retryAfter) {
            throw HCFError.httpStatus(code, retryAfter: retryAfter)
        } catch {
            var fallback = URLComponents(url: base.appending(path: "api/notifications"), resolvingAgainstBaseURL: false)!
            fallback.queryItems = [URLQueryItem(name: "page[limit]", value: String(safeLimit))]
            guard let fallbackURL = fallback.url else { throw error }
            let response = try await authenticatedRequest(fallbackURL, host: host)
            return try parseNotifications(response.data, baseURL: base, limit: safeLimit)
        }
    }

    @discardableResult
    public func markNotificationRead(host: String, notificationID: String) async throws -> Int {
        guard isNumeric(notificationID) else { throw HCFError.invalidData("Invalid notification id") }
        let base = try trustedBase(host)
        let payload: [String: Any] = [
            "data": [
                "type": "notifications",
                "id": notificationID,
                "attributes": ["isRead": true]
            ]
        ]
        return try await mutate(
            base.appending(path: "api/notifications/\(notificationID)"),
            host: host,
            payload: payload,
            methodOverridePatch: true
        )
    }

    @discardableResult
    public func sendConversationReply(host: String, conversationID: String, text: String) async throws -> Int {
        guard isNumeric(conversationID) else { throw HCFError.invalidData("Invalid conversation id") }
        let reply = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !reply.isEmpty, reply.count <= 10_000 else { throw HCFError.invalidData("Reply must contain 1–10,000 characters") }
        let base = try trustedBase(host)
        let payload: [String: Any] = [
            "data": [
                "type": "messages",
                "attributes": ["messageContents": reply, "conversationId": conversationID]
            ]
        ]
        return try await mutate(
            base.appending(path: "api/neoncube-private-messages/messages"),
            host: host,
            payload: payload,
            methodOverridePatch: false
        )
    }

    public func currentCSRFToken(host: String) async throws -> String {
        let base = try trustedBase(host)
        let cookie = await session.cookieHeader(for: host)
        guard !cookie.isEmpty else { throw HCFError.notAuthenticated }
        let response = try await http.request(
            base,
            headers: [
                "Accept": "text/html,application/xhtml+xml",
                "Cache-Control": "no-cache, max-age=0",
                "Cookie": cookie,
                "User-Agent": HCFBuildInfo.userAgentMarker + " NotificationSession"
            ]
        )
        guard let html = String(data: response.data.prefix(1_500_000), encoding: .utf8),
              let payload = extractFlarumPayload(html),
              let sessionObject = payload["session"] as? [String: Any],
              intValue(sessionObject["userId"]) > 0,
              let token = sessionObject["csrfToken"] as? String,
              !token.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw HCFError.notAuthenticated
        }
        return String(token.prefix(1000))
    }

    private func authenticatedRequest(_ url: URL, host: String) async throws -> HCFHTTPResponse {
        let cookie = await session.cookieHeader(for: host)
        let headers = [
            "Accept": "application/vnd.api+json, application/json",
            "Cache-Control": "no-cache, max-age=0",
            "Cookie": cookie,
            "User-Agent": HCFBuildInfo.userAgentMarker + " Notification"
        ]
        return try await http.request(url, headers: headers)
    }

    private func mutate(_ url: URL, host: String, payload: [String: Any], methodOverridePatch: Bool) async throws -> Int {
        let cookie = await session.cookieHeader(for: host)
        guard !cookie.isEmpty else { throw HCFError.notAuthenticated }
        let csrf = try await currentCSRFToken(host: host)
        let body = try JSONSerialization.data(withJSONObject: payload)
        var headers = [
            "Accept": "application/vnd.api+json, application/json",
            "Content-Type": "application/vnd.api+json",
            "X-CSRF-Token": csrf,
            "Cookie": cookie,
            "User-Agent": HCFBuildInfo.userAgentMarker + " NotificationAction"
        ]
        if methodOverridePatch { headers["X-HTTP-Method-Override"] = "PATCH" }
        let response = try await http.request(url, method: .post, headers: headers, body: body)
        return response.statusCode
    }

    private func trustedBase(_ host: String) throws -> URL {
        let normalized = host.lowercased()
        guard [HCFBuildInfo.primaryHost, HCFBuildInfo.backupHost].contains(normalized),
              let url = URL(string: "https://\(normalized)/") else { throw HCFError.untrustedURL(URL(string: "https://invalid.invalid")!) }
        return url
    }

    private func parseNotifications(_ data: Data, baseURL: URL, limit: Int) throws -> [HCFNotificationItem] {
        let root = try jsonObject(data)
        guard let rows = root["data"] as? [[String: Any]] else { return [] }
        let included = (root["included"] as? [[String: Any]]) ?? []
        var index: [String: [String: Any]] = [:]
        for item in included {
            index[relationKey(item["type"], item["id"])] = item
        }
        return rows.prefix(limit).compactMap { parseNotification($0, included: index, baseURL: baseURL) }
    }

    private func parseNotification(_ item: [String: Any], included: [String: [String: Any]], baseURL: URL) -> HCFNotificationItem? {
        guard let id = stringValue(item["id"]), !id.isEmpty else { return nil }
        let attributes = (item["attributes"] as? [String: Any]) ?? [:]
        let type = stringValue(attributes["type"]) ?? "notification"
        let lowerType = type.lowercased()
        let relationships = (item["relationships"] as? [String: Any]) ?? [:]
        let fromRelation = relationData(relationships["fromUser"])
        let subjectRelation = relationData(relationships["subject"])
        let fromUser = fromRelation.flatMap { included[relationKey($0["type"], $0["id"])] }
        let subject = subjectRelation.flatMap { included[relationKey($0["type"], $0["id"])] }
        let content = contentObject(attributes["content"])

        var userLabel = firstNonEmpty(attribute(fromUser, "displayName"), attribute(fromUser, "username"))
        if userLabel.isEmpty { userLabel = firstDeep(content, keys: ["displayName", "display_name", "username", "user_name", "senderName", "sender_name"]) }
        if userLabel.isEmpty { userLabel = "Someone" }

        var conversationID = firstDeep(content, keys: ["conversationId", "conversation_id"])
        var discussionID = firstDeep(content, keys: ["discussionId", "discussion_id"])
        var titleOrBody = firstDeep(content, keys: ["discussionTitle", "discussion_title", "title"])
        var postNumber = firstDeep(content, keys: ["postNumber", "post_number", "number"])
        var userSlug = firstDeep(content, keys: ["userSlug", "user_slug", "slug", "username"])
        let actorSlug = firstNonEmpty(attribute(fromUser, "slug"), attribute(fromUser, "username"))
        let subjectType = stringValue(subjectRelation?["type"]) ?? ""

        if subjectType == "discussions" {
            discussionID = stringValue(subjectRelation?["id"]) ?? discussionID
            titleOrBody = firstNonEmpty(titleOrBody, attribute(subject, "title"))
        } else if subjectType == "posts" {
            if let relation = relationData((subject?["relationships"] as? [String: Any])?["discussion"]) {
                discussionID = stringValue(relation["id"]) ?? discussionID
            }
            postNumber = firstNonEmpty(postNumber, attribute(subject, "number"))
        } else if subjectType == "users" {
            userSlug = firstNonEmpty(attribute(subject, "slug"), attribute(subject, "username"), userSlug)
        }

        let privateMessageType = lowerType.contains("privatediscussion")
            || lowerType.contains("private_message") || lowerType.contains("privatemessage")
            || lowerType.contains("conversationmessage") || lowerType.contains("conversation_message")
            || lowerType.contains("messenger")
        let isMessage = !conversationID.isEmpty || privateMessageType

        let title: String
        if isMessage { title = "New message from \(userLabel)" }
        else if lowerType.contains("postmentioned") || lowerType.contains("usermentioned") { title = "\(userLabel) mentioned you" }
        else if lowerType.contains("liked") || lowerType.contains("postliked") { title = "\(userLabel) liked your post" }
        else if lowerType.contains("newpost") || lowerType.contains("reply") { title = "New reply from \(userLabel)" }
        else if lowerType.contains("follow") { title = "\(userLabel) followed you" }
        else { title = "New forum alert from \(userLabel)" }

        let message = firstDeep(content, keys: ["message", "body", "text", "excerpt", "preview", "content"])
        if isMessage || titleOrBody.isEmpty { titleOrBody = message }
        var body = cleanNotificationBody(titleOrBody)
        if body.isEmpty { body = isMessage ? "You have a new private message." : readableType(type) }

        var target = baseURL.appending(path: "notifications")
        if isMessage, isNumeric(conversationID) { target = baseURL.appending(path: "conversations/\(conversationID)") }
        else if lowerType.contains("follow"), !actorSlug.isEmpty { target = baseURL.appending(path: "u/\(actorSlug)") }
        else if subjectType == "users", !userSlug.isEmpty { target = baseURL.appending(path: "u/\(userSlug)") }
        else if isNumeric(discussionID) {
            var path = "d/\(discussionID)"
            if isNumeric(postNumber) { path += "/\(postNumber)" }
            target = baseURL.appending(path: path)
        }

        let avatar = absoluteURL(attribute(fromUser, "avatarUrl"), baseURL: baseURL)
        let replyCapable = isNumeric(conversationID) && (privateMessageType || subjectType == "messages")
        let isRead = boolValue(attributes["isRead"])
        let createdAt = parseDate(stringValue(attributes["time"]) ?? stringValue(attributes["createdAt"]) ?? "")

        return .init(
            id: id,
            title: String(title.prefix(120)),
            body: String(body.prefix(500)),
            url: target,
            conversationID: conversationID.isEmpty ? nil : conversationID,
            discussionID: discussionID.isEmpty ? nil : discussionID,
            actorAvatarURL: avatar,
            isRead: isRead,
            replyCapable: replyCapable,
            createdAt: createdAt
        )
    }

    private func extractFlarumPayload(_ html: String) -> [String: Any]? {
        guard let marker = html.range(of: "flarum-json-payload") ?? html.range(of: "id='flarum-json-payload'") else { return nil }
        guard let open = html.range(of: ">", range: marker.lowerBound..<html.endIndex),
              let close = html.range(of: "</script>", range: open.upperBound..<html.endIndex) else { return nil }
        let json = String(html[open.upperBound..<close.lowerBound])
        guard let data = json.data(using: .utf8), let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        return object
    }

    private func jsonObject(_ data: Data) throws -> [String: Any] {
        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw HCFError.invalidData("Expected JSON object") }
        return object
    }

    private func relationData(_ value: Any?) -> [String: Any]? {
        guard let relation = value as? [String: Any] else { return nil }
        return relation["data"] as? [String: Any]
    }

    private func relationKey(_ type: Any?, _ id: Any?) -> String { "\(stringValue(type) ?? "")#\(stringValue(id) ?? "")" }
    private func attribute(_ item: [String: Any]?, _ key: String) -> String {
        stringValue((item?["attributes"] as? [String: Any])?[key]) ?? ""
    }

    private func contentObject(_ value: Any?) -> [String: Any] {
        if let object = value as? [String: Any] { return object }
        if let text = value as? String, let data = text.data(using: .utf8), let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] { return object }
        return [:]
    }

    private func firstDeep(_ object: [String: Any], keys: [String], depth: Int = 0) -> String {
        guard depth <= 5 else { return "" }
        for key in keys {
            if let value = object[key], let found = readableValue(value, keys: keys, depth: depth + 1), !found.isEmpty { return found }
        }
        for value in object.values where value is [String: Any] || value is [Any] {
            if let found = readableValue(value, keys: keys, depth: depth + 1), !found.isEmpty { return found }
        }
        return ""
    }

    private func readableValue(_ value: Any, keys: [String], depth: Int) -> String? {
        guard depth <= 6 else { return nil }
        if let object = value as? [String: Any] { return firstDeep(object, keys: keys, depth: depth) }
        if let array = value as? [Any] {
            for item in array { if let found = readableValue(item, keys: keys, depth: depth + 1), !found.isEmpty { return found } }
            return nil
        }
        let text = String(describing: value).trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return nil }
        if (text.hasPrefix("{") || text.hasPrefix("[")), let data = text.data(using: .utf8), let nested = try? JSONSerialization.jsonObject(with: data) {
            return readableValue(nested, keys: keys, depth: depth + 1)
        }
        return String(text.prefix(500))
    }

    private func cleanNotificationBody(_ text: String) -> String {
        let stripped = text.replacingOccurrences(of: #"<[^>]+>"#, with: " ", options: .regularExpression)
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return String(stripped.prefix(500))
    }

    private func readableType(_ type: String) -> String {
        let spaced = type.replacingOccurrences(of: "_", with: " ").replacingOccurrences(of: "-", with: " ")
        let words = spaced.replacingOccurrences(of: #"([a-z])([A-Z])"#, with: "$1 $2", options: .regularExpression)
        return words.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "You have a new forum notification." : words
    }

    private func absoluteURL(_ raw: String, baseURL: URL) -> URL? {
        guard !raw.isEmpty else { return nil }
        if let url = URL(string: raw), url.scheme != nil { return url.scheme == "https" ? url : nil }
        return URL(string: raw, relativeTo: baseURL)?.absoluteURL
    }

    private func stringValue(_ value: Any?) -> String? {
        if let string = value as? String { return string.trimmingCharacters(in: .whitespacesAndNewlines) }
        if let number = value as? NSNumber { return number.stringValue }
        return nil
    }
    private func intValue(_ value: Any?) -> Int {
        if let number = value as? NSNumber { return number.intValue }
        return Int(stringValue(value) ?? "") ?? 0
    }
    private func boolValue(_ value: Any?) -> Bool {
        if let value = value as? Bool { return value }
        if let number = value as? NSNumber { return number.boolValue }
        return ["1", "true", "yes"].contains((stringValue(value) ?? "").lowercased())
    }
    private func isNumeric(_ value: String) -> Bool { !value.isEmpty && value.allSatisfy(\.isNumber) }
    private func firstNonEmpty(_ values: String...) -> String { values.first { !$0.isEmpty } ?? "" }
    private func parseDate(_ value: String) -> Date? { ISO8601DateFormatter().date(from: value) }
}
