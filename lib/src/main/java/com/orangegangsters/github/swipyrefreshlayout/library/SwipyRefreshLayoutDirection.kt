package com.orangegangsters.github.swipyrefreshlayout.library

enum class SwipyRefreshLayoutDirection(private val value: Int) {
    TOP(0),
    BOTTOM(1),
    BOTH(2);

    companion object {
        fun getFromInt(value: Int): SwipyRefreshLayoutDirection = values()
                .find { it.value == value }
                ?: BOTH
    }
}