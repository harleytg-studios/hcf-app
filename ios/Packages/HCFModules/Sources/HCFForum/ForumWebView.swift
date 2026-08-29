import SwiftUI
import WebKit
import HCFCore

@MainActor
public final class ForumBrowserState: ObservableObject {
    @Published public var currentURL: URL?
    @Published public var title = "Harley's Clan Forum"
    @Published public var isLoading = false
    @Published public var progress = 0.0
    @Published public var canGoBack = false
    @Published public var canGoForward = false
    @Published public var lastError: String?
    @Published public var identity: ForumIdentity = .guest()
    @Published public var navigationRequest: URL?
    @Published public var reloadGeneration = 0

    public init() {}

    public func navigate(to url: URL) { navigationRequest = url }
    public func reload() { reloadGeneration &+= 1 }
}

public struct ForumWebView: UIViewRepresentable {
    @ObservedObject private var state: ForumBrowserState
    private let initialURL: URL
    private let router: ForumRouter
    private let session: ForumSessionManager
    private let onExternalURL: @MainActor (URL) -> Void
    private let onIdentity: @MainActor (ForumIdentity) -> Void

    public init(
        state: ForumBrowserState,
        initialURL: URL,
        router: ForumRouter,
        session: ForumSessionManager = .shared,
        onExternalURL: @escaping @MainActor (URL) -> Void,
        onIdentity: @escaping @MainActor (ForumIdentity) -> Void = { _ in }
    ) {
        self.state = state
        self.initialURL = initialURL
        self.router = router
        self.session = session
        self.onExternalURL = onExternalURL
        self.onIdentity = onIdentity
    }

    public func makeCoordinator() -> Coordinator {
        Coordinator(state: state, router: router, session: session, onExternalURL: onExternalURL, onIdentity: onIdentity)
    }

    public func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()
        configuration.defaultWebpagePreferences.allowsContentJavaScript = true
        configuration.userContentController.add(context.coordinator, name: Coordinator.identityHandler)
        configuration.userContentController.addUserScript(WKUserScript(
            source: Coordinator.identityBridgeJavaScript,
            injectionTime: .atDocumentEnd,
            forMainFrameOnly: true
        ))
        let webView = WKWebView(frame: .zero, configuration: configuration)
        context.coordinator.webView = webView
        webView.navigationDelegate = context.coordinator
        webView.uiDelegate = context.coordinator
        webView.allowsBackForwardNavigationGestures = true
        webView.allowsLinkPreview = true
        webView.isOpaque = false
        webView.scrollView.keyboardDismissMode = .interactive
        webView.configuration.applicationNameForUserAgent = HCFBuildInfo.userAgentMarker + " NativeApp"
        context.coordinator.installObservers(on: webView)

