//
//  AppReadyState.swift
//  VibeStudio
//
//  Created by VibeStudio on 2026-03-26.
//

import Foundation
import SwiftUI

/// Tracks the app's readiness state after launch.
/// Used to coordinate initialization of services and UI components.
@Observable
@MainActor
final class AppReadyState {
    /// Whether the app has finished initial setup and is ready to display content.
    var isReady: Bool = false
    
    /// Whether TCC (Transparency, Consent, and Control) permissions have been granted
    /// for required system access (terminal input, file access, etc.).
    var tccGranted: Bool = false
    
    /// Mark the app as ready.
    func markReady() {
        isReady = true
    }
    
    /// Mark TCC permissions as granted.
    func markTCCGranted() {
        tccGranted = true
    }
}
