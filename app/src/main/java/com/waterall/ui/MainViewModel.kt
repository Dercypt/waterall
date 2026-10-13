package com.waterall.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.waterall.domain.repository.InMemoryReportRepository
import com.waterall.domain.repository.ReportRepository

/**
 * ViewModel managing the crowdsourced report repository across Activity configuration changes (e.g. screen rotation).
 */
class MainViewModel(
    val reportRepository: ReportRepository = InMemoryReportRepository.createDefault()
) : ViewModel() {

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                    return MainViewModel() as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}
