import Foundation
import UserNotifications
import BackgroundTasks
import WidgetKit
import UIKit
import HCFCore
import HCFForum

public enum HCFNotificationConstants {
    public static let replyCategory = "HCF_FORUM_REPLY"
    public static let readCategory = "HCF_FORUM_READ"
    public static let replyAction = "HCF_REPLY"
    public static let markReadAction = "HCF_MARK_READ"
    public static let refreshTask = "com.harleytg.forum.dev.notification-refresh"
    public static let updateTask = "com.harleytg.forum.dev.update-refresh"
}

public actor NotificationSyncService {
    public static let shared = NotificationSyncService()

    private let api: ForumAPIClient
    private let center: UNUserNotificationCenter
    private var foregroundTask: Task<Void, Never>?
    private var inFlight = false

    public init(api: ForumAPIClient = .shared, center: UNUserNotificationCenter = .current()) {
        self.api = api
        self.center = center
    }

    public func startForegroundSync() {
        guard foregroundTask == nil else { return }
        foregroundTask = Task { [weak self] in
            while !Task.isCancelled {
                _ = await self?.syncNow(source: "foreground")
                try? await Task.sleep(for: .seconds(8))
            }
        }
    }

    public func stopForegroundSync() {
        foregroundTask?.cancel()
        foregroundTask = nil
    }

    @discardableResult
    public func syncNow(source: String) async -> Bool {
        guard !inFlight else { return false }
        inFlight = true
        defer { inFlight = false }

        let defaults = UserDefaults(suiteName: HCFBuildInfo.appGroupID) ?? .standard
        guard defaults.object(forKey: PreferencesStore.Key.backgroundNotificationSync) as? Bool ?? true else { return false }
        let identity = await SharedContainerStore.shared.identity()
        guard identity.isSignedIn, !identity.id.isEmpty else { return false }
        let host = trustedHost(defaults.string(forKey: PreferencesStore.Key.activeHost) ?? identity.host)

        do {
            let count = try await api.unreadCount(host: host, userID: identity.id)
            let previous = defaults.object(forKey: PreferencesStore.Key.lastNotificationCount) as? Int
            defaults.set(count, forKey: PreferencesStore.Key.lastNotificationCount)
            let delta = previous.map { max(0, count - max(0, $0)) } ?? 0
            var newestPreview: WidgetNotificationPreview?

            if delta > 0 {
                let items = try await api.latestNotifications(host: host, limit: max(8, delta + 4))
                let delivered = deliveredIDs(defaults)
                var nextDelivered = delivered
                var posted = 0
                for item in items where posted < delta {
                    guard !nextDelivered.contains(item.id) else { continue }
                    try await post(item, host: host)
                    nextDelivered.insert(item.id)
                    posted += 1
                    if newestPreview == nil {
                        newestPreview = .init(title: item.title, body: item.body, url: item.url)
                    }
                    appendHistory(item, defaults: defaults)
                }
                saveDeliveredIDs(nextDelivered, defaults: defaults)
            }

            try? await center.setBadgeCount(max(0, count))
            await updateWidget(count: count, identity: identity, preview: newestPreview, defaults: defaults)
            await DiagnosticLogger.shared.info("notification_sync", "\(source) • unread=\(count) delta=\(delta)")
            return true
        } catch let HCFError.httpStatus(code, _) where code == 401 {
            await ForumSessionManager.shared.clearSession()
            await DiagnosticLogger.shared.warning("notification_auth", "HTTP 401 • session cleared")
            return false
        } catch HCFError.notAuthenticated {
            await ForumSessionManager.shared.clearSession()
            await DiagnosticLogger.shared.warning("notification_auth", "missing authenticated cookie")
            return false
        } catch {
            await DiagnosticLogger.shared.warning("notification_sync", "\(source) • \(error.localizedDescription)")
            return false
        }
    }

    public func consumePushPayload(_ userInfo: [AnyHashable: Any]) async -> UIBackgroundFetchResult {
        if let item = Self.notificationFromPush(userInfo) {
            do {
                let defaults = UserDefaults(suiteName: HCFBuildInfo.appGroupID) ?? .standard
                var delivered = deliveredIDs(defaults)
                if !delivered.contains(item.id) {
                    try await post(item, host: trustedHost(defaults.string(forKey: PreferencesStore.Key.activeHost) ?? HCFBuildInfo.primaryHost))
                    delivered.insert(item.id)
                    saveDeliveredIDs(delivered, defaults: defaults)
                    appendHistory(item, defaults: defaults)
                }
            } catch {
                await DiagnosticLogger.shared.warning("push_payload", error.localizedDescription)
            }
        }
        return await syncNow(source: "push") ? .newData : .noData
    }

    private func post(_ item: HCFNotificationItem, host: String) async throws {
        let content = UNMutableNotificationContent()
        content.title = item.title
        content.body = item.body
        content.categoryIdentifier = item.replyCapable
            ? HCFNotificationConstants.replyCategory
            : HCFNotificationConstants.readCategory
        content.sound = .default
        content.threadIdentifier = "hcf-forum"
        content.userInfo = [
            "hcf_notification_id": item.id,
            "hcf_conversation_id": item.conversationID ?? "",
            "hcf_discussion_id": item.discussionID ?? "",
            "hcf_notification_url": item.url?.absoluteString ?? "",
            "hcf_forum_host": host,
            "hcf_reply_capable": item.replyCapable
        ]
        let request = UNNotificationRequest(identifier: "hcf-\(item.id)", content: content, trigger: nil)
        try await center.add(request)
    }

    private func updateWidget(count: Int, identity: ForumIdentity, preview: WidgetNotificationPreview?, defaults: UserDefaults) async {
        let followTheme = defaults.object(forKey: PreferencesStore.Key.widgetFollowTheme) as? Bool ?? true
        let appTheme = HCFThemeMode(rawValue: defaults.string(forKey: PreferencesStore.Key.appTheme) ?? "system") ?? .system
        var current = await SharedContainerStore.shared.widgetSnapshot()
        current.unreadCount = max(0, count)
        current.username = identity.username
        current.lastUpdated = .now
        if let preview { current.lastNotification = preview }
        current.theme = followTheme ? appTheme : current.theme
        current.backgroundAlpha = defaults.object(forKey: PreferencesStore.Key.widgetBackgroundAlpha) as? Int ?? current.backgroundAlpha
        current.textSize = defaults.object(forKey: PreferencesStore.Key.widgetTextSize) as? Int ?? current.textSize
        current.compact = defaults.object(forKey: PreferencesStore.Key.widgetCompact) as? Bool ?? current.compact
        current.showUsername = defaults.object(forKey: PreferencesStore.Key.widgetShowUsername) as? Bool ?? true
        current.showUnread = defaults.object(forKey: PreferencesStore.Key.widgetShowUnread) as? Bool ?? true
        current.showLastUpdated = defaults.object(forKey: PreferencesStore.Key.widgetShowUpdated) as? Bool ?? true
        current.showPreview = defaults.object(forKey: PreferencesStore.Key.widgetShowPreview) as? Bool ?? true
        current.defaultTap = HCFWidgetTapAction(rawValue: defaults.string(forKey: PreferencesStore.Key.widgetDefaultTap) ?? "forum") ?? .forum
        try? await SharedContainerStore.shared.saveWidgetSnapshot(current)
        WidgetCenter.shared.reloadAllTimelines()
    }

    private func deliveredIDs(_ defaults: UserDefaults) -> Set<String> {
        let raw = defaults.string(forKey: "delivered_notification_ids_ios") ?? ""
        return Set(raw.split(separator: "\n").map(String.init))
    }

    private func saveDeliveredIDs(_ input: Set<String>, defaults: UserDefaults) {
        let trimmed = Array(input.suffix(100))
        defaults.set(trimmed.joined(separator: "\n"), forKey: "delivered_notification_ids_ios")
    }

    private func appendHistory(_ item: HCFNotificationItem, defaults: UserDefaults) {
        let mode = HCFNotificationHistoryMode(rawValue: defaults.string(forKey: PreferencesStore.Key.historyMode) ?? "title") ?? .title
        guard mode != .off else { defaults.removeObject(forKey: "native_notification_history_json_ios"); return }
        let limit = min(60, max(10, defaults.object(forKey: PreferencesStore.Key.historyLimit) as? Int ?? 30))
        var history = (try? JSONDecoder().decode([WidgetNotificationPreview].self, from: defaults.data(forKey: "native_notification_history_json_ios") ?? Data())) ?? []
        let body = mode == .full ? item.body : ""
        history.insert(.init(title: item.title, body: body, url: item.url), at: 0)
        history = Array(history.prefix(limit))
        if let data = try? JSONEncoder().encode(history) { defaults.set(data, forKey: "native_notification_history_json_ios") }
    }

    private func trustedHost(_ raw: String) -> String {
        let host = raw.lowercased()
        return [HCFBuildInfo.primaryHost, HCFBuildInfo.backupHost].contains(host) ? host : HCFBuildInfo.primaryHost
    }

    private static func notificationFromPush(_ userInfo: [AnyHashable: Any]) -> HCFNotificationItem? {
        let attributes = userInfo["attributes"] as? [String: Any] ?? userInfo.reduce(into: [String: Any]()) { result, pair in
            if let key = pair.key as? String { result[key] = pair.value }
        }
        func first(_ keys: String...) -> String {
            for key in keys {
                if let value = attributes[key] as? String, !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return value }
                if let value = userInfo[key] as? String, !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return value }
            }
            return ""
        }
        let id = first("id", "notificationId", "notification_id")
        let title = first("title")
        let body = first("body", "message", "text", "content")
        guard !id.isEmpty, !title.isEmpty, !body.isEmpty else { return nil }
        let url = URL(string: first("url"))
        let conversation = first("conversationId", "conversation_id")
        let explicitReply = first("replyCapable", "reply_capable").lowercased()
        let replyCapable = !conversation.isEmpty && (explicitReply.isEmpty || ["1", "true", "yes"].contains(explicitReply))
        return .init(id: id, title: title, body: body, url: url, conversationID: conversation.isEmpty ? nil : conversation, replyCapable: replyCapable)
    }
}

