package com.growsnova.compassor

sealed class CompassorException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class DatabaseException(message: String, cause: Throwable? = null) : CompassorException(message, cause)
    class NetworkException(message: String, cause: Throwable? = null) : CompassorException(message, cause)
    class LocationException(message: String) : CompassorException(message)
    class PermissionDeniedException(message: String) : CompassorException(message)
    class UnknownException(message: String, cause: Throwable? = null) : CompassorException(message, cause)
}

sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val exception: CompassorException) : Result<Nothing>()
    object Loading : Result<Nothing>()
}
