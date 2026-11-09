package com.github.devvikassoni.leaklens.ai

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * Stores the AI provider API key in the IDE's secure credential store (OS keychain / KeePass)
 * instead of project XML, which is frequently committed to VCS.
 */
object AiCredentials {

    private val attributes = CredentialAttributes(generateServiceName("LeakLens", "AI API Key"))

    fun getApiKey(): String = PasswordSafe.instance.getPassword(attributes).orEmpty()

    fun setApiKey(apiKey: String) {
        PasswordSafe.instance.setPassword(attributes, apiKey.trim().ifBlank { null })
    }
}
