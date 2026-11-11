package app.supersax.audio

/** Microphone state, shown to the user (errors are visible and recoverable). */
sealed interface MicState {
    data object Idle : MicState
    data object Listening : MicState
    /** The recorder could not start (mic busy, no permission...). */
    data class Failed(val reason: Reason) : MicState
    enum class Reason { NO_PERMISSION, BUSY, ERROR }
}
