import XCTest
import HCFCore
import HCFForum
import HCFUpdates

final class HCFIntegrationTests: XCTestCase {
    func testDomainRegistryParsesAndroidConfigShape() {
        let source = """
        [config]
        https_only=true
        preserve_path=true
        preserve_query=true
        preserve_fragment=true

        [primary]
        domain=forum.harleytg.com

        [backups]
        domain_1=harleysclan.freeflarum.com
        """
        let registry = DomainRegistryService.parse(source)
        XCTAssertEqual(registry.primary, "forum.harleytg.com")
        XCTAssertEqual(registry.backups, ["harleysclan.freeflarum.com"])
        XCTAssertTrue(registry.httpsOnly)
        XCTAssertTrue(registry.trustedHosts.contains("forum.harleytg.com"))
        XCTAssertTrue(registry.trustedHosts.contains("harleysclan.freeflarum.com"))
    }

    func testRouterPreservesDiscussionRouteAcrossHosts() throws {
        let router = ForumRouter()
        let original = try XCTUnwrap(URL(string: "https://forum.harleytg.com/d/27-test?near=4#reply"))
        let result = router.equivalent(original, on: "harleysclan.freeflarum.com")
        XCTAssertEqual(result.host, "harleysclan.freeflarum.com")
        XCTAssertEqual(result.path, "/d/27-test")
        XCTAssertEqual(result.query, "near=4")
        XCTAssertEqual(result.fragment, "reply")
    }

    func testSettingsTransferNeverIncludesSessionKeys() {
        XCTAssertFalse(PreferencesStore.transferableKeys.contains(PreferencesStore.Key.sessionUserID))
        XCTAssertFalse(PreferencesStore.transferableKeys.contains(PreferencesStore.Key.lastNotificationCount))
        XCTAssertFalse(PreferencesStore.transferableKeys.contains("cookies"))
        XCTAssertFalse(PreferencesStore.transferableKeys.contains("token"))
    }

    func testSHA256MatchesKnownVector() {
        XCTAssertEqual(
            HCFHash.sha256Hex("HarleysClanForum"),
            "80be5f0239dcf13a13b595178d4d215e864ede25607882fd3757c5fcef72e292"
        )
    }

    func testBuildIdentityMatchesDevBaseline() {
        XCTAssertEqual(HCFBuildInfo.buildNumber, 100000105)
        XCTAssertEqual(HCFBuildInfo.channelVersion, "1.1-hf2-a1")
    }
}
