package io.horizen.examples;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import java.net.URL;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.*;

public class OFACFeedSubscriber {
    private static String latestEntryHash = ""; // Store the latest entry hash

    public static void main(String[] args) {
        ScheduledExecutorService scheduledExecutorService  = Executors.newScheduledThreadPool(1);
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        Instant now = Instant.now();
        Instant nextMidday = calculateNextMidday(now);

        long initialDelay = Duration.between(now, nextMidday).toMillis();

        final Future<?>[] future = {executorService.submit(OFACFeedSubscriber::fetchOFACRSSFeed)};


        // Schedule the task to run every 24 hours on UTC noon
//        executorService.scheduleAtFixedRate(OFACFeedSubscriber::fetchOFACRSSFeed, initialDelay, 24, TimeUnit.HOURS);

        // Schedule the task to run immediately and repeat every 24 hours at UTC noon
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            future[0].cancel(true); // Cancel the previous task
            future[0] = executorService.submit(() -> fetchOFACRSSFeed()); // Submit a new task
        }, initialDelay, 24, TimeUnit.HOURS);

        // Keep the program running
        try {
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    private static void fetchOFACRSSFeed() {
        try {
            URL feedUrl = new URL("https://ofac.treasury.gov/media/3231/download?inline");
            XmlReader xmlReader = new XmlReader(feedUrl);

            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(xmlReader);

            List<SyndEntry> entries = feed.getEntries();

            // Calculate hash of the latest entry
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String newLatestEntryHash = hashEntry(entries.get(0), md);

            // Compare with stored hash
            if (!newLatestEntryHash.equals(latestEntryHash)) {
                System.out.println("New update detected!");
                latestEntryHash = newLatestEntryHash;
            }

            System.out.println("Hash updated: " + latestEntryHash);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String hashEntry(SyndEntry entry, MessageDigest md) {
        // Construct a string using entry's properties that you want to include in the hash
        String hashString = entry.getTitle() + entry.getLink() + entry.getPublishedDate();

        // Convert the hashString to bytes and calculate the hash
        byte[] hashBytes = md.digest(hashString.getBytes());

        // Convert hashBytes to hexadecimal format
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            hexString.append(String.format("%02x", b));
        }

        return hexString.toString();
    }

    private static Instant calculateNextMidday(Instant currentInstant) {
        Instant nextMidday = currentInstant.atZone(ZoneOffset.UTC)
                .with(LocalTime.NOON)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toInstant();

        // If the current time is after midday, move to the next day
        if (currentInstant.isAfter(nextMidday)) {
            nextMidday = nextMidday.plus(Duration.ofDays(1));
        }

        return nextMidday;
    }
}
