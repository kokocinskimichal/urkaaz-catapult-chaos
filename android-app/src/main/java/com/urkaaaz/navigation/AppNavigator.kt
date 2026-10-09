package com.urkaaaz.navigation

class AppNavigator(
    private val onScreenChanged: (AppScreen) -> Unit,
) {
    private val backStack = ArrayDeque<AppScreen>()
    var currentScreen: AppScreen? = null
        private set

    fun start(screen: AppScreen) {
        if (currentScreen != null) return
        currentScreen = screen
        onScreenChanged(screen)
    }

    fun navigate(screen: AppScreen) {
        currentScreen?.let(backStack::addLast)
        currentScreen = screen
        onScreenChanged(screen)
    }

    fun replace(screen: AppScreen) {
        currentScreen = screen
        onScreenChanged(screen)
    }

    fun back(): Boolean {
        val previous = backStack.removeLastOrNull() ?: return false
        currentScreen = previous
        onScreenChanged(previous)
        return true
    }
}
