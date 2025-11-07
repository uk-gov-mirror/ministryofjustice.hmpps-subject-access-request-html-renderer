package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.Attachment
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.ServiceData
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.ATTACHMENT_EXISTS
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.GET_ATTACHMENT_COMPLETE
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.GET_ATTACHMENT_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.GET_SERVICE_DATA
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.RENDERED_HTML_EXISTS
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.SERVICE_DATA_EXISTS
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.SERVICE_DATA_NO_CONTENT
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.SERVICE_DATA_NO_CONTENT_ID_NOT_SUPPORTED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.SERVICE_DATA_RETURNED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.STORE_ATTACHMENT_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.STORE_ATTACHMENT_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.STORE_SERVICE_DATA_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.STORE_SERVICE_DATA_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.renderEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.templates.TemplateRenderingService
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID

@Service
class RenderService(
  private val dynamicServicesClient: DynamicServicesClient,
  private val documentStore: DocumentStore,
  private val templateRenderingService: TemplateRenderingService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val STATUS_IDENTIFIER_TYPE_NOT_SUPPORTED = 209
  }

  suspend fun renderServiceDataHtml(renderRequest: RenderRequest): RenderResult {
    log.info(
      "rendering html for request: {}, serviceName: {}",
      renderRequest.id,
      renderRequest.serviceConfiguration.serviceName,
    )
    val (content, attachments) = getContentAndAttachmentsFromJsonData(renderRequest)
    val result = renderAndStoreHtmlIfNotAlreadyPresent(renderRequest, content)
    attachments?.forEach { attachment -> getAndStoreAttachment(renderRequest, attachment) }
    return result
  }

  suspend fun getRenderedHtml(documentKey: String): ByteArray? = documentStore.getByDocumentKey(documentKey)

  suspend fun listCacheFilesWithPrefix(subjectAccessRequestId: UUID) = documentStore.list(subjectAccessRequestId)

  private suspend fun getContentAndAttachmentsFromJsonData(renderRequest: RenderRequest): ServiceData {
    val documentJsonKey = renderRequest.documentJsonKey()
    if (documentStore.contains(documentJsonKey)) {
      telemetryClient.renderEvent(SERVICE_DATA_EXISTS, renderRequest)
      log.info("json response for request: {} exists retrieving data", documentJsonKey)
      return objectMapper.readValue(documentStore.getByDocumentKey(documentJsonKey), ServiceData::class.java)
    } else {
      log.info(
        "json response for request: {} does not exist getting data from {}",
        documentJsonKey,
        renderRequest.serviceConfiguration.serviceName,
      )
      val serviceData = getDataForSubject(renderRequest)
      storeJson(renderRequest, serviceData)
      return serviceData
    }
  }

  private suspend fun renderAndStoreHtmlIfNotAlreadyPresent(
    renderRequest: RenderRequest,
    content: Any?,
  ): RenderResult {
    val documentHtmlKey = renderRequest.documentHtmlKey()
    if (documentStore.contains(documentKey = documentHtmlKey)) {
      telemetryClient.renderEvent(RENDERED_HTML_EXISTS, renderRequest)
      log.info("document html for request: {} exists no action required", documentHtmlKey)
      return RenderResult.DATA_ALREADY_EXISTS
    } else {
      log.info(
        "document html for request: {} does not exist rendering for {}",
        documentHtmlKey,
        renderRequest.serviceConfiguration.serviceName,
      )
      val renderedData = templateRenderingService.renderServiceDataHtml(renderRequest, content)
      storeRenderedHtml(renderRequest, renderedData)
      log.info("document {} created and added to document store", documentHtmlKey)
      return RenderResult.CREATED
    }
  }

  private suspend fun getAndStoreAttachment(renderRequest: RenderRequest, attachment: Attachment) {
    val documentAttachmentKey = renderRequest.documentAttachmentKey(attachment.attachmentNumber, attachment.filename)
    if (documentStore.contains(documentKey = documentAttachmentKey)) {
      telemetryClient.renderEvent(ATTACHMENT_EXISTS, renderRequest)
      log.info("attachment for request: {} exists no action required", documentAttachmentKey)
    } else {
      telemetryClient.renderEvent(GET_ATTACHMENT_STARTED, renderRequest)
      log.info(
        "attachment for request: {} does not exist downloading ${attachment.filename} for {}",
        documentAttachmentKey,
        renderRequest.serviceConfiguration.serviceName,
      )
      val attachmentData =
        dynamicServicesClient.getAttachment(renderRequest, attachment.url, attachment.contentType, attachment.filesize)
      telemetryClient.renderEvent(GET_ATTACHMENT_COMPLETE, renderRequest)
      storeAttachment(renderRequest, attachment, attachmentData)
    }
  }

  private fun getDataForSubject(renderRequest: RenderRequest): ServiceData {
    telemetryClient.renderEvent(GET_SERVICE_DATA, renderRequest)
    log.info(
      "Retrieved service data for id={}, service={}",
      renderRequest.id,
      renderRequest.serviceConfiguration.serviceName,
    )

    try {
      val response: ResponseEntity<ServiceData> = dynamicServicesClient
        .getSubjectAccessRequestData(renderRequest) ?: throw SubjectAccessRequestException(
        message = "API response data was null",
        cause = null,
        subjectAccessRequestId = renderRequest.id,
        params = mapOf("serviceUrl" to renderRequest.serviceConfiguration.url),
      )

      log.info("get data response status:  ${response.statusCode}")
      return extractResponseBody(response, renderRequest).sanitize()
    } catch (ex: Exception) {
      if (ex is SubjectAccessRequestException) {
        throw ex
      }

      throw SubjectAccessRequestException(
        message = "get data request failed with exception",
        cause = ex,
        subjectAccessRequestId = renderRequest.id,
        params = mapOf("serviceUrl" to renderRequest.serviceConfiguration.url),
      )
    }
  }

  private fun extractResponseBody(
    response: ResponseEntity<ServiceData>,
    renderRequest: RenderRequest,
  ): ServiceData = when (response.statusCode.value()) {
    HttpStatus.OK.value() -> {
      telemetryClient.renderEvent(SERVICE_DATA_RETURNED, renderRequest)
      response.body.also {
        log.info(
          "received response body, id: {}, service: {}",
          renderRequest.id,
          renderRequest.serviceConfiguration.serviceName,
        )
      }
    }

    HttpStatus.NO_CONTENT.value() -> {
      telemetryClient.renderEvent(SERVICE_DATA_NO_CONTENT, renderRequest)
      ServiceData()
    }

    STATUS_IDENTIFIER_TYPE_NOT_SUPPORTED -> {
      telemetryClient.renderEvent(SERVICE_DATA_NO_CONTENT_ID_NOT_SUPPORTED, renderRequest)
      ServiceData()
    }

    else -> throw SubjectAccessRequestException(
      message = "get service data returned unexpected response status",
      cause = null,
      subjectAccessRequestId = renderRequest.id,
      params = mapOf(
        "status" to response.statusCode,
        "serviceName" to renderRequest.serviceConfiguration.serviceName,
        "serviceUrl" to renderRequest.serviceConfiguration.url,
      ),
    )
  }

  private suspend fun storeJson(renderRequest: RenderRequest, serviceData: ServiceData) {
    telemetryClient.renderEvent(STORE_SERVICE_DATA_STARTED, renderRequest)
    log.info("storing json for id={}, service={}", renderRequest.id, renderRequest.serviceConfiguration.serviceName)

    documentStore.addJson(renderRequest, objectMapper.writeValueAsString(serviceData).toByteArray(UTF_8))

    telemetryClient.renderEvent(STORE_SERVICE_DATA_COMPLETED, renderRequest)
    log.info(
      "json stored successfully for id={}, service={}",
      renderRequest.id,
      renderRequest.serviceConfiguration.serviceName,
    )
  }

  private suspend fun storeRenderedHtml(renderRequest: RenderRequest, renderedData: ByteArrayOutputStream?) {
    telemetryClient.renderEvent(RenderEvent.STORE_RENDERED_HTML_STARTED, renderRequest)
    log.info(
      "stored rendered html document for id={}, service={}",
      renderRequest.id,
      renderRequest.serviceConfiguration.serviceName,
    )

    documentStore.addHtml(renderRequest, renderedData?.toByteArray())

    telemetryClient.renderEvent(RenderEvent.STORE_RENDERED_HTML_COMPLETED, renderRequest)
    log.info(
      "html document stored successfully for id={}, service={}",
      renderRequest.id,
      renderRequest.serviceConfiguration.serviceName,
    )
  }

  private suspend fun storeAttachment(renderRequest: RenderRequest, attachment: Attachment, attachmentData: ByteArray) {
    telemetryClient.renderEvent(STORE_ATTACHMENT_STARTED, renderRequest)
    log.info(
      "storing attachment {} for id={}, service={}",
      attachment.attachmentNumber,
      renderRequest.id,
      renderRequest.serviceConfiguration.serviceName,
    )

    documentStore.addAttachment(renderRequest, attachment, attachmentData)

    telemetryClient.renderEvent(STORE_ATTACHMENT_COMPLETED, renderRequest)
    log.info(
      "attachment {} stored successfully for id={}, service={}",
      attachment.attachmentNumber,
      renderRequest.id,
      renderRequest.serviceConfiguration.serviceName,
    )
  }

  enum class RenderResult {
    /**
     * Service data was rendered to HTML and added to the document store.
     */
    CREATED,

    /**
     * Document store contains existing entry for subject access request ID/service name combination. No action required
     */
    DATA_ALREADY_EXISTS,
  }
}
