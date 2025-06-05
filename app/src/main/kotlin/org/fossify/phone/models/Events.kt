package org.fossify.phone.models

sealed class Events {
    data object RefreshCallLog : Events()
}
