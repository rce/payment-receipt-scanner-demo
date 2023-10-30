package fi.rce

import com.amazonaws.services.lambda.runtime.events.{APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse}
import com.amazonaws.services.lambda.runtime.{Context, LambdaLogger, RequestHandler}
import org.apache.commons.fileupload.*
import org.apache.commons.fileupload.disk.DiskFileItemFactory
import org.apache.commons.io.output.ByteArrayOutputStream

import java.io.{ByteArrayInputStream, InputStream}
import scala.util.Using

case class Input(x: Int)

case class Output(transaction: Transaction)

class Lambda extends RequestHandler[APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse] {
  val decoder = java.util.Base64.getDecoder

  def parseImageFromRequestBodyForm(body: Array[Byte], contentType: String): Array[Byte] = Log.time("parseImageFromBody") {
    val factory = new DiskFileItemFactory()
    val fileUpload = new FileUpload()
    val iterator = fileUpload.getItemIterator(new RequestContext {
      override def getContentLength: Int = body.length

      override def getCharacterEncoding: String = "UTF-8"

      override def getContentType: String = contentType

      override def getInputStream: java.io.InputStream = new ByteArrayInputStream(body)
    })

    while (iterator.hasNext) {
      val item = iterator.next
      Log.info(s"item: ${item.getFieldName}, isField ${item.isFormField}")
      if (item.getFieldName == "image") {
        return Using(item.openStream())(_.readAllBytes()).get
      }
    }
    throw new RuntimeException("No image field")
  }

  override def handleRequest(event: APIGatewayV2HTTPEvent, context: Context): APIGatewayV2HTTPResponse = Log.time("handleRequest") {
    val logger = context.getLogger()
    logger.log("Starting")
    try {
      val body = if (event.getIsBase64Encoded) {
        Log.time("Decoding base64") {
          decoder.decode(event.getBody)
        }
      } else {
        throw RuntimeException("Not base64 encoded")
      }

      val blob = parseImageFromRequestBodyForm(body, event.getHeaders.get("content-type"))
      val tx = ReceiptReader.read(blob)
      val result = APIGatewayV2HTTPResponse.builder()
        .withStatusCode(200)
        .withBody(Json.mkString(tx))
        .build()
      logger.log("Done")
      result
    } catch {
      case e: Exception =>
        logger.log("Error: " + e.getMessage)
        Log.info(s"Error: ${e.getMessage}")
        APIGatewayV2HTTPResponse.builder()
          .withStatusCode(500)
          .withBody(s"""{"error": "Internal server error", "message": "${e.getMessage}"}""")
          .build()
    }
  }
}

