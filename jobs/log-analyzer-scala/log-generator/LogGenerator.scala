import java.io.{BufferedWriter, FileWriter}
import java.time.{LocalDateTime, ZoneOffset, Duration}
import java.time.format.DateTimeFormatter
import java.util.Locale
import scala.util.Random

object LogGenerator {

  private val random = new Random()
  private val ipAddressChars = "0123456789"

  // Helper to generate random IP addresses
  private def generateRandomIp(): String = {
    (1 to 4).map(_ => random.nextInt(256)).mkString(".")
  }

  // Helper to generate random user names or return "-"
  private def generateRandomUser(): String = {
    if (random.nextBoolean()) "-"
    else {
      val names = Seq("frank", "user1", "jdoe", "api_user", "crawler", "guest", "admin_test")
      names(random.nextInt(names.length)) + (if(random.nextBoolean()) random.nextInt(100).toString else "")
    }
  }
  
  // Helper to generate random slugs for URLs
  private def generateSlug(length: Int = random.nextInt(5) + 5): String = {
    (1 to length).map(_ => (random.nextInt(26) + 'a').toChar).mkString
  }

  // Helper to generate random words for URLs
  private def generateWord(length: Int = random.nextInt(7) + 3): String = {
    (1 to length).map(_ => (random.nextInt(26) + 'a').toChar).mkString
  }


  def generateLogLine(): String = {
    val ip = generateRandomIp()
    val user = generateRandomUser()

    // Generate a timestamp within the last year
    val now = LocalDateTime.now()
    val oneYearAgo = now.minusYears(1)
    val randomSecondsInYear = random.nextLong(Duration.between(oneYearAgo, now).getSeconds)
    val randomDate = oneYearAgo.plusSeconds(randomSecondsInYear)
    // Format: [dd/MMM/yyyy:HH:mm:ss Z] e.g., [10/Oct/2000:13:55:36 -0700]
    // Note: Scala's DateTimeFormatter for 'Z' produces offset like +0700, not -07:00 directly without custom handling.
    // For simplicity, we'll use a fixed offset or rely on system default if ZoneOffset is not specified.
    // Using a common timezone for logs, like UTC or a fixed offset.
    val timestampFormatter = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH)
    val timestamp = randomDate.atOffset(ZoneOffset.ofHours(-random.nextInt(12))).format(timestampFormatter) // Random timezone offset

    val methods = Seq("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "PATCH")
    val method = methods(random.nextInt(methods.length))

    val pathStems = Seq("/articles", "/products", "/services", "/about", "/contact", "/blog", "/news", "/media", "/api/v1", "/user/profile")
    val pathDetailsOptions = Seq(
      s"/${generateSlug()}",
      s"/${generateWord()}/${random.nextInt(1000) + 1}",
      s"/${generateWord()}/${generateSlug()}",
      s"/${random.nextInt(50000) + 1000}"
    )
    val resourceExtensions = Seq(".html", ".php", ".aspx", ".jsp", ".pdf", ".jpg", ".png", ".gif", ".css", ".js", ".json", ".xml", ".txt", "")
    
    var url = pathStems(random.nextInt(pathStems.length)) +
              pathDetailsOptions(random.nextInt(pathDetailsOptions.length)) +
              resourceExtensions(random.nextInt(resourceExtensions.length))
    
    // Ensure URL starts with a slash and handles cases where extension might be empty
    if (!url.startsWith("/")) url = "/" + url
    if (url.endsWith("/") && url.length > 1) url = url.dropRight(1) // Avoid trailing slash if it's not just "/"
    if (url.isEmpty) url = "/" // Default to root if somehow empty


    val protocols = Seq("HTTP/1.0", "HTTP/1.1", "HTTP/2.0")
    val protocol = protocols(random.nextInt(protocols.length))

    val statusCodes = Seq(200, 201, 204, 301, 302, 304, 400, 401, 403, 404, 500, 502, 503)
    val statusWeights = Seq(70,  5,   2,   3,   3,   2,   3,   2,   1,   5,   2,   1,   1) // Weighted towards 200 OK
    
    // Simple weighted random selection
    val weightedList = statusCodes.zip(statusWeights).flatMap { case (item, weight) => Seq.fill(weight)(item) }
    val statusCode = weightedList(random.nextInt(weightedList.length))

    var responseSize = 0
    statusCode match {
      case sc if sc >= 200 && sc < 300 => responseSize = random.nextInt(50000) + 100 // 100 bytes to 50KB for success
      case 404 => responseSize = random.nextInt(1000) + 100 // Smaller for 404
      case _ => responseSize = random.nextInt(500) // Small or 0 for other errors/redirects
    }
    if (method == "HEAD") responseSize = 0 // HEAD requests have no body

    s"""$ip - $user [$timestamp] "$method $url $protocol" $statusCode $responseSize"""
  }

  def main(args: Array[String]): Unit = {
    val numLines = if (args.length > 0) args(0).toIntOption.getOrElse(1000000) else 1000000 // Default 1 million
    val outputFile = if (args.length > 1) args(1) else "web_server_logs.txt"

    println(s"Generating $numLines log entries to $outputFile...")
    val startTime = System.nanoTime()

    val writer = new BufferedWriter(new FileWriter(outputFile))
    try {
      for (i <- 1 to numLines) {
        writer.write(generateLogLine())
        writer.newLine()
        if (i % (numLines / 100 max 1) == 0) { // Print progress every 1% (ensure numLines/100 is at least 1)
          val percentComplete = (i.toDouble / numLines * 100).toInt
          print(s"\rProgress: $percentComplete%")
        }
      }
    } finally {
      writer.close()
    }
    
    val endTime = System.nanoTime()
    val durationSeconds = (endTime - startTime) / 1e9d
    println(f"\nGenerated $numLines log entries in $durationSeconds%.2f seconds.")
  }
}
