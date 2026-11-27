package com.hhh.phoneagent.core.di

import com.hhh.phoneagent.core.common.Clock
import com.hhh.phoneagent.core.common.SystemClock
import com.hhh.phoneagent.data.approval.ApprovalQueue
import com.hhh.phoneagent.data.local.prefs.EncryptedGeminiKeyStore
import com.hhh.phoneagent.data.remote.gemini.GeminiLanguageModel
import com.hhh.phoneagent.domain.repository.GeminiKeyStore
import com.hhh.phoneagent.domain.repository.LanguageModel
import com.hhh.phoneagent.domain.security.ApprovalGate
import com.hhh.phoneagent.domain.tool.ToolRegistry
import com.hhh.phoneagent.tools.registry.DefaultToolRegistry
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindLanguageModel(impl: GeminiLanguageModel): LanguageModel

    @Binds
    abstract fun bindGeminiKeyStore(impl: EncryptedGeminiKeyStore): GeminiKeyStore

    @Binds
    abstract fun bindApprovalGate(impl: ApprovalQueue): ApprovalGate

    @Binds
    abstract fun bindToolRegistry(impl: DefaultToolRegistry): ToolRegistry

    @Binds
    abstract fun bindClock(impl: SystemClock): Clock
}
