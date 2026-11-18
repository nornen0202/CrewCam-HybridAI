package com.galaxycrew.crewcam

import android.graphics.Bitmap
import android.os.SystemClock
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.InferenceSource
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.OnDeviceModelOption
import com.google.firebase.ai.OnDeviceModelStatus
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

internal enum class Route(val title: String) {
    PRIVATE("On this phone"), HYBRID("Prefer this phone"), CLOUD("Cloud test")
}

internal data class Frame(val bitmap: Bitmap, val timestamp: Long, val origin: String)

internal data class Reading(val grounding: GroundedFraming, val receipt: JSONObject)
internal data class LocatedReference(val reference: AppearanceReference, val match: MatchPoint)

@OptIn(PublicPreviewAPI::class)
internal class HybridInference(private val log: (JSONObject) -> Unit) {
    private val options = listOf(OnDeviceModelOption.STABLE, OnDeviceModelOption.PREVIEW_FAST, OnDeviceModelOption.PREVIEW)
    private data class LocalModel(val option: OnDeviceModelOption, val status: OnDeviceModelStatus, val name: String?)

    private fun model(route: Route, option: OnDeviceModelOption = OnDeviceModelOption.STABLE) = FirebaseAI.getInstance(
        FirebaseApp.getInstance(), GenerativeBackend.googleAI()
    ).generativeModel(
        modelName = CLOUD_MODEL,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
            responseSchema = SubjectExtent.cloudSchema
            temperature = 0f
        },
        onDeviceConfig = OnDeviceConfig(mode = when (route) {
            Route.PRIVATE -> InferenceMode.ONLY_ON_DEVICE
            Route.HYBRID -> InferenceMode.PREFER_ON_DEVICE
            Route.CLOUD -> InferenceMode.ONLY_IN_CLOUD
        }, maxOutputTokens = 320, temperature = 0f, modelOption = option)
    )

    private suspend fun inspect(option: OnDeviceModelOption): LocalModel {
        try {
            val extension = model(Route.PRIVATE, option).onDeviceExtension
            val status = withTimeoutOrNull(2500) { extension?.checkStatus() } ?: OnDeviceModelStatus.UNAVAILABLE
            val name = if (status == OnDeviceModelStatus.AVAILABLE) withTimeoutOrNull(2500) {
                try { extension?.getModelName() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
            } else null
            return LocalModel(option, status, name)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { return LocalModel(option, OnDeviceModelStatus.UNAVAILABLE, null) }
    }

    private suspend fun preferredModel(): LocalModel {
        val states = mutableListOf<LocalModel>()
        for (option in options) {
            val local = inspect(option)
            if (local.status == OnDeviceModelStatus.AVAILABLE && local.name?.contains("nano-v4", ignoreCase = true) == true) return local
            states += local
        }
        return states.firstOrNull { it.status == OnDeviceModelStatus.AVAILABLE }
            ?: states.firstOrNull { it.status == OnDeviceModelStatus.DOWNLOADING || it.status == OnDeviceModelStatus.DOWNLOADABLE }
            ?: states.first()
    }
    suspend fun readiness(): String = preferredModel().let {
        listOfNotNull(it.option.toString(), it.status.toString(), it.name).joinToString(" · ")
    }

    suspend fun download(update: (String) -> Unit) {
        val local = preferredModel()
        val extension = requireNotNull(model(Route.PRIVATE, local.option).onDeviceExtension)
        if (extension.checkStatus() == OnDeviceModelStatus.DOWNLOADABLE) {
            extension.download().collect { event -> update(event.javaClass.simpleName) }
        }
    }

    suspend fun otherCategories(): String {
        val states = mutableListOf<String>()
        for (option in options) {
            val local = inspect(option)
            states += listOfNotNull(option.toString(), local.status.toString(), local.name).joinToString(" · ")
        }
        return states.joinToString("\n")
    }

    private class InvalidLocalReply(val request: String, cause: LocalOutputFailure) : Exception(cause)

    suspend fun observe(run: String, round: String, camera: String, subject: String, frame: Frame,
                        route: Route, stableProbe: Boolean = false, reference: AppearanceReference? = null,
                        projectedReference: LocatedReference? = null): Reading {
        require(subject.isNotBlank() && subject.length <= 80)
        require(!stableProbe || (route == Route.HYBRID && frame.origin == "generated sample"))
        require(projectedReference==null || reference==null && frame.origin=="video rehearsal" &&
            projectedReference.reference.subject==subject && frame.timestamp>projectedReference.reference.sensor)
        val located = projectedReference ?: reference?.let {
            require(it.subject == subject && frame.timestamp > it.sensor)
            val match = withContext(Dispatchers.Default) { it.locate(referencePixels(frame.bitmap)) }
            if (match == null) {
                log(JSONObject().put("event","reference_lost").put("run",run).put("round",round)
                    .put("camera",camera).put("selection_frame",it.sensor).put("frame",frame.timestamp))
                throw ReferenceLost()
            }
            LocatedReference(it,match)
        }
        try { return request(run, round, camera, subject, frame, route, stableProbe, null, located) }
        catch (invalid: InvalidLocalReply) {
            if (stableProbe) throw requireNotNull(invalid.cause)
            val retryRoute = if (route == Route.HYBRID) Route.CLOUD else Route.PRIVATE
            try { return request(run, round, camera, subject, frame, retryRoute, false, invalid.request, located) }
            catch (again: InvalidLocalReply) { throw requireNotNull(again.cause) }
        }
    }

    private suspend fun request(run: String, round: String, camera: String, subject: String, frame: Frame,
                                route: Route, stableProbe: Boolean, retryOf: String?, located: LocatedReference?): Reading {
        val started = SystemClock.elapsedRealtime()
        val receipt = JSONObject().put("event", "inference").put("run", run).put("round", round)
            .put("request", java.util.UUID.randomUUID().toString()).put("camera", camera)
            .put("frame", frame.timestamp).put("origin", frame.origin).put("images", 1)
            .put("mode", when (route) { Route.PRIVATE -> "ONLY_ON_DEVICE"; Route.HYBRID -> "PREFER_ON_DEVICE"; Route.CLOUD -> "ONLY_IN_CLOUD" })
            .put("source", JSONObject.NULL).put("cloud_model_config", CLOUD_MODEL)
        try {
            located?.let {
                receipt.put("selection_frame",it.reference.sensor).put("selection_method",it.reference.method)
                    .put("reference_match_error",it.match.difference).put("image_width",frame.bitmap.width)
                    .put("image_height",frame.bitmap.height).put("reference_x",(it.match.region.x*1000).toInt())
                    .put("reference_y",(it.match.region.y*1000).toInt())
            }
            if (retryOf != null) receipt.put("retry_of", retryOf).put("retry_reason", "invalid_local_output")
            val local = when { stableProbe -> inspect(OnDeviceModelOption.STABLE); route != Route.CLOUD -> preferredModel(); else -> null }
            receipt.put("local_category", local?.option?.toString() ?: JSONObject.NULL)
                .put("local_status", local?.status?.toString() ?: JSONObject.NULL)
            val ai = model(route, local?.option ?: OnDeviceModelOption.STABLE)
            val prompt = content {
                image(frame.bitmap)
                text(FrameGroundingContract.prompt(subject, retryOf != null, located?.match?.region))
            }
            val sdkStart = SystemClock.elapsedRealtime()
            val response = ai.generateContent(prompt)
            val source = when (response.inferenceSource) {
                InferenceSource.ON_DEVICE -> "ON_DEVICE"
                InferenceSource.IN_CLOUD -> "IN_CLOUD"
                else -> error("Unknown inference source")
            }
            receipt.put("source", source).put("sdk_ms", SystemClock.elapsedRealtime() - sdkStart)
                .put("response_model", response.modelVersion ?: JSONObject.NULL)
            check(route != Route.PRIVATE || source == "ON_DEVICE")
            check(route != Route.CLOUD || source == "IN_CLOUD")
            if (source == "ON_DEVICE") {
                val localName = withTimeoutOrNull(3000) {
                    try { ai.onDeviceExtension?.getModelName() }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                }
                receipt.put("local_model", localName ?: JSONObject.NULL)
            }
            val raw = response.text.orEmpty().trim()
            if (frame.origin == "generated sample") receipt.put("sample_response", raw.take(1600))
            val text = fencedJson.matchEntire(raw)?.groupValues?.get(1) ?: raw
            val grounding = try { FrameGroundingContract.parse(text, located?.match?.region) }
                catch (e: LocalOutputFailure) {
                    if (source == "ON_DEVICE") throw InvalidLocalReply(receipt.getString("request"), e)
                    throw e
                }
            receipt.put("result", "valid").put("framing", grounding.framing).put("grounding", grounding.observation)
            return Reading(grounding, receipt)
        } catch (e: Exception) {
            receipt.put("result", if (e is CancellationException) "cancelled" else "failed")
                .put("error_type", e.javaClass.simpleName)
            throw e
        } finally {
            receipt.put("elapsed_ms", SystemClock.elapsedRealtime() - started)
            log(receipt)
        }
    }

    private companion object {
        const val CLOUD_MODEL = "gemini-3.5-flash-lite"
        val fencedJson = Regex("""^`{3}(?:json)?\s*(\{[\s\S]*\})\s*`{3}$""", RegexOption.IGNORE_CASE)
    }
}
