package com.jse.sentiment.pipeline;

import com.jse.sentiment.model.NewsArticle;
import com.jse.sentiment.pipeline.stages.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class NewsPipeline {

    private static final Logger logger = LoggerFactory.getLogger(NewsPipeline.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IngestionStage ingestionStage;
    private final CleaningStage cleaningStage;
    private final SectorClassificationStage sectorClassificationStage;
    private final SentimentAnalysisStage sentimentAnalysisStage;
    private final StorageAndBroadcastStage storageAndBroadcastStage;

    // In-memory rolling log stream for the frontend dashboard
    private final Queue<String> pipelineLogs = new ConcurrentLinkedQueue<>();
    private final int MAX_LOG_SIZE = 100;

    public NewsPipeline(
            IngestionStage ingestionStage,
            CleaningStage cleaningStage,
            SectorClassificationStage sectorClassificationStage,
            SentimentAnalysisStage sentimentAnalysisStage,
            StorageAndBroadcastStage storageAndBroadcastStage) {
        this.ingestionStage = ingestionStage;
        this.cleaningStage = cleaningStage;
        this.sectorClassificationStage = sectorClassificationStage;
        this.sentimentAnalysisStage = sentimentAnalysisStage;
        this.storageAndBroadcastStage = storageAndBroadcastStage;
        
        logToStream("System pipeline initialized. Ready to process JSE news.");
    }

    // Run automatically every 5 minutes
    @Scheduled(fixedRateString = "${news.scrape.interval-ms:300000}")
    public synchronized List<NewsArticle> execute() {
        logToStream("Triggering pipeline execution...");
        long startTime = System.currentTimeMillis();
        List<NewsArticle> articles = new ArrayList<>();

        try {
            // Stage 1: Ingestion
            logToStream("Stage 1/5 [INGESTION]: Scraping RSS feeds...");
            articles = ingestionStage.process(null);
            logToStream(String.format("Stage 1/5 [INGESTION] Complete. Fetched %d raw articles.", articles.size()));

            if (articles.isEmpty()) {
                logToStream("No new articles fetched. Terminating current pipeline run.");
                return articles;
            }

            // Stage 2: Cleaning
            logToStream("Stage 2/5 [CLEANING]: Normalizing text markup...");
            articles = cleaningStage.process(articles);
            logToStream(String.format("Stage 2/5 [CLEANING] Complete. Cleaned %d headlines.", articles.size()));

            // Stage 3: Sector Classification
            logToStream("Stage 3/5 [CLASSIFICATION]: Mapping articles to JSE sectors...");
            articles = sectorClassificationStage.process(articles);
            logToStream("Stage 3/5 [CLASSIFICATION] Complete.");

            // Stage 4: Sentiment Analysis
            logToStream("Stage 4/5 [SENTIMENT]: Executing Stanford CoreNLP / Regex sentiment analysis...");
            articles = sentimentAnalysisStage.process(articles);
            logToStream("Stage 4/5 [SENTIMENT] Complete.");

            // Stage 5: Storage and Broadcast
            logToStream("Stage 5/5 [STORAGE]: Persisting articles and broadcasting live aggregates...");
            articles = storageAndBroadcastStage.process(articles);
            long duration = System.currentTimeMillis() - startTime;
            logToStream(String.format("Stage 5/5 [STORAGE] Complete. Saved %d new records in %d ms.", articles.size(), duration));

        } catch (Exception e) {
            logToStream(String.format("CRITICAL PIPELINE ERROR: %s", e.getMessage()));
            logger.error("Pipeline run failed: ", e);
        }

        return articles;
    }

    public void logToStream(String message) {
        String timestampedMessage = String.format("[%s] %s", LocalDateTime.now().format(formatter), message);
        pipelineLogs.offer(timestampedMessage);
        
        // Prune logs if they exceed max size
        while (pipelineLogs.size() > MAX_LOG_SIZE) {
            pipelineLogs.poll();
        }
        
        logger.info(message);
    }

    public List<String> getLogs() {
        return new ArrayList<>(pipelineLogs);
    }
    
    public void clearLogs() {
        pipelineLogs.clear();
        logToStream("Logs cleared by administrator.");
    }
}
