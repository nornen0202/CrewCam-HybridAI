package com.galaxycrew.crewcam

internal fun checkError(error: Exception): String = when (error.javaClass.simpleName) {
    "FirebaseAIOnDeviceNotAvailableException" ->
        "The local model is unavailable. Open Sample checks to prepare it, or allow cloud fallback for a staged subject."
    "FirebaseAIOnDeviceDownloadRequiredException" ->
        "The local model needs a download. In Sample checks, select Prepare local model."
    "FirebaseAIInvalidInputException", "LocalOutputFailure", "JSONException", "IllegalArgumentException" ->
        "The model could not return a usable observation. Keep the subject visible and try again."
    else -> "The check could not finish (" + error.javaClass.simpleName + "). Check the connection and try again."
}
