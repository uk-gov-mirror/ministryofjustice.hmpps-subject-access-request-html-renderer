package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.templates

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestTemplatingException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service.RenderRequest

@Service
class TemplateResourcesService(
  @Value("\${template-resources.directory}") private val templatesDirectory: String = "/templates",
) {

  companion object {
    private val log = LoggerFactory.getLogger(TemplateResourcesService::class.java)
  }

  fun getStyleTemplate(): String = getTemplateResourceOrNull("$templatesDirectory/main_stylesheet.mustache") ?: ""

  fun getServiceTemplate(
    renderRequest: RenderRequest,
  ): String {
    log.info(
      "{}.templateMigrated == {}",
      renderRequest.serviceConfiguration.serviceName,
      renderRequest.serviceConfiguration.templateMigrated,
    )
    return if (renderRequest.serviceConfiguration.templateMigrated) {
      "" // TODO
    } else {
      getTemplateResource(
        renderRequest = renderRequest,
        resourcePath = "$templatesDirectory/template_${renderRequest.serviceConfiguration.serviceName}.mustache",
      ).readText()
    }
  }

  fun getNoDataTemplate(renderRequest: RenderRequest): String = getTemplateResource(
    renderRequest = renderRequest,
    resourcePath = "$templatesDirectory/template_no_data.mustache",
  ).readText()

  private fun getTemplateResourceOrNull(path: String) = this::class.java.getResource(path)?.readText()

  private fun getTemplateResource(renderRequest: RenderRequest, resourcePath: String) = this::class.java
    .getResource(resourcePath) ?: throw templateResourceNotFoundException(renderRequest, resourcePath)

  private fun templateResourceNotFoundException(renderRequest: RenderRequest, resourcePath: String) =
    SubjectAccessRequestTemplatingException(
      subjectAccessRequestId = renderRequest.id,
      message = "template resource not found",
      params = mapOf(
        "resource" to resourcePath,
      ),
    )
}
