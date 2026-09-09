package de.snowworks.ariana.core.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local provider metadata. Secrets are stored separately in ProviderSecretStore.
 * Saving a provider NEVER changes the NetworkGate allow-list.
 */
class ModelProviderStore(context: Context) {
    data class Provider(
        val id: String,
        val label: String,
        val endpoint: String,
        val model: String,
        val enabled: Boolean = false,
    )

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun list(): List<Provider> {
        val raw = prefs.getString(KEY_PROVIDERS, null) ?: return emptyList()
        return runCatching { decode(JSONArray(raw)) }.getOrDefault(emptyList())
    }

    @Synchronized
    fun get(id: String): Provider? = list().firstOrNull { it.id == id }

    @Synchronized
    fun active(): Provider? = list().firstOrNull { it.enabled }

    @Synchronized
    fun upsert(provider: Provider) {
        validate(provider)
        val providers = list().toMutableList()
        val normalized = if (provider.enabled) {
            providers.map { if (it.id == provider.id) it else it.copy(enabled = false) }.toMutableList()
        } else providers
        val index = normalized.indexOfFirst { it.id == provider.id }
        if (index >= 0) normalized[index] = provider else normalized += provider
        require(normalized.size <= MAX_PROVIDERS) { "Too many model providers." }
        save(normalized)
    }

    @Synchronized
    fun setActive(id: String?) {
        val providers = list().map { provider ->
            provider.copy(enabled = id != null && provider.id == id)
        }
        if (id != null) require(providers.any { it.id == id }) { "Provider not found." }
        save(providers)
    }

    @Synchronized
    fun remove(id: String): Boolean {
        val providers = list().toMutableList()
        val removed = providers.removeAll { it.id == id }
        if (removed) save(providers)
        return removed
    }

    private fun save(providers: List<Provider>) {
        val array = JSONArray(providers.map { provider ->
            JSONObject()
                .put("id", provider.id)
                .put("label", provider.label)
                .put("endpoint", provider.endpoint)
                .put("model", provider.model)
                .put("enabled", provider.enabled)
        })
        prefs.edit().putString(KEY_PROVIDERS, array.toString()).apply()
    }

    private fun decode(array: JSONArray): List<Provider> {
        val out = ArrayList<Provider>(array.length())
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val provider = Provider(
                id = item.getString("id"),
                label = item.getString("label"),
                endpoint = item.getString("endpoint"),
                model = item.getString("model"),
                enabled = item.optBoolean("enabled", false),
            )
            validate(provider)
            out += provider
        }
        return out
    }

    private fun validate(provider: Provider) {
        require(provider.id.matches(ID_REGEX)) { "Invalid provider id." }
        require(provider.label.length in 1..64) { "Invalid provider label." }
        require(provider.endpoint.length in 1..512) { "Invalid provider endpoint." }
        require(provider.model.length in 1..128) { "Invalid model name." }
    }

    companion object {
        private const val PREFS = "x_ariana_model_providers"
        private const val KEY_PROVIDERS = "providers_v1"
        private const val MAX_PROVIDERS = 12
        private val ID_REGEX = Regex("[A-Za-z0-9._:-]{1,64}")
    }
}
