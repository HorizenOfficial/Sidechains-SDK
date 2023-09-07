package io.horizen.account.utils

import akka.actor.{Actor, Cancellable, Timers}
import akka.util.Timeout
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.io.{SyndFeedInput, XmlReader}
import io.horizen.SidechainSettings
import io.horizen.account.utils.OFACActorReceivableMessages.{CheckFeed, FeedEntry, GetLatestHash, UpdateOFACList}
import sparkz.util.SparkzLogging

import java.security.MessageDigest
import java.time.Instant
import java.util
import java.util.concurrent.TimeUnit
import java.util.{Collections, Comparator}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.util.Success

class OFACActor(settings: SidechainSettings)(implicit ec: ExecutionContext) extends Actor with Timers with SparkzLogging {
  private var latestEntryHash = ""
  val timeoutDuration: FiniteDuration = settings.sparkzSettings.restApi.timeout
  implicit val timeout: Timeout = Timeout(timeoutDuration)
  private val rssFeedUrl = "https://ofac.treasury.gov/media/3231/download?inline"
  private var ofacList:List[String] = List()
  private val url = "https://www.treasury.gov/ofac/downloads/sanctions/1.0/sdn_advanced.xml"
  private var i = 0



  //scheduling - taken from SyncStatusActor
  // Start the scheduler, it will compare the new block events density between two scheduler calls
  //todo schedule only if OFACFiltering enabled (take sidechainSettings parameter)
  private val initialDelayScala: FiniteDuration = new FiniteDuration(3, TimeUnit.SECONDS)
  private val repeatedIntervalScala: FiniteDuration = new FiniteDuration(5, TimeUnit.SECONDS)
  private val checkFeedScheduler: Cancellable = context.system.scheduler.scheduleAtFixedRate(
    initialDelayScala, repeatedIntervalScala, self, CheckFeed)
  //



  override def receive: Receive = {
    case CheckFeed =>
      try {
        val feed = fetchRSSFeed(rssFeedUrl)
        val entries: util.List[SyndEntry] = feed.getEntries
        // Sort entries by published date in descending order
        Collections.sort(entries, new Comparator[SyndEntry] {
          override def compare(entry1: SyndEntry, entry2: SyndEntry): Int =
            entry2.getPublishedDate.compareTo(entry1.getPublishedDate)
        })

        val latestEntry = entries.get(0)
        val entryHash = calculateHash(latestEntry)
        self ! FeedEntry(latestEntry.getTitle, latestEntry.getLink, latestEntry.getPublishedDate.toInstant, entryHash)

      } catch {
        case e: Exception =>
          e.printStackTrace()
      }
    case FeedEntry(title, link, publishedDate, entryHash) =>
      if (entryHash != latestEntryHash) {
        println(s"OFAC RSS FEED - New update detected: $title, $link, $publishedDate")
        latestEntryHash = entryHash
        self ! UpdateOFACList
      }
      else {
        println("OFAC RSS FEED - No new update")
      }
    case GetLatestHash =>
      sender() ! Success(latestEntryHash)
    case UpdateOFACList =>
      ofacList = ofacList :+ s"Element $i"
      i = i + 1
      val a = 4
//        try {
//          // Download the XML content
//          val xmlContent = Source.fromURL(url).mkString
//
//          // Parse the XML content
//          val rootNode = XML.loadString(xmlContent)
//
//          // Process the XML data as needed
//          // For example, print the first child node
//          val a = 4
//        }
//        finally {
//          Source.stdin.close();
//        }



      //second way better
//        implicit val executionContext: ExecutionContextExecutor = system.dispatcher
//
//        val url = "https://www.treasury.gov/ofac/downloads/sanctions/1.0/sdn_advanced.xml"
//        val outputPath = "./sdn_advanced.xml"
//
//        val request = HttpRequest(HttpMethods.GET, uri = url)
//        val responseFuture = Http().singleRequest(request)
//
//        responseFuture.flatMap { response =>
//          response.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
//        }.map { byteString =>
//          val byteArray = byteString.toArray
//          Files.write(Paths.get(outputPath), byteArray)
//          println(s"Large XML file saved to: $outputPath")
//
//          // Now you can proceed to parse the XML file
//          parseXMLFile(outputPath)
//        }
  }

  private def fetchRSSFeed(feedUrl: String): com.rometools.rome.feed.synd.SyndFeed = {
    val url = new java.net.URL(feedUrl)
    val xmlReader = new XmlReader(url)
    val input = new SyndFeedInput()
    input.build(xmlReader)
  }

  private def calculateHash(entry: SyndEntry): String = {
    val md = MessageDigest.getInstance("SHA-256")
    val contentToHash = s"${entry.getTitle}${entry.getLink}${entry.getPublishedDate.toInstant}"
    md.digest(contentToHash.getBytes("UTF-8")).map("%02x".format(_)).mkString
  }
}

object OFACActorReceivableMessages {
  case object CheckFeed

  case object GetLatestHash

  case object UpdateOFACList

  case class FeedEntry(title: String, link: String, publishedDate: Instant, entryHash: String)
}
