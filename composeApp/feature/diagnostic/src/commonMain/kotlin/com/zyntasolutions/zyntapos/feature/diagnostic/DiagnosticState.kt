package com.zyntasolutions.zyntapos.feature.diagnostic

import com.zyntasolutions.zyntapos.domain.model.DiagnosticSession
import com.zyntasolutions.zyntapos.domain.model.DiagnosticSessionStatus

data class DiagnosticState(
    val pendingSession: DiagnosticSession? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
