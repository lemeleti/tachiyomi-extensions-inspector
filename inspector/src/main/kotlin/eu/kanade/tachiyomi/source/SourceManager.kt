package eu.kanade.tachiyomi.source

import android.content.Context
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import rx.Observable

open class SourceManager(
    private val context: Context,
) {
    private val sourcesMap = mutableMapOf<Long, Source>()

    private val stubSourcesMap = mutableMapOf<Long, StubSource>()

    init {
        createInternalSources().forEach { registerSource(it) }
    }

    open fun get(sourceKey: Long): Source? = sourcesMap[sourceKey]

    fun getOrStub(sourceKey: Long): Source =
        sourcesMap[sourceKey] ?: stubSourcesMap.getOrPut(sourceKey) {
            StubSource(sourceKey)
        }

    fun getOnlineSources() = sourcesMap.values.filterIsInstance<HttpSource>()

    fun getCatalogueSources() = sourcesMap.values.filterIsInstance<CatalogueSource>()

    internal fun registerSource(
        source: Source,
        overwrite: Boolean = false,
    ) {
        if (overwrite || !sourcesMap.containsKey(source.id)) {
            sourcesMap[source.id] = source
        }
        if (overwrite || !stubSourcesMap.containsKey(source.id)) {
            stubSourcesMap[source.id] = StubSource(source.id)
        }
    }

    internal fun unregisterSource(source: Source) {
        sourcesMap.remove(source.id)
    }

    private fun createInternalSources(): List<Source> =
        listOf(
//        LocalSource(context)
        )

    private inner class StubSource(
        override val id: Long,
    ) : Source {
        override val name: String
            get() = id.toString()

        override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.error(getSourceNotInstalledException())

        override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.error(getSourceNotInstalledException())

        override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.error(getSourceNotInstalledException())

        override fun toString(): String = name

        private fun getSourceNotInstalledException(): Exception = Exception("source not found")
    }
}
