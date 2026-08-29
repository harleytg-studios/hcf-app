import SwiftUI
import UniformTypeIdentifiers
import UserNotifications
import HCFCore
import HCFForum
import HCFNotifications
import HCFUpdates
import HCFPlatform

@MainActor
public final class HCFAppModel: ObservableObject {
    public enum StartupState: Equatable {
        case starting(String)
        case ready
        case restricted(HCFBanCheckResult)
        case failed(String)
    }

    @Published public var startup: StartupState = .starting("Starting HCF…")
    @Published public var router = ForumRouter()
    @Published public var identity: ForumIdentity = .guest()
    @Published public var selectedRoute: ForumRoute = .home
    @Published public var settingsPresented = false
    @Published public var diagnosticsPresented = false
    @Published public var updatePresented = false
    @Published public var externalURL: URL?
    @Published public var updateResult: HCFUpdateResult?

    public let preferences: PreferencesStore
    public let browser = ForumBrowserState()
    private let session = ForumSessionManager.shared

    public init(preferences: PreferencesStore = .init()) { self.preferences = preferences }

    public func start() async {
        startup = .starting("Loading trusted forum domains…")
        let registry = await DomainRegistryService.shared.registry()
        router = ForumRouter(registry: registry)
        session.updateTrustedHosts(registry.trustedHosts)

        startup = .starting("Restoring secure forum session…")
        await session.restoreCookies()
        identity = await session.currentIdentity()
        if registry.contains(identity.host) { preferences.activeHost = identity.host }

        startup = .starting("Checking access status…")
        let access = await HCFBanService.shared.checkAccess(identity: identity)
        if access.banned {
            startup = .restricted(access)
            return
        }

        startup = .starting("Preparing notifications and widgets…")
        HCFNotificationCoordinator.shared.configure()
        HCFNotificationCoordinator.shared.scheduleRefresh()

        startup = .ready
        navigate(.home)
        Task { await HCFDiscordObservationService.shared.observe(identity: identity) }
        Task { updateResult = await HCFUpdateChecker.shared.check() }
    }

    public func navigate(_ route: ForumRoute) {
        selectedRoute = route
        let url = router.url(for: route, host: preferences.activeHost)
        browser.navigate(to: url)
    }

    public func switchHost() {
        let next = preferences.activeHost == router.registry.primary
            ? (router.registry.backups.first ?? router.registry.primary)
            : router.registry.primary
        let current = browser.currentURL ?? router.home(host: preferences.activeHost)
        preferences.activeHost = next
        browser.navigate(to: router.equivalent(current, on: next))
    }

    public func identityChanged(_ value: ForumIdentity) {
        identity = value
        if value.isSignedIn { preferences.activeHost = value.host }
        Task { await HCFDiscordObservationService.shared.observe(identity: value) }
    }

    public func handleIncomingURL(_ url: URL) {
        if url.scheme?.lowercased() == "hcf" {
            switch url.host?.lowercased() {
            case "settings": settingsPresented = true; return
            case "notifications": navigate(.notifications); return
            case "latest": navigate(.latest); return
            case "profile": navigate(.profile(username: identity.username.isEmpty ? nil : identity.username)); return
            case "widget" where url.path.lowercased().contains("reload"):
                Task { _ = await NotificationSyncService.shared.syncNow(source: "widget-reload") }
                return
            default: navigate(.home); return
            }
        }
        if let trusted = router.route(fromIncoming: url) { browser.navigate(to: trusted) }
        else { externalURL = url }
    }

    public func refreshUpdate() async { updateResult = await HCFUpdateChecker.shared.check(force: true) }
}

public struct HCFRootView: View {
    @StateObject private var model: HCFAppModel
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL

    public init(model: HCFAppModel = .init()) { _model = StateObject(wrappedValue: model) }

