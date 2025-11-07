package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.StringValuePattern
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.GetSubjectAccessRequestDataParams

class SarDataSourceApiMockServer : WireMockServer(8092) {

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{"status":"${if (status == 200) "UP" else "DOWN"}"}""")
          .withStatus(status),
      ),
    )
  }

  fun stubGetSubjectAccessRequestData(
    params: GetSubjectAccessRequestDataParams,
    responseDefinition: ResponseDefinitionBuilder,
  ) {
    stubFor(
      get(urlPathEqualTo("/subject-access-request"))
        .withQueryParam("prn", equalTo(params.prn))
        .withQueryParam("fromDate", equalTo(params.dateFrom.toString()))
        .withQueryParam("toDate", equalTo(params.dateTo.toString()))
        .willReturn(responseDefinition),
    )
  }

  fun stubGetSubjectAccessRequestDataSuccess(
    responseDefinition: ResponseDefinitionBuilder,
    expectedQueryParams: Map<String, StringValuePattern>,
  ) {
    stubFor(
      get(urlPathEqualTo("/subject-access-request"))
        .withQueryParams(expectedQueryParams)
        .willReturn(responseDefinition),
    )
  }

  fun stubGetSubjectAccessRequestIdentifierNotSupported(params: GetSubjectAccessRequestDataParams) {
    stubFor(
      get(urlPathEqualTo("/subject-access-request"))
        .withQueryParam("prn", equalTo(params.prn))
        .withQueryParam("fromDate", equalTo(params.dateFrom.toString()))
        .withQueryParam("toDate", equalTo(params.dateTo.toString()))
        .willReturn(
          aResponse()
            .withStatus(209)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubGetAttachment(contentType: String, content: ByteArray, filename: String) {
    stubGetAttachment(
      contentType,
      filename,
      aResponse()
        .withStatus(200)
        .withHeader(CONTENT_TYPE, contentType)
        .withBody(content),
    )
  }

  fun stubGetAttachment(contentType: String, filename: String, responseDefinition: ResponseDefinitionBuilder) {
    stubFor(
      get(urlPathEqualTo("/attachments/$filename"))
        .withHeader(CONTENT_TYPE, equalTo(contentType))
        .willReturn(responseDefinition),
    )
  }

  fun stubGetTemplate(responseDefinition: ResponseDefinitionBuilder) {
    stubFor(
      get(urlPathEqualTo("/subject-access-request/template"))
        .willReturn(responseDefinition),
    )
  }

  fun verifyGetSubjectAccessRequestDataCalled(times: Int = 1) = verify(
    times,
    getRequestedFor(urlPathEqualTo("/subject-access-request")),
  )

  fun verifyGetSubjectAccessRequestDataNeverCalled() = verify(
    0,
    getRequestedFor(urlPathEqualTo("/subject-access-request")),
  )

  fun verifyGetAttachmentCalled(filename: String, times: Int = 1) = verify(
    times,
    getRequestedFor(urlPathEqualTo("/attachments/$filename")),
  )

  fun verifyGetAttachmentNeverCalled(filename: String) = verify(
    0,
    getRequestedFor(urlPathEqualTo("/attachments/$filename")),
  )

  fun verifyGetTemplateCall(times: Int = 1) = verify(
    times,
    getRequestedFor(urlPathEqualTo("/subject-access-request/template")),
  )
}

class SarDataSourceApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val sarDataSourceApi = SarDataSourceApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext): Unit = sarDataSourceApi.start()
  override fun beforeEach(context: ExtensionContext): Unit = sarDataSourceApi.resetAll()
  override fun afterAll(context: ExtensionContext): Unit = sarDataSourceApi.stop()
}
