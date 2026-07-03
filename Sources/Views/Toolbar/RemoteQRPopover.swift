// MARK: - RemoteQRPopover
// QR code popover for Remote Control auto-login URL.
// macOS 14+, Swift 5.10

import AppKit
import CoreImage.CIFilterBuiltins
import SwiftUI

/// Popover content showing a scannable QR code with the auto-login URL.
///
/// Visible when the user taps the toolbar `qrcode` button (`RegularToolbarView`
/// / `CodeSpeakToolbarView`). The URL embeds the current PIN so the iPhone
/// authenticates on first connect without typing it in.
struct RemoteQRPopover: View {

    let url: String

    var body: some View {
        VStack(spacing: DSSpacing.md) {
            if let qrImage = Self.generateQRCode(from: url) {
                Image(nsImage: qrImage)
                    .interpolation(.none)
                    .resizable()
                    .scaledToFit()
                    .frame(width: DSLayout.qrCodeSize, height: DSLayout.qrCodeSize)
            }

            Text(url)
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textSecondary)
                .textSelection(.enabled)
                .lineLimit(2)
                .multilineTextAlignment(.center)

            Button {
                ClipboardService.copy(url)
            } label: {
                HStack(spacing: DSSpacing.xs) {
                    Image(systemName: "doc.on.doc")
                        .font(DSFont.iconSM)
                    Text("Copy URL")
                        .font(DSFont.smallButtonLabel)
                }
                .foregroundStyle(DSColor.accentPrimary)
            }
            .buttonStyle(.plain)

            Text("Scan with your phone camera")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted)
        }
        .padding(DSSpacing.md)
        .frame(width: DSLayout.qrPopoverWidth)
        .background(DSColor.surfaceOverlay)
    }

    // MARK: - QR Generation

    /// Generate an `NSImage` QR code from a string using CoreImage.
    private static func generateQRCode(from string: String) -> NSImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(string.utf8)
        filter.correctionLevel = "M"

        guard let ciImage = filter.outputImage else { return nil }

        let scale = DSLayout.qrCodeSize / ciImage.extent.width
        let scaled = ciImage.transformed(by: CGAffineTransform(scaleX: scale, y: scale))

        let rep = NSCIImageRep(ciImage: scaled)
        let nsImage = NSImage(size: rep.size)
        nsImage.addRepresentation(rep)
        return nsImage
    }
}
