package blue.starry.onemorecoffee.core.social.di

import blue.starry.onemorecoffee.core.domain.repository.SocialRepository
import blue.starry.onemorecoffee.core.social.FirestoreSocialRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SocialModule {
    @Binds
    abstract fun bindSocialRepository(impl: FirestoreSocialRepository): SocialRepository
}
