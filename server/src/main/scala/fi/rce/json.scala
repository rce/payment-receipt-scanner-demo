package fi.rce

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.{DeserializationFeature, JsonNode}
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.scala.{ClassTagExtensions, DefaultScalaModule, JavaTypeable}
//import com.github.pjfanning.jackson.reflection.ScalaReflectionExtensions

import scala.reflect.{ClassTag, classTag}

object Json {
  private val mapper = JsonMapper.builder()
    .addModule(new DefaultScalaModule)
    .addModule(new JavaTimeModule)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
    .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
    .build() :: ClassTagExtensions

  def mkString[T](value: T): String =
    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value)

  def parse[T : JavaTypeable : ClassTag](json: String): T = {
    mapper.readValue(json) match {
      case null => throw new IllegalArgumentException(s"Cannot parse null value to given type ${classTag[T].runtimeClass.getTypeName}")
      case value => value
    }
  }
}


extension (node: JsonNode) {
  def getOpt(key: String): Option[JsonNode] =
    if node.has(key) then
      Some(node.get(key))
    else
      None
  def maybeLong(key: String): Option[Long] =
    node.getOpt(key).map(_.asLong)
  def maybeBoolean(key: String): Option[Boolean] =
    node.getOpt(key).map(_.asBoolean)
}

