import SwiftUI
import WidgetKit
import HCFCore

public struct HCFWidgetEntry: TimelineEntry, Sendable {
    public let date: Date
    public let snapshot: WidgetSnapshot
    public init(date: Date = .now, snapshot: WidgetSnapshot) { self.date = date; self.snapshot = snapshot }
}

private enum HCFWidgetCache {
    static let snapshotKey = "ios_widget_snapshot_v1"

    static func snapshot() -> WidgetSnapshot {
        let defaults = UserDefaults(suiteName: HCFBuildInfo.appGroupID) ?? .standard
        guard let data = defaults.data(forKey: snapshotKey) else { return .init() }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return (try? decoder.decode(WidgetSnapshot.self, from: data)) ?? .init()
    }
}

public struct HCFWidgetProvider: TimelineProvider {
    public init() {}

    public func placeholder(in context: Context) -> HCFWidgetEntry {
        .init(snapshot: .init(unreadCount: 3, username: "HarleyTG", lastUpdated: .now))
    }

    public func getSnapshot(in context: Context, completion: @escaping (HCFWidgetEntry) -> Void) {
        completion(.init(snapshot: HCFWidgetCache.snapshot()))
    }

    public func getTimeline(in context: Context, completion: @escaping (Timeline<HCFWidgetEntry>) -> Void) {
        let snapshot = HCFWidgetCache.snapshot()
        let defaults = UserDefaults(suiteName: HCFBuildInfo.appGroupID) ?? .standard
        let minutes = max(15, defaults.object(forKey: PreferencesStore.Key.widgetRefreshInterval) as? Int ?? 30)
        let next = Calendar.current.date(byAdding: .minute, value: minutes, to: .now) ?? .now.addingTimeInterval(TimeInterval(minutes * 60))
        completion(Timeline(entries: [.init(snapshot: snapshot)], policy: .after(next)))
    }
}

