package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception

import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service.RenderRequest
import java.util.UUID

/**
 * Top level subject access request exception type
 */
open class SubjectAccessRequestException(
  message: String,
  cause: Throwable? = null,
  val subjectAccessRequestId: UUID? = null,
  val params: Map<String, *>? = null,
) : RuntimeException(message, cause) {

  /**
   * Return the exception message with additional details.
   */
  override val message: String?
    get() = buildString {
      append(super.message)
      cause?.message?.let { append(", cause=$it") }
      append(", id=$subjectAccessRequestId")
      params?.toFormattedString()?.let { append(", $it") }
    }

  fun messageOrDefault(): String = message ?: this::class.java.simpleName

  fun paramsToPairsArray(): Array<Pair<String, String>> = this.params
    ?.map { Pair(it.key, it.value.toString()) }
    ?.toTypedArray()
    ?: emptyArray()

  private fun Map<String, *>.toFormattedString() = this.entries.joinToString(", ") { entry ->
    "${entry.key}=${entry.value}"
  }
}

/**
 * Exception type for fatal/non retryable errors.
 */
class FatalSubjectAccessRequestException(
  message: String,
  cause: Throwable?,
  subjectAccessRequestId: UUID? = null,
  params: Map<String, *>? = null,
) : SubjectAccessRequestException(
  FATAL_ERROR_MESSAGE_PREFIX.format(message),
  cause,
  subjectAccessRequestId,
  params,
) {

  constructor(
    message: String,
    subjectAccessRequestId: UUID? = null,
    params: Map<String, *>? = null,
  ) : this(message, null, subjectAccessRequestId, params)

  companion object {
    private const val FATAL_ERROR_MESSAGE_PREFIX = "subjectAccessRequest failed with non-retryable error: %s"
  }
}

/**
 * Exception type for errors document storage errors
 */
class SubjectAccessRequestDocumentStorageException(
  subjectAccessRequestId: UUID? = null,
  message: String,
  params: Map<String, *>? = null,
  cause: Throwable? = null,
) : SubjectAccessRequestException(
  subjectAccessRequestId = subjectAccessRequestId,
  message = message,
  params = params,
  cause = cause,
) {
  constructor(
    subjectAccessRequestId: UUID? = null,
    message: String,
    params: Map<String, *>? = null,
  ) : this(subjectAccessRequestId, message, params, null)
}

/**
 * Exception type for templating errors.
 */
class SubjectAccessRequestTemplatingException(
  subjectAccessRequestId: UUID? = null,
  message: String,
  params: Map<String, *>? = null,
) : SubjectAccessRequestException(
  subjectAccessRequestId = subjectAccessRequestId,
  message = message,
  params = params,
) {
  constructor(message: String, params: Map<String, *>? = null) : this(
    subjectAccessRequestId = null,
    message = message,
    params = params,
  )
}

/**
 * Exception type for retry exhausted errors
 */
class SubjectAccessRequestRetryExhaustedException(
  retryAttempts: Long,
  cause: Throwable?,
  subjectAccessRequestId: UUID? = null,
  params: Map<String, *>? = null,
) : SubjectAccessRequestException(
  RETRY_EXHAUSTED_ERROR_MESSAGE_PREFIX.format(retryAttempts),
  cause,
  subjectAccessRequestId,
  params,
) {

  companion object {
    private const val RETRY_EXHAUSTED_ERROR_MESSAGE_PREFIX = "request failed and max retry attempts (%s) exhausted"
  }
}

class SubjectAccessRequestServiceConfigurationNotFoundException(
  val serviceConfigurationId: UUID,
  subjectAccessRequestId: UUID,
) : SubjectAccessRequestException(
  message = "Subject access request service configuration Id: $serviceConfigurationId not found",
  subjectAccessRequestId = subjectAccessRequestId,
)

class SubjectAccessRequestResourceNotFoundException(resource: String) :
  RuntimeException(
    "Subject access request resource $resource not found",
  )

class SubjectAccessRequestBadRequestException(
  message: String,
  subjectAccessRequestId: UUID? = null,
) : SubjectAccessRequestException(message = message, subjectAccessRequestId = subjectAccessRequestId)

class SubjectAccessRequestGetTemplateException(
  message: String = "GET service template request returned unexpected error",
  renderRequest: RenderRequest,
  val status: Int,
) : SubjectAccessRequestException(
  message = message,
  subjectAccessRequestId = renderRequest.id,
) {
  val serviceName: String = renderRequest.serviceConfiguration.serviceName
  val url: String = renderRequest.serviceConfiguration.url
}
