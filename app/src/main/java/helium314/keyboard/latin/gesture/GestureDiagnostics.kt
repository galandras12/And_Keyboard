// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.gesture

/** What happened with glide typing since the app started; shown in the gesture settings, so it is clear why gestures do not work. */
object GestureDiagnostics {
    @Volatile var gesturesStarted = 0
        private set
    @Volatile var gesturesDecoded = 0
        private set
    @Volatile var lastCandidates = -1
        private set
    @Volatile var lastMillis = -1L
        private set
    /** number of words the decoder knows; -1 until the word list was read */
    @Volatile var lexiconWords = -1
        private set
    @Volatile var lastError: String? = null
        private set

    fun onGestureStarted() { gesturesStarted++ }

    fun onLexicon(words: Int) { lexiconWords = words }

    fun onDecoded(candidates: Int, millis: Long) {
        gesturesDecoded++
        lastCandidates = candidates
        lastMillis = millis
    }

    fun onError(error: Throwable) { lastError = error.javaClass.simpleName + ": " + error.message }
    fun onProblem(problem: String) { lastError = problem }
}
