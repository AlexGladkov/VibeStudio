package studio.vibe.shared.core.common

/**
 * Contract for a use case that accepts parameters and returns a [Result]-wrapped value.
 *
 * All implementations must NOT throw — every exception must be caught inside
 * [invoke] and surfaced as [Result.failure].
 */
interface UseCase<in P, out R> {
    suspend operator fun invoke(params: P): Result<R>
}

/**
 * Contract for a use case that requires no input parameters.
 *
 * All implementations must NOT throw — every exception must be caught inside
 * [invoke] and surfaced as [Result.failure].
 */
interface NoParamsUseCase<out R> {
    suspend operator fun invoke(): Result<R>
}
