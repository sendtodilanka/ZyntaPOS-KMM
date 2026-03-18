package com.zyntasolutions.zyntapos.data.local.mapper

import com.zyntasolutions.zyntapos.db.Product_variants
import com.zyntasolutions.zyntapos.domain.model.ProductVariant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Maps between the SQLDelight-generated [Product_variants] entity and
 * the domain [ProductVariant] model.
 *
 * The `attributes` column stores a JSON object string
 * (e.g. `{"Color":"Blue","Size":"XL"}`).
 */
object ProductVariantMapper {

    private val json = Json { ignoreUnknownKeys = true }

    fun toDomain(row: Product_variants): ProductVariant = ProductVariant(
        id        = row.id,
        productId = row.product_id,
        name      = row.name,
        attributes = parseAttributes(row.attributes),
        price     = row.price,
        stock     = row.stock,
        barcode   = row.barcode,
    )

    fun attributesToJson(attrs: Map<String, String>): String {
        val obj = JsonObject(attrs.mapValues { JsonPrimitive(it.value) })
        return obj.toString()
    }

    private fun parseAttributes(raw: String): Map<String, String> {
        if (raw.isBlank() || raw == "{}") return emptyMap()
        return try {
            json.parseToJsonElement(raw).jsonObject.mapValues { it.value.jsonPrimitive.content }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
