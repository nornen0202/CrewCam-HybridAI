package com.galaxycrew.crewcam

import org.json.JSONObject
import org.json.JSONTokener

internal class LocalOutputFailure(val kind: String, message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

internal object LocalResponseJson {
    /** Only field/schema decoding opts into repair; downstream semantic/safety failures never do. */
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
            val value = reader.nextValue()
            require(value is JSONObject && reader.nextClean() == '\u0000')
            return value
        } catch (error: Exception) {
            throw LocalOutputFailure("MALFORMED_JSON",
                "모델 응답을 완성하지 못해 배포하지 않았습니다. 같은 장면을 유지하고 다시 분석해주세요.", error)
        }
    }
}
