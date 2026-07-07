package blue.starry.onemorecoffee

import android.app.Application
import blue.starry.onemorecoffee.core.social.FirebaseInitializer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OneMoreCoffeeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseInitializer.initialize(
            context = this,
            projectId = BuildConfig.FIREBASE_PROJECT_ID,
            applicationId = BuildConfig.FIREBASE_APPLICATION_ID,
            apiKey = BuildConfig.FIREBASE_API_KEY,
        )
    }
}
