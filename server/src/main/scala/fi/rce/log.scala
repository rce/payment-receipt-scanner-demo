package fi.rce

import com.amazonaws.services.lambda.runtime.LambdaLogger

object Log {
  def info(msg: String) = println(msg)
  def time[T](label: String)(fun: => T): T = {
    info(s"Starting $label")
    val start = System.currentTimeMillis()
    val result: T = fun
    val end = System.currentTimeMillis()
    info(s"Finished $label in ${end - start} ms")
    result
  }
}

