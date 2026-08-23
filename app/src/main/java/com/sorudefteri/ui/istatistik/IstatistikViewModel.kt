package com.sorudefteri.ui.istatistik

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sorudefteri.data.repository.SoruRepository
import com.sorudefteri.domain.model.DersSoruSayisi
import com.sorudefteri.domain.model.GunlukAktivite
import com.sorudefteri.domain.model.HataTipiDagilimi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class IstatistikUiState(
    val totalCount: Int = 0,
    val solvedCount: Int = 0,
    val unsolvedCount: Int = 0,
    val successRate: Float = 0f,
    val subjectStats: List<DersSoruSayisi> = emptyList(),
    val mistakeStats: List<HataTipiDagilimi> = emptyList(),
    val weeklyActivity: List<GunlukAktivite> = emptyList(),
    val isLoading: Boolean = false
) {
    val isEmpty: Boolean get() = totalCount == 0 && !isLoading
}

@HiltViewModel
class IstatistikViewModel @Inject constructor(
    private val soruRepository: SoruRepository
) : ViewModel() {

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<IstatistikUiState> = combine(
        soruRepository.totalCount,
        soruRepository.solvedCount,
        soruRepository.unsolvedCount,
        soruRepository.getSubjectStats(),
        soruRepository.getMistakeStats(),
        soruRepository.getWeeklyActivity()
    ) { args: Array<Any?> ->
        val total = args[0] as? Int ?: 0
        val solved = args[1] as? Int ?: 0
        val unsolved = args[2] as? Int ?: 0
        val subjects = args[3] as? List<DersSoruSayisi> ?: emptyList()
        val mistakes = args[4] as? List<HataTipiDagilimi> ?: emptyList()
        val weekly = args[5] as? List<GunlukAktivite> ?: emptyList()

        val rate = if (total > 0) (solved.toFloat() / total.toFloat()) * 100f else 0f
        IstatistikUiState(
            totalCount = total,
            solvedCount = solved,
            unsolvedCount = unsolved,
            successRate = rate,
            subjectStats = subjects,
            mistakeStats = mistakes,
            weeklyActivity = weekly,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = IstatistikUiState(isLoading = true)
    )
}