    public var body: some View {
        Group {
            switch model.startup {
            case .starting(let message): HCFStartupView(message: message)
            case .restricted(let result): HCFAccessRestrictedView(result: result)
            case .failed(let message): HCFStartupFailureView(message: message) { Task { await model.start() } }
            case .ready: HCFMainView(model: model)
            }
        }
        .preferredColorScheme(preferredColorScheme)
        .task { await model.start() }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .active:
                Task { await NotificationSyncService.shared.startForegroundSync() }
            case .background:
                Task {
                    await NotificationSyncService.shared.stopForegroundSync()
                    await ForumSessionManager.shared.persistCookies()
                }
                HCFNotificationCoordinator.shared.scheduleRefresh()
            default: break
            }
        }
        .onOpenURL { model.handleIncomingURL($0) }
        .confirmationDialog(
            "You're leaving Harley's Clan Forum",
            isPresented: Binding(get: { model.externalURL != nil }, set: { if !$0 { model.externalURL = nil } }),
            titleVisibility: .visible
        ) {
            if let url = model.externalURL {
                Button("Open in Browser") { model.externalURL = nil; openURL(url) }
                Button("Cancel", role: .cancel) { model.externalURL = nil }
            }
        } message: {
            Text(model.externalURL?.host.map { "This link opens an external site: \($0)" } ?? "This link opens outside HCF.")
        }
    }

    private var preferredColorScheme: ColorScheme? {
        switch model.preferences.theme {
        case .light: .light
        case .dark, .amoled: .dark
        case .system, .followForum: nil
        }
    }
}

private struct HCFStartupView: View {
    let message: String
    var body: some View {
        ZStack {
            Color(red: 13/255, green: 16/255, blue: 20/255).ignoresSafeArea()
            VStack(spacing: 18) {
                Image(systemName: "pawprint.circle.fill").font(.system(size: 68)).foregroundStyle(.cyan)
                Text("Harley's Clan Forum").font(.title2.bold()).foregroundStyle(.white)
                ProgressView().tint(.cyan)
                Text(message).font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center)
            }.padding(30)
        }
    }
}

private struct HCFStartupFailureView: View {
    let message: String
    let retry: () -> Void
    var body: some View {
        ContentUnavailableView {
            Label("HCF couldn't start", systemImage: "exclamationmark.triangle.fill")
        } description: { Text(message) } actions: { Button("Retry", action: retry).buttonStyle(.borderedProminent) }
    }
}

private struct HCFAccessRestrictedView: View {
    let result: HCFBanCheckResult
    var body: some View {
        VStack(spacing: 18) {
            Image(systemName: "hand.raised.fill").font(.system(size: 54)).foregroundStyle(.red)
            Text("Access Restricted").font(.largeTitle.bold())
            Text(result.reason ?? "This account or network is currently restricted from Harley's Clan Forum.")
                .multilineTextAlignment(.center).foregroundStyle(.secondary)
            if let id = result.banID { LabeledContent("Ban ID", value: id).font(.footnote) }
            if let expires = result.expiresAt { LabeledContent("Expires", value: expires).font(.footnote) }
            Text("Scope: \(result.scope.rawValue)").font(.caption).foregroundStyle(.tertiary)
        }
        .padding(28)
    }
}

public struct HCFMainView: View {
    @ObservedObject var model: HCFAppModel
    @Environment(\.openURL) private var openURL

    public init(model: HCFAppModel) { self.model = model }

    public var body: some View {
        GeometryReader { proxy in
            let mode = HCFAdaptiveLayout.mode(for: proxy.size.width)
            Group {
                if mode == .desktop { desktopLayout }
                else { compactLayout }
            }
            .background(shellBackground)
        }
        .sheet(isPresented: $model.settingsPresented) { HCFSettingsView(model: model) }
        .sheet(isPresented: $model.diagnosticsPresented) { HCFDiagnosticsView() }
        .sheet(isPresented: $model.updatePresented) { HCFUpdateView(model: model) }
    }

    private var compactLayout: some View {
        VStack(spacing: 0) {
            HCFHeader(model: model)
            forumSurface
            HCFBottomBar(model: model)
        }
    }

