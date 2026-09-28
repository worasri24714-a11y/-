package app.copyruler

import android.content.Context
import app.copyruler.data.AppDatabase
import app.copyruler.data.DocumentRepository
import app.copyruler.data.ImageStore
import app.copyruler.data.SettingsRepository
import app.copyruler.data.settingsDataStore
import app.copyruler.ocr.TextRecognizerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-rolled dependency container; one instance lives in [CopyRulerApp]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Outlives screens, so saves started just before leaving a screen still complete. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val imageStore = ImageStore(appContext)

    val documentRepository: DocumentRepository by lazy {
        DocumentRepository(AppDatabase.build(appContext).documentDao(), imageStore, applicationScope)
    }

    val settingsRepository = SettingsRepository(appContext.settingsDataStore)

    val textRecognizer = TextRecognizerService(appContext)
}