public struct HCFNotificationsWidgetDefinition: Widget {
    public init() {}
    public var body: some WidgetConfiguration {
        StaticConfiguration(kind: "com.harleytg.forum.dev.widget.notifications", provider: HCFWidgetProvider()) { entry in
            HCFWidgetView(entry: entry, unreadFocused: false)
        }
        .configurationDisplayName("Harley's Clan Forum")
        .description("Forum status, unread alerts and HCF quick actions.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

public struct HCFUnreadWidgetDefinition: Widget {
    public init() {}
    public var body: some WidgetConfiguration {
        StaticConfiguration(kind: "com.harleytg.forum.dev.widget.unread", provider: HCFWidgetProvider()) { entry in
            HCFWidgetView(entry: entry, unreadFocused: true)
        }
        .configurationDisplayName("HCF Unread")
        .description("A notification-focused HCF widget.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

private struct HCFWidgetPalette {
    let background: Color
    let panel: Color
    let text: Color
    let secondary: Color
    let accent = Color(red: 0, green: 184.0 / 255.0, blue: 240.0 / 255.0)

    init(mode: HCFThemeMode, alpha: Int, environment: ColorScheme) {
        let opacity = Double(min(100, max(20, alpha))) / 100.0
        let resolved: HCFThemeMode
        switch mode {
        case .system, .followForum:
            resolved = environment == .dark ? .dark : .light
        default:
            resolved = mode
        }

        switch resolved {
        case .light:
            background = Color.white.opacity(opacity)
            panel = Color.black.opacity(0.06)
            text = .black
            secondary = Color.black.opacity(0.62)
        case .amoled:
            background = Color.black.opacity(opacity)
            panel = Color.white.opacity(0.07)
            text = .white
            secondary = Color.white.opacity(0.68)
        case .dark:
            background = Color(red: 13.0 / 255.0, green: 16.0 / 255.0, blue: 20.0 / 255.0).opacity(opacity)
            panel = Color.white.opacity(0.07)
            text = Color(red: 232.0 / 255.0, green: 248.0 / 255.0, blue: 1)
            secondary = Color(red: 174.0 / 255.0, green: 187.0 / 255.0, blue: 194.0 / 255.0)
        case .system, .followForum:
            background = Color.white.opacity(opacity)
            panel = Color.black.opacity(0.06)
            text = .black
            secondary = Color.black.opacity(0.62)
        }
    }
}

public struct HCFWidgetView: View {
    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var colorScheme
    let entry: HCFWidgetEntry
    let unreadFocused: Bool

    public init(entry: HCFWidgetEntry, unreadFocused: Bool) {
        self.entry = entry
        self.unreadFocused = unreadFocused
    }

    public var body: some View {
        let snapshot = entry.snapshot
        let palette = HCFWidgetPalette(mode: snapshot.theme, alpha: snapshot.backgroundAlpha, environment: colorScheme)
        VStack(alignment: .leading, spacing: snapshot.compact ? 6 : 9) {
            header(snapshot, palette)
            if unreadFocused || family == .systemSmall {
                unreadHero(snapshot, palette)
            } else {
                statusRow(snapshot, palette)
            }
            if family != .systemSmall, snapshot.showPreview, let preview = snapshot.lastNotification {
                notificationPreview(preview, palette)
            }
            Spacer(minLength: 0)
            quickActions(palette)
        }
        .font(.system(size: CGFloat(snapshot.textSize), weight: .medium, design: .rounded))
        .padding(snapshot.compact ? 10 : 13)
        .foregroundStyle(palette.text)
        .containerBackground(for: .widget) { palette.background }
        .widgetURL(deepLink(snapshot.defaultTap))
    }

    @ViewBuilder
    private func header(_ snapshot: WidgetSnapshot, _ palette: HCFWidgetPalette) -> some View {
        HStack(spacing: 7) {
            Image(systemName: "pawprint.fill")
                .foregroundStyle(palette.accent)
            VStack(alignment: .leading, spacing: 1) {
                Text("HCF").fontWeight(.bold)
                if snapshot.showUsername, !snapshot.username.isEmpty, family != .systemSmall {
                    Text("@\(snapshot.username)").font(.caption2).foregroundStyle(palette.secondary).lineLimit(1)
                }
            }
            Spacer()
            Circle().fill(palette.accent).frame(width: 7, height: 7)
        }
    }

    @ViewBuilder
    private func unreadHero(_ snapshot: WidgetSnapshot, _ palette: HCFWidgetPalette) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            if snapshot.showUnread {
                Text("\(snapshot.unreadCount)")
                    .font(.system(size: family == .systemSmall ? 34 : 42, weight: .bold, design: .rounded))
                    .foregroundStyle(snapshot.unreadCount > 0 ? palette.accent : palette.text)
                Text(snapshot.unreadCount == 1 ? "unread alert" : "unread alerts")
                    .foregroundStyle(palette.secondary)
            } else {
                Text("Forum ready").font(.headline)
            }
            if snapshot.showLastUpdated, let updated = snapshot.lastUpdated {
                Text(updated, style: .relative).font(.caption2).foregroundStyle(palette.secondary)
            }
        }
    }

    private func statusRow(_ snapshot: WidgetSnapshot, _ palette: HCFWidgetPalette) -> some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(snapshot.showUnread ? "\(snapshot.unreadCount) unread" : "Connected")
                    .font(.headline)
                    .foregroundStyle(snapshot.unreadCount > 0 ? palette.accent : palette.text)
                if snapshot.showLastUpdated, let updated = snapshot.lastUpdated {
                    Text("Updated \(updated, style: .relative)").font(.caption2).foregroundStyle(palette.secondary)
                }
            }
            Spacer()
        }
        .padding(9)
        .background(palette.panel, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private func notificationPreview(_ preview: WidgetNotificationPreview, _ palette: HCFWidgetPalette) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(preview.title).font(.caption).fontWeight(.semibold).lineLimit(1)
            if !preview.body.isEmpty { Text(preview.body).font(.caption2).foregroundStyle(palette.secondary).lineLimit(family == .systemLarge ? 3 : 1) }
        }
        .padding(9)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.panel, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private func quickActions(_ palette: HCFWidgetPalette) -> some View {
        HStack(spacing: family == .systemSmall ? 8 : 11) {
            action("Forum", "house.fill", .forum, palette)
            action("Alerts", "bell.fill", .notifications, palette)
            if family != .systemSmall {
                action("Reload", "arrow.clockwise", nil, palette, customURL: URL(string: "hcf://widget/reload"))
                action("Settings", "gearshape.fill", .settings, palette)
            }
        }
        .font(.caption2)
    }

    private func action(_ title: String, _ symbol: String, _ tap: HCFWidgetTapAction?, _ palette: HCFWidgetPalette, customURL: URL? = nil) -> some View {
        Link(destination: customURL ?? deepLink(tap ?? .forum)) {
            VStack(spacing: 3) {
                Image(systemName: symbol).foregroundStyle(palette.accent)
                if family != .systemSmall { Text(title).foregroundStyle(palette.secondary) }
            }
            .frame(maxWidth: .infinity)
        }
    }

    private func deepLink(_ action: HCFWidgetTapAction) -> URL {
        URL(string: "hcf://\(action.rawValue)")!
    }
}
