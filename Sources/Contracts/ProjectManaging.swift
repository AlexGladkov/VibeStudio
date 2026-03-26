// MARK: - ProjectManaging Protocol
// Хранение и управление списком проектов.
// macOS 14+, Swift 5.10

import Foundation

// MARK: - Protocol

/// Управление списком проектов приложения.
///
/// Реализация ДОЛЖНА быть `@Observable` и работать на MainActor,
/// потому что UI напрямую наблюдает за `projects` и `activeProjectId`.
///
/// Персистенция: ~/Library/Application Support/VibeStudio/projects.json
@MainActor
protocol ProjectManaging: AnyObject, Observable {

    // MARK: - Observable State

    /// Все известные проекты, отсортированные по порядку табов.
    var projects: [Project] { get }

    /// ID активного проекта (выбранный таб).
    var activeProjectId: UUID? { get set }

    /// Полная история недавних проектов (до 10), отсортированные по lastOpened desc.
    var recentHistory: [Project] { get }

    /// Последние открытые проекты (до 10), отсортированные по lastOpened desc.
    /// Фильтрует проекты, которые уже открыты в сайдбаре.
    var recentProjects: [Project] { get }

    // MARK: - CRUD

    /// Открыть папку и добавить как проект.
    ///
    /// - Parameter path: Абсолютный путь к директории проекта.
    /// - Returns: Созданный проект.
    /// - Throws: `ProjectManagerError.invalidPath` если путь не директория,
    ///           `ProjectManagerError.duplicate` если проект уже добавлен.
    @discardableResult
    func addProject(at path: URL) throws -> Project

    /// Удалить проект из списка (не удаляет файлы с диска).
    ///
    /// - Parameter id: ID проекта.
    /// - Throws: `ProjectManagerError.notFound` если проект не существует.
    func removeProject(_ id: UUID) throws

    /// Обновить свойства проекта (имя, цвет, shell).
    ///
    /// - Parameters:
    ///   - id: ID проекта.
    ///   - mutate: Замыкание для мутации проекта.
    /// - Throws: `ProjectManagerError.notFound` если проект не существует.
    func updateProject(_ id: UUID, _ mutate: (inout Project) -> Void) throws

    /// Переупорядочить табы. Indices -- текущие позиции, destination -- новая.
    func moveProjects(from indices: IndexSet, to destination: Int)

    // MARK: - Lookup

    /// Найти проект по ID. O(1) -- реализация должна держать словарь.
    func project(for id: UUID) -> Project?

    /// Найти проект по пути на диске.
    func project(at path: URL) -> Project?

    // MARK: - Lifecycle

    /// Загрузить список проектов с диска. Вызывается при старте приложения.
    func load() throws

    /// Сохранить текущий список на диск. Вызывается автоматически при мутациях,
    /// но можно вызвать явно.
    func save() throws
}

// MARK: - Convenience defaults

extension ProjectManaging {
    /// Активный проект (вычисляемый для удобства).
    var activeProject: Project? {
        guard let id = activeProjectId else { return nil }
        return project(for: id)
    }
}
