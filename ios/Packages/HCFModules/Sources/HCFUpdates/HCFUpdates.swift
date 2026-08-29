import Foundation
import HCFCore

public struct HCFGitHubAsset: Codable, Sendable, Identifiable {
    public let id: Int
    public let name: String
    public let browserDownloadURL: URL
    public let size: Int

    enum CodingKeys: String, CodingKey {
        case id, name, size
        case browserDownloadURL = "browser_download_url"
    }
}

public struct HCFGitHubRelease: Codable, Sendable, Identifiable {
    public let id: Int
    public let tagName: String
    public let name: String?
    public let body: String?
    public let htmlURL: URL
    public let prerelease: Bool
    public let draft: Bool
    public let publishedAt: Date?
    public let assets: [HCFGitHubAsset]

    enum CodingKeys: String, CodingKey {
        case id, name, body, prerelease, draft, assets
        case tagName = "tag_name"
        case htmlURL = "html_url"
        case publishedAt = "published_at"
    }

    public var parsedBuildNumber: Int? {
        guard let body else { return nil }
        let patterns = [
            #"(?i)version\s*code\s*[:=]?\s*`?(\d{6,})"#,
            #"(?i)build\s*(?:number)?\s*[:=]?\s*`?(\d{6,})"#,
            #"\((\d{6,})\)"#
        ]
        for pattern in patterns {
            if let regex = try? NSRegularExpression(pattern: pattern),
               let match = regex.firstMatch(in: body, range: NSRange(body.startIndex..., in: body)),
               match.numberOfRanges > 1,
               let range = Range(match.range(at: 1), in: body),
               let number = Int(body[range]) { return number }
        }
        return nil
    }
}

public struct HCFUpdateResult: Sendable, Equatable {
    public enum State: Sendable, Equatable { case current, available, unavailable }
    public let state: State
    public let currentTag: String
    public let availableTag: String?
    public let currentBuild: Int
    public let availableBuild: Int?
    public let releaseURL: URL?
    public let releaseName: String?
    public let notes: String?

    public var summary: String {
        switch state {
        case .current: "Current • \(currentTag) (\(currentBuild))"
        case .available: "Dev/Beta update • \(availableTag ?? "new release")"
        case .unavailable: "Update service unavailable"
        }
    }
}

public actor HCFUpdateChecker {
    public static let shared = HCFUpdateChecker()
    private let client: HTTPClient
    private var lastResult: HCFUpdateResult?
    private var checkedAt: Date?

    public init(client: HTTPClient = .shared) { self.client = client }

    public func check(force: Bool = false) async -> HCFUpdateResult {
        if !force, let checkedAt, let lastResult, Date().timeIntervalSince(checkedAt) < 15 * 60 { return lastResult }
        do {
            let response = try await client.request(HCFBuildInfo.releasesURL, headers: [
                "Accept": "application/vnd.github+json",
                "X-GitHub-Api-Version": "2022-11-28",
                "User-Agent": HCFBuildInfo.userAgentMarker + " UpdateChecker"
            ])
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            let releases = try decoder.decode([HCFGitHubRelease].self, from: response.data)
            guard let release = releases.first(where: { !$0.draft && $0.prerelease }) else {
                let result = HCFUpdateResult(
                    state: .current,
                    currentTag: HCFBuildInfo.channelTag,
                    availableTag: nil,
                    currentBuild: HCFBuildInfo.buildNumber,
                    availableBuild: nil,
                    releaseURL: nil,
                    releaseName: nil,
                    notes: nil
                )
                cache(result)
                return result
            }

            let availableBuild = release.parsedBuildNumber
            let tagChanged = normalized(release.tagName) != normalized(HCFBuildInfo.channelTag)
            let buildChanged = availableBuild.map { $0 > HCFBuildInfo.buildNumber } ?? false
            let available = tagChanged || buildChanged
            let result = HCFUpdateResult(
                state: available ? .available : .current,
                currentTag: HCFBuildInfo.channelTag,
                availableTag: release.tagName,
                currentBuild: HCFBuildInfo.buildNumber,
                availableBuild: availableBuild,
                releaseURL: release.htmlURL,
                releaseName: release.name,
                notes: release.body.map { String($0.prefix(4_000)) }
            )
            cache(result)
            await DiagnosticLogger.shared.info("update_check", result.summary)
            return result
        } catch {
            await DiagnosticLogger.shared.warning("update_check", error.localizedDescription)
            let result = HCFUpdateResult(
                state: .unavailable,
                currentTag: HCFBuildInfo.channelTag,
                availableTag: nil,
                currentBuild: HCFBuildInfo.buildNumber,
                availableBuild: nil,
                releaseURL: nil,
                releaseName: nil,
                notes: nil
            )
            cache(result)
            return result
        }
    }

    public nonisolated func preferredInstallURL(for result: HCFUpdateResult) -> URL? {
        // iOS distribution remains under Apple control. TestFlight is preferred;
        // GitHub is used for version discovery/release notes only.
        HCFBuildInfo.testFlightURL ?? result.releaseURL
    }

    private func normalized(_ value: String) -> String {
        value.lowercased().trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "v"))
    }

    private func cache(_ result: HCFUpdateResult) {
        lastResult = result
        checkedAt = .now
    }
}
