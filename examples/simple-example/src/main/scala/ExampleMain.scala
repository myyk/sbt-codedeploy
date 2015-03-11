import java.net.InetSocketAddress

import java.util.concurrent.CountDownLatch

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer

object ExampleMain extends App {
  val server = HttpServer.create(
    new InetSocketAddress(80),
    0)

  server.createContext("/", new HttpHandler {
    override def handle(exchange: HttpExchange): Unit = {
      val content = "sbt-codedeploy example server".getBytes
      exchange.sendResponseHeaders(200, content.length)
      val response = exchange.getResponseBody
      response.write(content)
      response.close()
    }
  })

  server.start()

  val latch = new CountDownLatch(1)
  latch.await
}