    private var desktopLayout: some View {
        NavigationSplitView {
            List {
                Section("Forum") {
                    NavRow("Home", "house.fill") { model.navigate(.home) }
                    NavRow("Latest", "list.bullet.rectangle") { model.navigate(.latest) }
                    NavRow("New Discussion", "square.and.pencil") { model.navigate(.compose) }
                    NavRow("Notifications", "bell.fill", badge: model.preferences.unreadCount) { model.navigate(.notifications) }
                    NavRow("Profile", "person.crop.circle") { model.navigate(.profile(username: model.identity.username.isEmpty ? nil : model.identity.username)) }
                }
                Section("App") {
                    NavRow("Settings", "gearshape.fill") { model.settingsPresented = true }
                    NavRow("Diagnostics", "stethoscope") { model.diagnosticsPresented = true }
                    NavRow("Updates", "arrow.down.circle") { model.updatePresented = true }
                }
            }
            .navigationTitle("HCF")
            .navigationSplitViewColumnWidth(min: 210, ideal: 235, max: 280)
        } detail: {
            VStack(spacing: 0) { HCFHeader(model: model); forumSurface }
        }
        .navigationSplitViewStyle(.balanced)
    }

    private var forumSurface: some View {
        ZStack {
            ForumWebView(
                state: model.browser,
                initialURL: model.router.url(for: .home, host: model.preferences.activeHost),
                router: model.router,
                onExternalURL: { model.externalURL = $0 },
                onIdentity: { model.identityChanged($0) }
            )
            if model.browser.isLoading, model.browser.progress < 1 {
                VStack { ProgressView(value: model.browser.progress).tint(.cyan); Spacer() }
            }
            if let error = model.browser.lastError {
                VStack {
                    Spacer()
                    HStack {
                        Image(systemName: "wifi.exclamationmark")
                        Text(error).lineLimit(2)
                        Spacer()
                        Button("Retry") { model.browser.reload() }
                    }
                    .font(.footnote).padding(12).background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
                    .padding()
                }
            }
        }
    }

    private var shellBackground: Color { model.preferences.theme == .amoled ? .black : Color(.systemBackground) }

    private struct NavRow: View {
        let title: String; let symbol: String; let badge: Int?; let action: () -> Void
        init(_ title: String, _ symbol: String, badge: Int? = nil, action: @escaping () -> Void) { self.title = title; self.symbol = symbol; self.badge = badge; self.action = action }
        var body: some View {
            Button(action: action) {
                HStack { Label(title, systemImage: symbol); Spacer(); if let badge, badge > 0 { Text("\(min(999, badge))").font(.caption2).padding(.horizontal, 6).padding(.vertical, 2).background(.cyan.opacity(0.18), in: Capsule()) } }
            }.buttonStyle(.plain)
        }
    }
}

private struct HCFHeader: View {
    @ObservedObject var model: HCFAppModel
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "pawprint.fill").font(.title3).foregroundStyle(.cyan)
            VStack(alignment: .leading, spacing: 1) {
                Text("Harley's Clan Forum").font(.headline).lineLimit(1)
                Text(model.preferences.activeHost).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer()
            if model.identity.isSignedIn {
                Text("@\(model.identity.username)").font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            Button { model.switchHost() } label: { Image(systemName: "arrow.triangle.2.circlepath") }.accessibilityLabel("Switch forum host")
            Menu {
                Button("Settings", systemImage: "gearshape") { model.settingsPresented = true }
                Button("Diagnostics", systemImage: "stethoscope") { model.diagnosticsPresented = true }
                Button("Updates", systemImage: "arrow.down.circle") { model.updatePresented = true }
            } label: { Image(systemName: "ellipsis.circle") }
        }
        .padding(.horizontal, 12).frame(minHeight: 52)
        .background(.bar)
    }
}

private struct HCFBottomBar: View {
    @ObservedObject var model: HCFAppModel
    var body: some View {
        HStack {
            item("Home", "house.fill") { model.navigate(.home) }
            item("Browse", "rectangle.grid.1x2") { model.navigate(.latest) }
            item("Create", "plus.circle.fill") { model.navigate(.compose) }
            item("Alerts", "bell.fill", badge: model.preferences.unreadCount) { model.navigate(.notifications) }
            item("Profile", "person.crop.circle") { model.navigate(.profile(username: model.identity.username.isEmpty ? nil : model.identity.username)) }
        }
        .padding(.horizontal, 6).padding(.vertical, 5).background(.bar)
    }

