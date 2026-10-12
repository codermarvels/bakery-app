package com.vsjbakery.app.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vsjbakery.app.data.RemoteErrors
import com.vsjbakery.app.data.reports.ReportsRepository
import com.vsjbakery.app.domain.model.ReportPreset
import com.vsjbakery.app.domain.model.ReportRange
import com.vsjbakery.app.domain.model.SalesReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.temporal.ChronoUnit

data class ReportsState(
    val preset: ReportPreset = ReportPreset.TODAY,
    val range: ReportRange = ReportRange.forPreset(ReportPreset.TODAY, ReportRange.today()),
    val isLoading: Boolean = false,
    val report: SalesReport? = null,
    val errorMessage: String? = null,
)

/** Loads read-only sales reports. All numbers are calculated by the server. */
class ReportsViewModel(private val repository: ReportsRepository?) : ViewModel() {

    private val _state = MutableStateFlow(ReportsState())
    val state: StateFlow<ReportsState> = _state.asStateFlow()
    private var job: Job? = null

    fun choosePreset(preset: ReportPreset) {
        if (preset == ReportPreset.CUSTOM) return // the screen opens the date picker instead
        _state.update { it.copy(preset = preset, range = ReportRange.forPreset(preset, ReportRange.today())) }
        load()
    }

    fun chooseCustom(range: ReportRange) {
        if (ChronoUnit.DAYS.between(range.from, range.to) > ReportRange.MAX_DAYS) {
            _state.update { it.copy(errorMessage = "Choose a range of one year or less.") }
            return
        }
        _state.update { it.copy(preset = ReportPreset.CUSTOM, range = range) }
        load()
    }

    fun load() {
        val repo = repository ?: run {
            _state.update { it.copy(errorMessage = "Reports are not set up in this version of the app.") }
            return
        }
        val range = _state.value.range
        job?.cancel()
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        job = viewModelScope.launch {
            try {
                val report = repo.salesReport(range)
                _state.update { it.copy(isLoading = false, report = report) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = RemoteErrors.message(e)) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(errorMessage = null) }
}
