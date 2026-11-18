package com.galaxycrew.crewcam

import org.json.JSONObject
import org.json.JSONTokener

internal class LocalOutputFailure(val kind: String, message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

internal object LocalResponseJson {
    /** Schema failures are reported before an observation can reach the crew. */
    fun <T> contract(block: () -> T): T = try { block() } catch (error: Exception) {
        throw when (error) {
            is LocalOutputFailure -> error
            is IllegalArgumentException, is org.json.JSONException -> LocalOutputFailure("INVALID_CONTRACT",
                "모델 응답의 필드·값을 확인하지 못해 배포하지 않았습니다. 같은 장면으로 다시 분석해주세요.", error)
            else -> error
        }
    }
    fun parse(text: String): JSONObject {
        try {
            require(text.isNotBlank() && text.length <= 16_000)
            val reader = JSONTokener(text)
            require(reader.nextClean() == '{')
            val value = JSONObject()
            var next = reader.nextClean()
            while (next != '}') {
                require(next == '"')
                val key = reader.nextString(next)
                require(!value.has(key) && reader.nextClean() == ':') { "Repeated or malformed field" }
                value.put(key, reader.nextValue())
                next = reader.nextClean()
                if (next != '}') {
                    require(next == ',')
                    next = reader.nextClean()
                    require(next == '"')
                }
            }
            require(reader.nextClean() == '\u0000')
            return value
        } catch (error: Exception) {
            throw LocalOutputFailure("MALFORMED_JSON",
                "모델 응답을 완성하지 못해 배포하지 않았습니다. 같은 장면을 유지하고 다시 분석해주세요.", error)
        }
    }
}