    private func item(_ title: String, _ symbol: String, badge: Int? = nil, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 2) {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: symbol).font(.system(size: 18))
                    if let badge, badge > 0 { Text("\(min(999, badge))").font(.system(size: 8, weight: .bold)).padding(3).background(.cyan, in: Circle()).foregroundStyle(.black).offset(x: 9, y: -7) }
                }
                Text(title).font(.caption2)
            }.frame(maxWidth: .infinity)
        }.buttonStyle(.plain).foregroundStyle(.primary)
    }
}

// MARK: - Settings

public struct HCFSettingsView: View {
    @ObservedObject var model: HCFAppModel
    @Environment(\.dismiss) private var dismiss
    @State private var exporting = false
    @State private var importing = false
    @State private var exportDocument = HCFSettingsDocument(archive: .init(preferences: [:]))
    @State private var importError: String?

    public init(model: HCFAppModel) { self.model = model }

    public var body: some View {
        NavigationStack {
            Form {
                Section("Appearance") {
                    Picker("Theme", selection: $model.preferences.theme) {
                        ForEach(HCFThemeMode.allCases) { Text($0.label).tag($0) }
                    }
                    Text("AMOLED uses a true-black native shell. Follow Forum leaves the website's own theme in control.").font(.caption).foregroundStyle(.secondary)
                }
                Section("Forum") {
                    LabeledContent("Active host", value: model.preferences.activeHost)
                    Toggle("Automatic failover", isOn: $model.preferences.autoFailover)
                    Button("Switch host now") { model.switchHost() }
                }
                Section("Notifications") {
                    Toggle("Background forum refresh", isOn: $model.preferences.backgroundNotificationSync)
                    Button("Allow Notifications") { Task { _ = await HCFNotificationCoordinator.shared.requestAuthorization() } }
                    Button("Sync now") { Task { _ = await NotificationSyncService.shared.syncNow(source: "settings") } }
                }
                WidgetSettingsSection(defaults: model.preferences.defaults)
                Section("Updates") {
                    LabeledContent("Installed", value: HCFBuildInfo.displayVersion)
                    Button("Check Dev/Beta channel") { Task { await model.refreshUpdate(); model.updatePresented = true } }
                }
                Section("Settings Transfer") {
                    Button("Export Settings") {
                        exportDocument = HCFSettingsDocument(archive: model.preferences.exportArchive())
                        exporting = true
                    }
                    Button("Import Settings") { importing = true }
                    Text("Authentication cookies, Keychain values, APNs tokens and secrets are never exported.").font(.caption).foregroundStyle(.secondary)
                }
                Section("Support") {
                    Button("Logs & Diagnostics") { dismiss(); model.diagnosticsPresented = true }
                    LabeledContent("Build", value: HCFBuildInfo.displayVersion)
                    LabeledContent("Platform", value: HCFPlatformInfo.systemVersion)
                }
            }
            .navigationTitle("HCF Settings")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .fileExporter(isPresented: $exporting, document: exportDocument, contentType: .hcfSettings, defaultFilename: "HCF-Settings-\(HCFBuildInfo.channelVersion)") { _ in }
            .fileImporter(isPresented: $importing, allowedContentTypes: [.hcfSettings, .json]) { result in
                do {
                    let url = try result.get()
                    let accessed = url.startAccessingSecurityScopedResource()
                    defer { if accessed { url.stopAccessingSecurityScopedResource() } }
                    let data = try Data(contentsOf: url)
                    let decoder = JSONDecoder(); decoder.dateDecodingStrategy = .iso8601
                    try model.preferences.importArchive(decoder.decode(HCFSettingsArchive.self, from: data))
                } catch { importError = error.localizedDescription }
            }
            .alert("Settings Import Failed", isPresented: Binding(get: { importError != nil }, set: { if !$0 { importError = nil } })) { Button("OK") { importError = nil } } message: { Text(importError ?? "Unknown error") }
        }
    }
}

