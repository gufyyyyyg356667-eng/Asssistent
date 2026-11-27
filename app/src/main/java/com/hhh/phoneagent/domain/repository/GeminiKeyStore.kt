package com.hhh.phoneagent.domain.repository

interface GeminiKeyStore {
    fun keys(): List<String>
    fun saveKeys(keys: List<String>)
}
