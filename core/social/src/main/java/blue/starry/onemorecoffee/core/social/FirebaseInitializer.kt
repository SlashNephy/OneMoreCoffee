package blue.starry.onemorecoffee.core.social

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

// google-services Gradle プラグインを使わず、secrets.properties 由来の値で手動初期化する。
// 値が既定値のままなら初期化せず、ソーシャル機能は「未構成」として振る舞う（アプリ本体は動く）。
object FirebaseInitializer {
    fun initialize(context: Context, projectId: String, applicationId: String, apiKey: String) {
        if (projectId.startsWith("DEFAULT_") || applicationId.startsWith("DEFAULT_") || apiKey.startsWith("DEFAULT_")) {
            return
        }
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            return
        }

        val options = FirebaseOptions.Builder()
            .setProjectId(projectId)
            .setApplicationId(applicationId)
            .setApiKey(apiKey)
            .build()
        FirebaseApp.initializeApp(context, options)
    }

    fun isAvailable(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()
}
