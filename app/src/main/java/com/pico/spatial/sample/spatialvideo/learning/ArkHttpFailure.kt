package com.pico.spatial.sample.spatialvideo.learning

import java.io.IOException
import okhttp3.Response
import org.json.JSONObject

internal class ArkHttpFailure
private constructor(
    val status: Int,
    val code: String?,
    val type: String?,
    val parameter: String?,
    val fastUnavailable: Boolean,
) : IOException(listOfNotNull("HTTP $status", code, type, parameter).joinToString(" ")) {
    companion object {
        fun from(response: Response): ArkHttpFailure {
            val error =
                runCatching { JSONObject(response.peekBody(8_192).string()).optJSONObject("error") }
                    .getOrNull()
            val rawCode = error?.optString("code").orEmpty()
            val message = error?.optString("message").orEmpty().lowercase()
            val fastUnavailable =
                rawCode == "ModelNotOpen" &&
                    (FAST_MODE_UNAVAILABLE.containsMatchIn(message) ||
                        ((message.contains("低延迟") || message.contains("低时延")) &&
                            (message.contains("未开通") || message.contains("未激活"))))
            return ArkHttpFailure(
                status = response.code,
                code = rawCode.takeIf { it in SAFE_CODES },
                type = error?.optString("type")?.takeIf { it in SAFE_TYPES },
                parameter = error?.optString("param")?.takeIf { it in SAFE_PARAMETERS },
                fastUnavailable = fastUnavailable,
            )
        }

        private const val FAST_MODE = "\\bfast[- ]+(?:inference[- ]+)?mode\\b"
        private const val NOT_ACTIVATED =
            "\\bnot\\s+(?:(?:yet|been)\\s+){0,2}(?:activated|enabled|opened|available)\\b"
        private val FAST_MODE_UNAVAILABLE =
            Regex(
                "$FAST_MODE[^.!?;\\n]{0,120}$NOT_ACTIVATED|$NOT_ACTIVATED[^.!?;\\n]{0,48}$FAST_MODE"
            )

        // Never copy arbitrary provider strings into diagnostics, including unknown code fields.
        private val SAFE_CODES =
            setOf(
                "ModelNotOpen",
                "AuthenticationError",
                "MissingParameter",
                "InvalidParameter",
                "InvalidParameter.UnsupportedParameter",
                "InvalidArgumentError",
                "InvalidImageURL.EmptyURL",
                "InvalidImageURL.InvalidFormat",
                "OutofContextError",
                "AccessDenied",
                "OperationDenied",
                "OperationDenied.ServiceNotOpen",
                "OperationDenied.ServiceOverdue",
                "QuotaExceeded",
                "RateLimitExceeded",
                "InternalServiceError",
                "InternalError",
                "ServiceUnavailable",
                "ServerOverloaded",
            )
        private val SAFE_TYPES =
            setOf(
                "BadRequest",
                "Unauthorized",
                "Forbidden",
                "NotFound",
                "TooManyRequests",
                "InternalServerError",
                "ServiceUnavailable",
            )
        private val SAFE_PARAMETERS =
            setOf("service_tier", "model", "thinking", "max_tokens", "messages", "image_url")
    }
}
