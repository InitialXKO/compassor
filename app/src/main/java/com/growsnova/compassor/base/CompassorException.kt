package com.growsnova.compassor.base

sealed class CompassorException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class DatabaseException(message: String, cause: Throwable? = null) : CompassorException(message, cause)
    class NetworkException(message: String, cause: Throwable? = null) : CompassorException(message, cause)
    class LocationException(message: String) : CompassorException(message)
    class NavigationException(message: String) : CompassorException(message)
    class UnknownException(message: String, cause: Throwable? = null) : CompassorException(message, cause)
}
