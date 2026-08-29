import SwiftUI
import HCFUI

@main
struct HarleysClanForumApp: App {
    @UIApplicationDelegateAdaptor(HCFAppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            HCFRootView()
        }
    }
}
