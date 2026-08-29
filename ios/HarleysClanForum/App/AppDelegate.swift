import UIKit
import HCFCore
import HCFNotifications

final class HCFAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        HCFNotificationCoordinator.shared.configure()
        Task { await DiagnosticLogger.shared.info("app_launch", HCFBuildInfo.displayVersion) }
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        HCFNotificationCoordinator.shared.registerAPNsToken(deviceToken)
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        Task { await DiagnosticLogger.shared.warning("apns_registration", error.localizedDescription) }
    }

    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        Task {
            let result = await HCFNotificationCoordinator.shared.handleRemoteNotification(userInfo)
            completionHandler(result)
        }
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        HCFNotificationCoordinator.shared.scheduleRefresh()
    }
}
