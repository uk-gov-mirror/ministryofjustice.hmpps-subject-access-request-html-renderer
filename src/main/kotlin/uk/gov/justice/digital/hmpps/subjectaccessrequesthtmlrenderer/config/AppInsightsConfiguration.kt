package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service.RenderRequest
import java.util.UUID

@Configuration
class AppInsightsConfiguration {

  @Bean
  fun telemetryClient(): TelemetryClient = TelemetryClient()
}

fun TelemetryClient.renderEvent(
  event: RenderEvent,
  id: UUID? = null,
  vararg kvPairs: Pair<String, String> = emptyArray(),
) {
  this.trackEvent(
    event.name,
    mapOf(
      "id" to id.toString(),
      *kvPairs,
    ),
    null,
  )
}

fun TelemetryClient.renderEvent(
  event: RenderEvent,
  request: RenderRequest? = null,
  vararg kvPairs: Pair<String, String> = emptyArray(),
) {
  this.trackEvent(
    event.name,
    mapOf(
      "id" to request?.id.toString(),
      "serviceName" to request?.serviceConfiguration?.serviceName,
      *kvPairs,
    ),
    null,
  )
}

enum class RenderEvent {
  REQUEST_RECEIVED,
  REQUEST_COMPLETE,
  REQUEST_ERRORED,
  REQUEST_COMPLETE_HTML_CACHED,
  GET_SERVICE_DATA,
  SERVICE_DATA_RETURNED,
  SERVICE_DATA_NO_CONTENT,
  SERVICE_DATA_NO_CONTENT_ID_NOT_SUPPORTED,
  SERVICE_DATA_EXISTS,
  GET_SERVICE_DATA_RETRY,
  STORE_SERVICE_DATA_STARTED,
  STORE_SERVICE_DATA_COMPLETED,
  RENDER_TEMPLATE_STARTED,
  RENDER_TEMPLATE_COMPLETED,
  RENDERED_HTML_EXISTS,
  STORE_RENDERED_HTML_STARTED,
  STORE_RENDERED_HTML_COMPLETED,
  GET_ATTACHMENT_STARTED,
  GET_ATTACHMENT_COMPLETE,
  GET_ATTACHMENT_ERRORED,
  GET_ATTACHMENT_RETRY,
  STORE_ATTACHMENT_STARTED,
  STORE_ATTACHMENT_COMPLETED,
  ATTACHMENT_EXISTS,
  GET_LOCATION_RETRY,
  GET_NOMIS_MAPPING_RETRY,
  GET_SERVICE_TEMPLATE_RETRY
}