private struct WidgetSettingsSection: View {
    let defaults: UserDefaults
    @State private var showUsername: Bool
    @State private var showUnread: Bool
    @State private var compact: Bool
    @State private var showUpdated: Bool
    @State private var showPreview: Bool
    @State private var alpha: Double
    @State private var textSize: Double
    @State private var defaultTap: HCFWidgetTapAction
    @State private var history: HCFNotificationHistoryMode
    @State private var historyLimit: Int

    init(defaults: UserDefaults) {
        self.defaults = defaults
        _showUsername = State(initialValue: defaults.object(forKey: PreferencesStore.Key.widgetShowUsername) as? Bool ?? true)
        _showUnread = State(initialValue: defaults.object(forKey: PreferencesStore.Key.widgetShowUnread) as? Bool ?? true)
        _compact = State(initialValue: defaults.object(forKey: PreferencesStore.Key.widgetCompact) as? Bool ?? false)
        _showUpdated = State(initialValue: defaults.object(forKey: PreferencesStore.Key.widgetShowUpdated) as? Bool ?? true)
        _showPreview = State(initialValue: defaults.object(forKey: PreferencesStore.Key.widgetShowPreview) as? Bool ?? true)
        _alpha = State(initialValue: Double(defaults.object(forKey: PreferencesStore.Key.widgetBackgroundAlpha) as? Int ?? 96))
        _textSize = State(initialValue: Double(defaults.object(forKey: PreferencesStore.Key.widgetTextSize) as? Int ?? 12))
        _defaultTap = State(initialValue: HCFWidgetTapAction(rawValue: defaults.string(forKey: PreferencesStore.Key.widgetDefaultTap) ?? "forum") ?? .forum)
        _history = State(initialValue: HCFNotificationHistoryMode(rawValue: defaults.string(forKey: PreferencesStore.Key.historyMode) ?? "title") ?? .title)
        _historyLimit = State(initialValue: defaults.object(forKey: PreferencesStore.Key.historyLimit) as? Int ?? 30)
    }

    var body: some View {
        Section("Home Screen Widget") {
            Toggle("Show connected username", isOn: bind($showUsername, PreferencesStore.Key.widgetShowUsername))
            Toggle("Show unread count", isOn: bind($showUnread, PreferencesStore.Key.widgetShowUnread))
            Toggle("Compact layout", isOn: bind($compact, PreferencesStore.Key.widgetCompact))
            Toggle("Show last updated", isOn: bind($showUpdated, PreferencesStore.Key.widgetShowUpdated))
            Toggle("Show last notification preview", isOn: bind($showPreview, PreferencesStore.Key.widgetShowPreview))
            VStack(alignment: .leading) { Text("Background opacity • \(Int(alpha))%"); Slider(value: bindDouble($alpha, PreferencesStore.Key.widgetBackgroundAlpha), in: 20...100, step: 1) }
            VStack(alignment: .leading) { Text("Text size • \(Int(textSize)) pt"); Slider(value: bindDouble($textSize, PreferencesStore.Key.widgetTextSize), in: 10...18, step: 1) }
            Picker("Default tap", selection: Binding(get: { defaultTap }, set: { defaultTap = $0; defaults.set($0.rawValue, forKey: PreferencesStore.Key.widgetDefaultTap) })) { ForEach(HCFWidgetTapAction.allCases) { Text($0.label).tag($0) } }
            Picker("Notification history", selection: Binding(get: { history }, set: { history = $0; defaults.set($0.rawValue, forKey: PreferencesStore.Key.historyMode) })) { ForEach(HCFNotificationHistoryMode.allCases) { Text($0.label).tag($0) } }
            Picker("History retention", selection: Binding(get: { historyLimit }, set: { historyLimit = $0; defaults.set($0, forKey: PreferencesStore.Key.historyLimit) })) { Text("10 events").tag(10); Text("30 events").tag(30); Text("60 events").tag(60) }
        }
    }

