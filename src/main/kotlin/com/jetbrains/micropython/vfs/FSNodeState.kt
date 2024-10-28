package com.jetbrains.micropython.vfs

enum class FSNodeState {
    NON_CACHED,
    CACHED,
    DIRTY
}