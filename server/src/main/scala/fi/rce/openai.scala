package fi.rce

class OpenAIClient(
  val apiKey: String,
  val organization: Option[String] = None
) {
  val http = Http(
    baseurl = "https://api.openai.com",
    defaultHeaders = Map("Authorization" -> s"Bearer $apiKey"),
  )

  def prompt(system: String, input: String): String = {
    val result = chatCompletions(ChatCompletionRequest(
      model = "gpt-3.5-turbo",
      messages = Seq(Message("system", system), Message("user", input)),
    ))
    result.choices.head.message.content
  }

  def chatCompletions(req: ChatCompletionRequest): ChatCompletionResponse =
    http.postJson[ChatCompletionResponse, ChatCompletionRequest]("/v1/chat/completions", req).get
}


case class ChatCompletionRequest(
  model: String,
  messages: Seq[Message],
  temperature: Option[Double] = None,
)

case class ChatCompletionResponse(
  id: String,
  `object`: String,
  created: Long,
  model: String,
  usage: Usage,
  choices: Seq[Choice],
)

case class Usage(prompt_tokens: Int, completion_tokens: Int, total_tokens: Int)

case class Choice(message: Message, finish_reason: String, index: Int)

case class Message(role: String, content: String)