package com.jetbrains.micropython.nova

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.Service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.net.URI
import java.net.URISyntaxException


@Service(Service.Level.PROJECT)
class ConnectCredentials {

    private fun createCredentialAttributes(key: String): CredentialAttributes {
        return CredentialAttributes(
            generateServiceName("MySystem", key)
        )
    }

    private fun key(url: String) = "${this::class.java.name}/$url"

    suspend fun retrievePassword(url: String): String {
        return withContext(Dispatchers.IO) {
            val attributes = createCredentialAttributes(key(url))
            val passwordSafe = PasswordSafe.instance
            passwordSafe.getPassword(attributes) ?: ""
        }
    }

    suspend fun savePassword(url: String, password: String) {
        val attributes = createCredentialAttributes(key(url))
        val credentials = Credentials(null, password)
        withContext(Dispatchers.IO) {
            PasswordSafe.instance.set(attributes, credentials)
        }
    }
}

fun messageForBrokenUrl(url: String): @Nls String? {
    try {
        val uri = URI(url)
        if (uri.scheme !in arrayOf("ws", "wss")) {
            return "URL format has to be ws://host:port or wss://host:port\n but you have entered: $url"
        }
        return null
    } catch (_: URISyntaxException) {
        return "Malformed URL $url"
    }
}