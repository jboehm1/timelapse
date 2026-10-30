package eu.example.constructionlapse

/** Process-wide UI job ownership, retained through Activity recreation (not process death). */
object ProjectFileJobs {
    @Volatile var busy = false
    @Volatile var cancelVideo = false
    @Volatile var videoStatus: String? = null
}
