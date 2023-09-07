//package io.horizen.account.utils
//
//import akka.actor.{Actor, ActorSystem, Props}
//import akka.pattern.ask
//import akka.routing.RoundRobinPool
//import akka.util.Timeout
//import com.rometools.rome.feed.synd.SyndEntry
//import com.rometools.rome.io.{SyndFeedInput, XmlReader}
//import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
//
//import java.security.MessageDigest
//import java.time.{Duration, Instant, ZoneOffset, ZonedDateTime}
//import java.util
//import java.util.concurrent.TimeUnit
//import java.util.{Collections, Comparator}
//import scala.concurrent.Await
//import scala.concurrent.duration.FiniteDuration
//import scala.util.Success
//import scala.xml.{Node, XML}
//
//object OFACFeedSubscriber {
//  implicit val system: ActorSystem = ActorSystem("OFACFeedSystem")
//  def main(): Unit = {
//
//    // Create an actor that periodically checks the RSS feed for updates
//    val feedSubscriberActorRef = system.actorOf(Props[FeedSubscriberActor].withRouter(RoundRobinPool(1)))
//
//    val initialDelayScala: FiniteDuration = new FiniteDuration(3, TimeUnit.SECONDS)
//    val repeatedIntervalScala: FiniteDuration = new FiniteDuration(10, TimeUnit.SECONDS)
//
//    // Define a Runnable implementation
//    val checkFeedRunnable: Runnable = () => {
//      feedSubscriberActorRef ! CheckFeed
//    }
//
//    // Schedule the actor to run every 24 hours at UTC noon
//    import system.dispatcher // Import the execution context
//    system.scheduler.scheduleWithFixedDelay(initialDelayScala, repeatedIntervalScala)(checkFeedRunnable)
//  }
//
//  def getLatestEntryHash: String = {
//    val feedEntryStorageSubscriberRef = system.actorOf(Props[FeedEntryStorageActor].withRouter(RoundRobinPool(1)))
//    implicit val timeout: Timeout = Timeout(10.seconds)
//    val res = Await.result(feedEntryStorageSubscriberRef ? GetLatestHash, timeout.duration)
//    res match {
//      case Success(latestHash) =>
//        latestHash.toString
//    }
//  }
//
//  case object CheckFeed
//  case object GetLatestHash
//  case object UpdateOFACList
//  case class FeedEntry(title: String, link: String, publishedDate: Instant, entryHash: String)
//
//  class FeedSubscriberActor extends Actor {
//    private val rssFeedUrl = "https://ofac.treasury.gov/media/3231/download?inline" // Replace with the actual OFAC RSS feed URL
//    private val feedEntryStorageActorRef = context.actorOf(Props[FeedEntryStorageActor])
//
//    override def receive: Receive = {
//      case CheckFeed =>
//        try {
//          val feed = fetchRSSFeed(rssFeedUrl)
//          val entries: util.List[SyndEntry] = feed.getEntries
//          // Sort entries by published date in descending order
//          Collections.sort(entries, new Comparator[SyndEntry] {
//            override def compare(entry1: SyndEntry, entry2: SyndEntry): Int =
//              entry2.getPublishedDate.compareTo(entry1.getPublishedDate)
//          })
//
//          val latestEntry = entries.get(0)
//          val entryHash = calculateHash(latestEntry)
//          feedEntryStorageActorRef ! FeedEntry(latestEntry.getTitle, latestEntry.getLink, latestEntry.getPublishedDate.toInstant, entryHash)
//
//        } catch {
//          case e: Exception =>
//            e.printStackTrace()
//        }
//    }
//
//    private def fetchRSSFeed(feedUrl: String): com.rometools.rome.feed.synd.SyndFeed = {
//      val url = new java.net.URL(feedUrl)
//      val xmlReader = new XmlReader(url)
//      val input = new SyndFeedInput()
//      input.build(xmlReader)
//    }
//
//    private def calculateHash(entry: SyndEntry): String = {
//      val md = MessageDigest.getInstance("SHA-256")
//      val contentToHash = s"${entry.getTitle}${entry.getLink}${entry.getPublishedDate.toInstant}"
//      md.digest(contentToHash.getBytes("UTF-8")).map("%02x".format(_)).mkString
//    }
//  }
//
//  class FeedEntryStorageActor extends Actor {
//    private var latestEntryHash = ""
//    private val OFACListStorageActorRef = context.actorOf(Props[OFACListStorageActor])
//
//    override def receive: Receive = {
//      case FeedEntry(title, link, publishedDate, entryHash) =>
//        if (entryHash != latestEntryHash) {
//          println(s"OFAC RSS FEED - New update detected: $title, $link, $publishedDate")
//          latestEntryHash = entryHash
//          OFACListStorageActorRef ! UpdateOFACList
//        }
//        else {
//          OFACListStorageActorRef ! UpdateOFACList
//          println("OFAC RSS FEED - No new update")
//        }
//      case GetLatestHash =>
//        sender() ! Success(latestEntryHash)
//    }
//  }
//
//  class OFACListStorageActor extends Actor {
//    private var ofacList:List[String] = List()
//    private val url = "https://www.treasury.gov/ofac/downloads/sanctions/1.0/sdn_advanced.xml"
//    private var i = 0;
//
//
//    override def receive: Receive = {
//      case UpdateOFACList =>
//        ofacList = ofacList :+ s"Element $i"
//        i = i + 1
//        val a = 4
////        try {
////          // Download the XML content
////          val xmlContent = Source.fromURL(url).mkString
////
////          // Parse the XML content
////          val rootNode = XML.loadString(xmlContent)
////
////          // Process the XML data as needed
////          // For example, print the first child node
////          val a = 4
////        }
////        finally {
////          Source.stdin.close();
////        }
//
//
//
//        //second way better
////        implicit val executionContext: ExecutionContextExecutor = system.dispatcher
////
////        val url = "https://www.treasury.gov/ofac/downloads/sanctions/1.0/sdn_advanced.xml"
////        val outputPath = "./sdn_advanced.xml"
////
////        val request = HttpRequest(HttpMethods.GET, uri = url)
////        val responseFuture = Http().singleRequest(request)
////
////        responseFuture.flatMap { response =>
////          response.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
////        }.map { byteString =>
////          val byteArray = byteString.toArray
////          Files.write(Paths.get(outputPath), byteArray)
////          println(s"Large XML file saved to: $outputPath")
////
////          // Now you can proceed to parse the XML file
////          parseXMLFile(outputPath)
////        }
//    }
//
//    def parseXMLFile(filePath: String): Unit = {
//      val xmlContent = XML.loadFile(filePath)
//      // Process the XML content as needed
//      // For example: val elements = (xmlContent \\ "elementName")
//      val assets = List("ETH") // Specify your assets here
//      for (asset <- assets) {
//        val addressId = getAddressId(xmlContent, asset)
//        val addresses = getSanctionedAddresses(xmlContent, addressId)
//
//        // Deduplicate addresses
//        val uniqueAddresses = addresses.distinct
//
//        // Sort addresses
//        val sortedAddresses = uniqueAddresses.sorted
//
//      }
//    }
//
//
//    def getAddressId(root: Node, asset: String): String = {
//      val expression = s"sdn:ReferenceValueSets/sdn:FeatureTypeValues/*[.='${featureTypeText(asset)}']"
//      val featureTypeNode = (root \\ expression).head
//
//      featureTypeNode.attribute("ID").getOrElse(
//        throw new RuntimeException(s"No FeatureType with the name ${featureTypeText(asset)} found")
//      ).text
//    }
//
//    def featureTypeText(asset: String): String = s"Digital Currency Address - $asset"
//
//    def getSanctionedAddresses(root: Node, addressId: String): List[String] = {
//      val expression = s"//sdn:DistinctParties//*[@FeatureTypeID='$addressId']//sdn:VersionDetail"
//      (root \\ expression).map(_.text).toList
//    }
//
////    def writeAddresses(addresses: List[String], asset: String, outputFormats: List[String], outPath: String): Unit = {
////      if (outputFormats.contains("TXT")) {
////        writeAddressesTxt(addresses, asset, outPath)
////      }
////      if (outputFormats.contains("JSON")) {
////        writeAddressesJson(addresses, asset, outPath)
////      }
////    }
//
////    def writeAddressesTxt(addresses: List[String], asset: String, outPath: String): Unit = {
////      val outputFile = new File(s"$outPath/sanctioned_addresses_$asset.txt")
////      val writer = new java.io.PrintWriter(outputFile)
////      addresses.foreach(writer.println)
////      writer.close()
////    }
////
////    def writeAddressesJson(addresses: List[String], asset: String, outPath: String): Unit = {
////      val outputFile = new File(s"$outPath/sanctioned_addresses_$asset.json")
////      val writer = new java.io.PrintWriter(outputFile)
////      writer.println(Json.toJson(addresses).toString())
////      writer.close()
////    }
//  }
//
//  private val initialDelay = Duration.between(Instant.now(), calculateNextMidday(Instant.now())).plusMillis(5 * 60 * 60 * 1000) // Adjust for your time zone offset
//  private val repeatInterval = Duration.ofDays(1)
//
//  private def calculateNextMidday(currentInstant: Instant): Instant = {
//    val nextMidday = ZonedDateTime.ofInstant(currentInstant, ZoneOffset.UTC)
//      .withHour(12)
//      .withMinute(0)
//      .withSecond(0)
//      .withNano(0)
//      .plusDays(1)
//    nextMidday.toInstant
//  }
//}
