package blue.starry.onemorecoffee.core.social

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.socialDataStore by preferencesDataStore(name = "social_session")

// 参加中リーグ ID の永続化。uid は Firebase Auth が保持するためここでは持たない。
@Singleton
class SocialSessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val leagueIdKey = stringPreferencesKey("league_id")

    val leagueId: Flow<String?> = context.socialDataStore.data.map { preferences ->
        preferences[leagueIdKey]
    }

    suspend fun save(leagueId: String) {
        context.socialDataStore.edit { preferences ->
            preferences[leagueIdKey] = leagueId
        }
    }

    suspend fun clear() {
        context.socialDataStore.edit { preferences ->
            preferences.remove(leagueIdKey)
        }
    }
}