@MainActor
public final class HCFNotificationCoordinator: NSObject, UNUserNotificationCenterDelegate {
    public static let shared = HCFNotificationCoordinator()
    private let center = UNUserNotificationCenter.current()
    private let sync = NotificationSyncService.shared

    public override init() { super.init() }

    public func configure() {
        let reply = UNTextInputNotificationAction(
            identifier: HCFNotificationConstants.replyAction,
            title: "Reply",
            options: [],
            textInputButtonTitle: "Send",
            textInputPlaceholder: "Reply to HCF message"
        )
        let markRead = UNNotificationAction(
            identifier: HCFNotificationConstants.markReadAction,
            title: "Mark as Read",
            options: []
        )
        center.setNotificationCategories([
            UNNotificationCategory(
                identifier: HCFNotificationConstants.replyCategory,
                actions: [reply, markRead],
                intentIdentifiers: [],
                options: [.customDismissAction]
            ),
            UNNotificationCategory(
                identifier: HCFNotificationConstants.readCategory,
                actions: [markRead],
                intentIdentifiers: [],
                options: [.customDismissAction]
            )
        ])
        center.delegate = self
        registerBackgroundTasks()
    }

    public func requestAuthorization() async -> Bool {
        do {
            let granted = try await center.requestAuthorization(options: [.alert, .badge, .sound])
            if granted { UIApplication.shared.registerForRemoteNotifications() }
            await DiagnosticLogger.shared.info("notification_permission", granted ? "granted" : "denied")
            return granted
        } catch {
            await DiagnosticLogger.shared.warning("notification_permission", error.localizedDescription)
            return false
        }
    }

