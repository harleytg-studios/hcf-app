import SwiftUI
import WidgetKit
import HCFWidget

@main
struct HCFWidgetBundle: WidgetBundle {
    var body: some Widget {
        HCFNotificationsWidgetDefinition()
        HCFUnreadWidgetDefinition()
    }
}
