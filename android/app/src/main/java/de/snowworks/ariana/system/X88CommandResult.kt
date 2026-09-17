package de.snowworks.ariana.system

data class X88CommandResult(
    val commandId: String,
    val accepted: Boolean,
    val code: String,
    val message: String? = null,
) {
    companion object {
        fun accepted(commandId: String, code: String = "X88_ACCEPTED") =
            X88CommandResult(commandId = commandId, accepted = true, code = code)

        fun rejected(commandId: String, code: String, message: String? = null) =
            X88CommandResult(commandId = commandId, accepted = false, code = code, message = message)
    }
}
