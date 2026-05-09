package blue.starry.onemorecoffee.core.data.di

import android.content.Context
import androidx.room.Room
import blue.starry.onemorecoffee.core.data.database.OneMoreCoffeeDatabase
import blue.starry.onemorecoffee.core.data.database.dao.StoreDao
import blue.starry.onemorecoffee.core.data.database.dao.VisitDao
import blue.starry.onemorecoffee.core.data.repository.StoreRepositoryImpl
import blue.starry.onemorecoffee.core.data.repository.VisitRepositoryImpl
import blue.starry.onemorecoffee.core.data.starbucks.StarbucksStoreClient
import blue.starry.onemorecoffee.core.data.starbucks.StarbucksStoreDataSource
import blue.starry.onemorecoffee.core.domain.repository.StoreRepository
import blue.starry.onemorecoffee.core.domain.repository.VisitRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindStoreRepository(impl: StoreRepositoryImpl): StoreRepository

    @Binds
    abstract fun bindVisitRepository(impl: VisitRepositoryImpl): VisitRepository

    @Binds
    abstract fun bindStarbucksStoreDataSource(impl: StarbucksStoreClient): StarbucksStoreDataSource

    companion object {
        @Provides
        @Singleton
        fun provideOneMoreCoffeeDatabase(
            @ApplicationContext context: Context,
        ): OneMoreCoffeeDatabase {
            return Room.databaseBuilder(
                context,
                OneMoreCoffeeDatabase::class.java,
                "one_more_coffee.db",
            ).build()
        }

        @Provides
        fun provideStoreDao(database: OneMoreCoffeeDatabase): StoreDao {
            return database.storeDao()
        }

        @Provides
        fun provideVisitDao(database: OneMoreCoffeeDatabase): VisitDao {
            return database.visitDao()
        }

        @Provides
        @Singleton
        fun provideHttpClient(): HttpClient {
            return HttpClient(OkHttp)
        }
    }
}
