package fi.rce

import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest
import software.amazon.awssdk.services.textract.TextractClient
import software.amazon.awssdk.services.textract.model.*

import scala.jdk.CollectionConverters.*

val prompt =
  raw"""You are given raw text extracted from a photo of a payment receipt.

I'd like you to generate a JSON object describing the transaction of the following form:
${
    Json.mkString(Transaction(
      transactionDate = "1970-01-01",
      payee = "R-Kioski Oy",
      items = Seq(
        Item(label = "Pieni kahvi", amount = 2.00, category = "Cafeteria"),
        Item(label = "Kana taquito 3 KPL", amount = 3.90, category = "Snacks")
      ),
      vat = Seq(
        Vat(base = "14%", gross = 2, net = 1.75, tax = 0.25),
        Vat(base = "24%", gross = 3.9, net = 3.15, tax = 0.75)
      ),
      totalSum = 5.90,
      currency = "EUR"
    ))
  }

You should be able to find items whose sum is the total sum.
There can be one or more items in the receipt.
The VAT information is optional. Use empty list if there is no VAT information.
Feel free to correct typos in item labels.
"""

object ReceiptReader {
  private val region = Region.EU_WEST_1
  private val s3Client = S3Client.builder()
    .region(region)
    .build()
  private val textractClient = TextractClient.builder()
    .region(region)
    .build()
  private val secretsmanagerClient = SecretsManagerClient.builder()
    .region(region)
    .build()

  private lazy val apiKey: String = getenv("OPENAI_API_KEY").getOrElse {
    Log.info("OPENAI_API_KEY not set, fetching from Secrets Manager")
    val response = secretsmanagerClient.getSecretValue(GetSecretValueRequest.builder()
      .secretId("openai-api-key")
      .build())
    response.secretString()
  }
  private lazy val ai = OpenAIClient(apiKey)

  def read(blob: Array[Byte]): Transaction = {
    val lines = detectLines(blob)
    if (lines.isEmpty) {
      Log.info("No lines detected in image")
      throw RuntimeException("No lines detected in image")
    } else {
      val response = Log.time("Prompt with OpenAI") {
        ai.prompt(prompt, lines.mkString("\n"))
      }
      Log.info(s"Response: $response")
      try {
        Json.parse[Transaction](response)
      } catch {
        case e: Exception =>
          Log.info(s"Failed to parse OpenAI response: $response")
          throw e
      }
    }
  }

  def detectLines(blob: Array[Byte]): Seq[String] = {
    try {
      detectLinesSync(blob)
    } catch {
      case e: Exception =>
        Log.info(s"Failed to detect lines synchronously: $e")
        detectLinesAsync(blob)
    }
  }

  private def detectLinesSync(blob: Array[Byte]) = Log.time("detectLinesSync") {
    val bytes = SdkBytes.fromByteArray(blob)
    val doc = Document.builder().bytes(bytes).build()
    val request = DetectDocumentTextRequest.builder().document(doc).build()
    val response = textractClient.detectDocumentText(request).blocks()
    val blocks = response.asScala
    val lines = blocks.filter(_.blockTypeAsString() == "LINE").map(_.text())
    lines.toSeq
  }

  def detectLinesAsync(blob: Array[Byte]): Seq[String] = Log.time("detectLinesAsync") {
    val uuid = java.util.UUID.randomUUID().toString
    val bucketName = getenv("BUCKET_NAME").get
    val objectKey = s"receipts/$uuid"

    Log.time("putObject") {
      s3Client.putObject(PutObjectRequest.builder()
        .bucket(bucketName)
        .key(objectKey)
        .build(), RequestBody.fromBytes(blob))
    }

    val jobId = Log.time("Start text detection") {
      val docLoc = DocumentLocation.builder()
        .s3Object(S3Object.builder()
          .bucket(bucketName)
          .name(objectKey)
          .build())
        .build()
      val request: StartDocumentTextDetectionRequest = StartDocumentTextDetectionRequest.builder()
        .documentLocation(docLoc)
        .build()
      textractClient.startDocumentTextDetection(request).jobId()
    }
    Log.info(s"Got job id: $jobId")


    // Attempt to get results
    var attempts = 0
    val maxAttempts = 30
    val response = Log.time("Wait for results") {
      waitForJobResult(jobId)
    }
    val blocks = response.blocks().asScala
    val lines = blocks.filter(_.blockTypeAsString() == "LINE").map(_.text())
    Log.info(s"Got ${lines.size} lines")
    Log.info(s"lines: ${lines.mkString(", ")}")
    lines.toSeq
  }

  private def waitForJobResult(jobId: String, attempts: Int = 0): GetDocumentTextDetectionResponse = {
    val maxAttempts = 30
    if (attempts > maxAttempts) throw new RuntimeException(s"Job not ready after ${maxAttempts} attempts")

    val response = textractClient.getDocumentTextDetection(_.jobId(jobId))
    response.jobStatusAsString() match {
      case "SUCCEEDED" => response
      case "PARTIAL_SUCCESS" => response
      case "IN_PROGRESS" =>
        Log.info(s"Job not ready yet, waiting...")
        Thread.sleep(2000)
        waitForJobResult(jobId, attempts + 1)
      case "FAILED" => throw new RuntimeException(s"Job failed: ${response.statusMessage()}")
      case _ => throw new RuntimeException(s"Unknown status: ${response.jobStatusAsString()}, ${response.statusMessage()}")
    }
  }
}

case class Transaction(
  transactionDate: String,
  payee: String,
  items: Seq[Item],
  vat: Seq[Vat],
  totalSum: Double,
  currency: String
)

case class Item(label: String, amount: Double, category: String)

case class Vat(base: String, gross: Double, net: Double, tax: Double)

def getenv(key: String): Option[String] = Option(System.getenv(key))