    private func bind(_ state: Binding<Bool>, _ key: String) -> Binding<Bool> { Binding(get: { state.wrappedValue }, set: { state.wrappedValue = $0; defaults.set($0, forKey: key) }) }
    private func bindDouble(_ state: Binding<Double>, _ key: String) -> Binding<Double> { Binding(get: { state.wrappedValue }, set: { state.wrappedValue = $0; defaults.set(Int($0), forKey: key) }) }
}

// MARK: - Updates and diagnostics

public struct HCFUpdateView: View {
    @ObservedObject var model: HCFAppModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    public init(model: HCFAppModel) { self.model = model }

    public var body: some View {
        NavigationStack {
            List {
                Section("Dev/Beta Channel") {
                    LabeledContent("Installed", value: HCFBuildInfo.displayVersion)
                    if let result = model.updateResult {
                        LabeledContent("Status", value: result.summary)
                        if let available = result.availableBuild { LabeledContent("Available build", value: "\(available)") }
                        if let name = result.releaseName { Text(name).font(.headline) }
                        if let notes = result.notes { Text(notes).font(.footnote).textSelection(.enabled) }
                        if let destination = HCFUpdateChecker.shared.preferredInstallURL(for: result) {
                            Button(HCFBuildInfo.testFlightURL == nil ? "Open GitHub Release" : "Open TestFlight") { openURL(destination) }
                        }
                    } else { ProgressView("Checking…") }
                }
                Section { Text("iOS does not allow HCF to silently download and install an IPA. Dev builds are delivered through TestFlight; App Store builds update through the App Store.").font(.caption).foregroundStyle(.secondary) }
            }
            .navigationTitle("App Updates")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .task { await model.refreshUpdate() }
        }
    }
}

public struct HCFDiagnosticsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var events: [DiagnosticEvent] = []
    public init() {}

    public var body: some View {
        NavigationStack {
            List {
                Section("Runtime") {
                    LabeledContent("Version", value: HCFBuildInfo.displayVersion)
                    LabeledContent("OS", value: HCFPlatformInfo.systemVersion)
                    LabeledContent("Thermal", value: HCFPlatformInfo.thermalState)
                    LabeledContent("Low Power", value: HCFPlatformInfo.lowPowerModeEnabled ? "On" : "Off")
                }
                Section("Sanitized Log") {
                    if events.isEmpty { Text("No diagnostic events recorded this launch.").foregroundStyle(.secondary) }
                    ForEach(events.reversed()) { event in
                        VStack(alignment: .leading, spacing: 3) {
                            HStack { Text(event.level).font(.caption.bold()); Text(event.code).font(.caption.monospaced()); Spacer(); Text(event.date, style: .time).font(.caption2) }
                            if !event.detail.isEmpty { Text(event.detail).font(.caption2).foregroundStyle(.secondary).textSelection(.enabled) }
                        }
                    }
                }
                Section { Text("Diagnostics are sanitized. HCF does not intentionally include cookies, authorization tokens, passwords, webhook URLs, or notification/reply message content in diagnostic events.").font(.caption).foregroundStyle(.secondary) }
            }
            .navigationTitle("Logs & Diagnostics")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("Clear") { Task { await DiagnosticLogger.shared.clear(); events = [] } } }
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
            .task { events = await DiagnosticLogger.shared.snapshot() }
        }
    }
}

public struct HCFSettingsDocument: FileDocument {
    public static var readableContentTypes: [UTType] { [.hcfSettings, .json] }
    public var archive: HCFSettingsArchive
    public init(archive: HCFSettingsArchive) { self.archive = archive }
    public init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents else { throw CocoaError(.fileReadCorruptFile) }
        let decoder = JSONDecoder(); decoder.dateDecodingStrategy = .iso8601
        archive = try decoder.decode(HCFSettingsArchive.self, from: data)
    }
    public func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        let encoder = JSONEncoder(); encoder.outputFormatting = [.prettyPrinted, .sortedKeys]; encoder.dateEncodingStrategy = .iso8601
        return FileWrapper(regularFileWithContents: try encoder.encode(archive))
    }
}

public extension UTType {
    static let hcfSettings = UTType(exportedAs: "com.harleytg.hcf.settings", conformingTo: .json)
}
