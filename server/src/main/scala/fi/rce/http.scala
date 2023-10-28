package fi.rce

import com.fasterxml.jackson.module.scala.JavaTypeable

import java.net.URI
import java.net.http.HttpRequest.{BodyPublisher, BodyPublishers}
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import scala.reflect.ClassTag

class Http(
  val baseurl: String,
  val defaultHeaders: Map[String, String] = Map.empty,
) {
  private val client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build()

  def getJson[T: JavaTypeable : ClassTag](path: String): Option[T] = {
    val req = mkRequest("GET", path).build()
    executeRequest(req) match {
      case Response(200, body) => Some(Json.parse[T](body))
      case _ => None
    }
  }

  def postJson[
    Response: JavaTypeable : ClassTag,
    Request: JavaTypeable : ClassTag,
  ](
    path: String,
    requestBody: Request,
  ) = {
    val json = Json.mkString(requestBody)
    val req = mkRequest("POST", path, BodyPublishers.ofString(json))
      .header("Content-Type", "application/json")
      .build()

    executeRequest(req) match {
      case Response(200, body) => Some(Json.parse[Response](body))
      case _ => None
    }
  }

  private def mkRequest(
    method: "GET" | "POST",
    path: String,
    bodyPublisher: BodyPublisher = BodyPublishers.noBody()
  ): HttpRequest.Builder = {
    val builder = HttpRequest.newBuilder()
      .uri(URI.create(baseurl + path))
      .method(method, bodyPublisher)
      .header("Accept", "application/json")
      .timeout(Duration.ofSeconds(60))
    defaultHeaders.foreach { case (key, value) =>
      builder.header(key, value)
    }
    builder
  }

  private def executeRequest(request: HttpRequest): Response = {
    val response: HttpResponse[String] = client.send(request, BodyHandlers.ofString())
    val body = response.body()
    Log.info(s"GET ${request.uri()} ${response.statusCode()} ${Json.mkString(body)}")
    Response(response.statusCode(), body)
  }
}

case class Response(val status: Int, val body: String)
