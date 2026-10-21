package com.off.audioconverter.domain

enum class ConversionStatus {
    IDLE, PREPARING, CONVERTING, SAVING, COMPLETED, CANCELLED, FAILED
}

data class ConversionUiState(
    val inputName: String? = null,
    val inputUri: String? = null,
    val bitrateKbps: Int = 192,
    val status: ConversionStatus = ConversionStatus.IDLE,
    val message: String? = null,
    val outputName: String? = null,
    val isBusy: Boolean = false
)
