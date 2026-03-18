package com.zyntasolutions.zyntapos.feature.media

import com.zyntasolutions.zyntapos.domain.usecase.media.DeleteMediaFileUseCase
import com.zyntasolutions.zyntapos.domain.usecase.media.GetMediaForEntityUseCase
import com.zyntasolutions.zyntapos.domain.usecase.media.SaveMediaFileUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin DI module for the :composeApp:feature:media feature (Sprints 16–17).
 *
 * ### Use-Case Registrations (factory — new instance per injection)
 * - [GetMediaForEntityUseCase] — reactive media stream for an entity
 * - [SaveMediaFileUseCase] — insert validated media record
 * - [DeleteMediaFileUseCase] — soft-delete a media record
 *
 * ### ViewModels
 * - [MediaViewModel] — entity-scoped media library, add/primary/delete
 */
val mediaModule = module {

    // ── Media use cases ───────────────────────────────────────────────────
    factoryOf(::GetMediaForEntityUseCase)
    factoryOf(::SaveMediaFileUseCase)
    factoryOf(::DeleteMediaFileUseCase)

    // ── MediaViewModel ────────────────────────────────────────────────────
    viewModel {
        MediaViewModel(
            authRepository = get(),
            getMediaForEntityUseCase = get(),
            saveMediaFileUseCase = get(),
            deleteMediaFileUseCase = get(),
            mediaRepository = get(),
            analytics = get(),
        )
    }
}