        Task { @MainActor in
            await session.restoreCookies()
            guard webView.url == nil else { return }
            webView.load(URLRequest(url: initialURL, cachePolicy: .useProtocolCachePolicy, timeoutInterval: 30))
        }
        return webView
    }

    public func updateUIView(_ webView: WKWebView, context: Context) {
        if let requested = state.navigationRequest, requested != webView.url, router.isTrusted(requested) {
            state.navigationRequest = nil
            webView.load(URLRequest(url: requested))
        }
        if context.coordinator.lastReloadGeneration != state.reloadGeneration {
            context.coordinator.lastReloadGeneration = state.reloadGeneration
            webView.reload()
        }
    }

    public static func dismantleUIView(_ webView: WKWebView, coordinator: Coordinator) {
        coordinator.removeObservers()
        webView.configuration.userContentController.removeScriptMessageHandler(forName: Coordinator.identityHandler)
        Task { @MainActor in await coordinator.session.persistCookies() }
        webView.navigationDelegate = nil
        webView.uiDelegate = nil
    }

    @MainActor
    public final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler {
        static let identityHandler = "hcfIdentity"
        static let identityBridgeJavaScript = #"""
        (() => {
          try {
            const model = window.flarum && flarum.session ? flarum.session.user : null;
            if (!model) {
              window.webkit.messageHandlers.hcfIdentity.postMessage({ loggedIn: false });
              return;
            }
            const read = (key, fallback = null) => {
              try {
                if (typeof model.attribute === 'function') {
                  const v = model.attribute(key);
                  if (v !== undefined && v !== null) return v;
                }
                const p = model[key];
                if (typeof p === 'function') {
                  const v = p.call(model);
                  if (v !== undefined && v !== null) return v;
                }
                if (p !== undefined && p !== null) return p;
              } catch (_) {}
              return fallback;
            };
            const groups = (() => {
              try {
                const value = typeof model.groups === 'function' ? model.groups() : [];
                return Array.isArray(value) ? value.map(g => {
                  try { return g.attribute ? (g.attribute('nameSingular') || g.attribute('namePlural') || '') : ''; }
                  catch (_) { return ''; }
                }).filter(Boolean) : [];
              } catch (_) { return []; }
            })();
            const payload = {
              loggedIn: true,
              id: String(model.id ? model.id() : read('id', '')),
              username: String(read('username', '')),
              displayName: String(read('displayName', read('username', ''))),
              avatarUrl: String(read('avatarUrl', '')),
              isAdmin: groups.some(g => String(g).toLowerCase().includes('admin')),
              groups
            };
            window.webkit.messageHandlers.hcfIdentity.postMessage(payload);
          } catch (_) {
            window.webkit.messageHandlers.hcfIdentity.postMessage({ loggedIn: false });
          }
        })();
        """#

        let state: ForumBrowserState
        let router: ForumRouter
        let session: ForumSessionManager
        let onExternalURL: @MainActor (URL) -> Void
        let onIdentity: @MainActor (ForumIdentity) -> Void
        weak var webView: WKWebView?
        var progressObservation: NSKeyValueObservation?
        var urlObservation: NSKeyValueObservation?
        var lastReloadGeneration = 0

        init(
            state: ForumBrowserState,
            router: ForumRouter,
            session: ForumSessionManager,
            onExternalURL: @escaping @MainActor (URL) -> Void,
            onIdentity: @escaping @MainActor (ForumIdentity) -> Void
        ) {
            self.state = state
            self.router = router
            self.session = session
            self.onExternalURL = onExternalURL
            self.onIdentity = onIdentity
        }

        func installObservers(on webView: WKWebView) {
            progressObservation = webView.observe(\.estimatedProgress, options: [.initial, .new]) { [weak self] view, _ in
                Task { @MainActor in self?.state.progress = view.estimatedProgress }
            }
            urlObservation = webView.observe(\.url, options: [.initial, .new]) { [weak self] view, _ in
                Task { @MainActor in
                    self?.state.currentURL = view.url
                    self?.state.canGoBack = view.canGoBack
                    self?.state.canGoForward = view.canGoForward
                }
            }
        }

        func removeObservers() {
            progressObservation?.invalidate(); progressObservation = nil
            urlObservation?.invalidate(); urlObservation = nil
        }

        public func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            state.isLoading = true
            state.lastError = nil
            state.currentURL = webView.url
        }

        public func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            state.isLoading = false
            state.progress = 1
            state.title = webView.title ?? "Harley's Clan Forum"
            state.currentURL = webView.url
            state.canGoBack = webView.canGoBack
            state.canGoForward = webView.canGoForward
            Task { @MainActor in await session.persistCookies() }
            webView.evaluateJavaScript(Self.identityBridgeJavaScript)
        }

        public func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            state.isLoading = false
            state.lastError = error.localizedDescription
            Task { await DiagnosticLogger.shared.warning("webview_navigation", error.localizedDescription) }
        }

        public func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            state.isLoading = false
            state.lastError = error.localizedDescription
            Task { await DiagnosticLogger.shared.warning("webview_provisional", error.localizedDescription) }
        }

        public func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
            state.lastError = "The forum renderer restarted. Reloading safely."
            Task { await DiagnosticLogger.shared.warning("webview_process", "terminated; reloading") }
            webView.reload()
        }

        public func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
        ) {
            guard let url = navigationAction.request.url else { decisionHandler(.cancel); return }
            if url.scheme == "about" || url.scheme == "blob" { decisionHandler(.allow); return }
            if router.isTrusted(url) {
                decisionHandler(.allow)
                return
            }
            if url.scheme?.lowercased() == "http" || url.scheme?.lowercased() == "https" {
                onExternalURL(url)
                decisionHandler(.cancel)
                return
            }
            if ["mailto", "tel", "sms"].contains(url.scheme?.lowercased() ?? "") {
                onExternalURL(url)
                decisionHandler(.cancel)
                return
            }
            decisionHandler(.cancel)
        }

        public func webView(
            _ webView: WKWebView,
            createWebViewWith configuration: WKWebViewConfiguration,
            for navigationAction: WKNavigationAction,
            windowFeatures: WKWindowFeatures
        ) -> WKWebView? {
            if navigationAction.targetFrame == nil, let url = navigationAction.request.url {
                if router.isTrusted(url) { webView.load(URLRequest(url: url)) }
                else { onExternalURL(url) }
            }
            return nil
        }

        public func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            guard message.name == Self.identityHandler, let object = message.body as? [String: Any] else { return }
            let signedIn = object["loggedIn"] as? Bool ?? false
            let host = webView?.url?.host ?? HCFBuildInfo.primaryHost
            let identity: ForumIdentity
            if signedIn {
                identity = .init(
                    id: String(describing: object["id"] ?? ""),
                    username: String(describing: object["username"] ?? ""),
                    displayName: String(describing: object["displayName"] ?? object["username"] ?? "Member"),
                    avatarURL: URL(string: String(describing: object["avatarUrl"] ?? "")),
                    isAdmin: object["isAdmin"] as? Bool ?? false,
                    isSignedIn: true,
                    host: host
                )
            } else {
                identity = .guest(host: host)
            }
            state.identity = identity
            onIdentity(identity)
            Task { @MainActor in await session.saveIdentity(identity) }
        }
    }
}