    public func registerAPNsToken(_ deviceToken: Data) {
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        Task {
            try? await KeychainStore.shared.setString(token, account: "apns-device-token")
            await DiagnosticLogger.shared.info("apns_token", "registered \(token.count / 2) bytes")
        }
    }

    public func handleRemoteNotification(_ userInfo: [AnyHashable: Any]) async -> UIBackgroundFetchResult {
        await sync.consumePushPayload(userInfo)
    }

    public func scheduleRefresh(earliest: TimeInterval = 15 * 60) {
        let request = BGAppRefreshTaskRequest(identifier: HCFNotificationConstants.refreshTask)
        request.earliestBeginDate = Date(timeIntervalSinceNow: max(15 * 60, earliest))
        do { try BGTaskScheduler.shared.submit(request) }
        catch { Task { await DiagnosticLogger.shared.warning("background_schedule", error.localizedDescription) } }
    }

    private func registerBackgroundTasks() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: HCFNotificationConstants.refreshTask, using: nil) { task in
            guard let refresh = task as? BGAppRefreshTask else { task.setTaskCompleted(success: false); return }
            let work = Task {
                let result = await NotificationSyncService.shared.syncNow(source: "bg-refresh")
                await MainActor.run { HCFNotificationCoordinator.shared.scheduleRefresh() }
                refresh.setTaskCompleted(success: result)
            }
            refresh.expirationHandler = { work.cancel() }
        }
    }

    public func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound, .badge])
    }

    public func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let info = response.notification.request.content.userInfo
        let host = trustedHost(info["hcf_forum_host"] as? String ?? HCFBuildInfo.primaryHost)
        let notificationID = info["hcf_notification_id"] as? String ?? ""
        let conversationID = info["hcf_conversation_id"] as? String ?? ""

        Task {
            do {
                switch response.actionIdentifier {
                case HCFNotificationConstants.replyAction:
                    guard let textResponse = response as? UNTextInputNotificationResponse else { break }
                    let text = textResponse.userText.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !text.isEmpty, text.count <= 10_000, !conversationID.isEmpty else { break }
                    _ = try await ForumAPIClient.shared.sendConversationReply(host: host, conversationID: conversationID, text: text)
                    if !notificationID.isEmpty { _ = try? await ForumAPIClient.shared.markNotificationRead(host: host, notificationID: notificationID) }
                    await DiagnosticLogger.shared.info("notification_reply_action", "success")
                case HCFNotificationConstants.markReadAction:
                    guard !notificationID.isEmpty else { break }
                    _ = try await ForumAPIClient.shared.markNotificationRead(host: host, notificationID: notificationID)
                    await DiagnosticLogger.shared.info("notification_read_action", "success")
                default:
                    if !notificationID.isEmpty { _ = try? await ForumAPIClient.shared.markNotificationRead(host: host, notificationID: notificationID) }
                }
                _ = await NotificationSyncService.shared.syncNow(source: "notification-action")
            } catch {
                await DiagnosticLogger.shared.warning("notification_action", error.localizedDescription)
            }
            completionHandler()
        }
    }

    private func trustedHost(_ raw: String) -> String {
        let normalized = raw.lowercased()
        return [HCFBuildInfo.primaryHost, HCFBuildInfo.backupHost].contains(normalized) ? normalized : HCFBuildInfo.primaryHost
    }
}
