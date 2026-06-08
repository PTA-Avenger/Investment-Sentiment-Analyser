package com.jse.sentiment.controller;

import com.jse.sentiment.model.AnalyzerEngine;
import com.jse.sentiment.model.NewsArticle;
import com.jse.sentiment.model.SentimentCategory;
import com.jse.sentiment.pipeline.NewsPipeline;
import com.jse.sentiment.pipeline.stages.IngestionStage;
import com.jse.sentiment.repository.NewsArticleRepository;
import com.jse.sentiment.service.CoreNlpSentimentAnalyzer;
import com.jse.sentiment.service.RegexSentimentAnalyzer;
import com.jse.sentiment.service.SentimentService;
import com.jse.sentiment.service.SseBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/sentiment")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
public class SentimentController {

    private static final Logger logger = LoggerFactory.getLogger(SentimentController.class);

    private final NewsArticleRepository repository;
    private final SentimentService sentimentService;
    private final CoreNlpSentimentAnalyzer nlpAnalyzer;
    private final RegexSentimentAnalyzer regexAnalyzer;
    private final NewsPipeline newsPipeline;
    private final IngestionStage ingestionStage;
    private final SseBroadcaster sseBroadcaster;

    public SentimentController(
            NewsArticleRepository repository,
            SentimentService sentimentService,
            CoreNlpSentimentAnalyzer nlpAnalyzer,
            RegexSentimentAnalyzer regexAnalyzer,
            NewsPipeline newsPipeline,
            IngestionStage ingestionStage,
            SseBroadcaster sseBroadcaster) {
        this.repository = repository;
        this.sentimentService = sentimentService;
        this.nlpAnalyzer = nlpAnalyzer;
        this.regexAnalyzer = regexAnalyzer;
        this.newsPipeline = newsPipeline;
        this.ingestionStage = ingestionStage;
        this.sseBroadcaster = sseBroadcaster;
    }

    // 1. Get Sector Statistics for the last 72 hours
    @GetMapping("/sectors")
    public List<Map<String, Object>> getSectorStats() {
        LocalDateTime since = LocalDateTime.now().minusHours(72);
        List<Map<String, Object>> dbStats = repository.getSectorStats(since);
        
        // Ensure all defined JSE sectors exist in the result, even if they have 0 headlines
        List<String> requiredSectors = Arrays.asList(
            "Mining", "Financials", "Retail", "Technology & Telecoms", "Energy & Industrials", "General Business"
        );
        
        List<Map<String, Object>> finalStats = new ArrayList<>();
        for (String sector : requiredSectors) {
            Map<String, Object> sectorStat = dbStats.stream()
                .filter(m -> sector.equalsIgnoreCase((String) m.get("sector")))
                .findFirst()
                .map(HashMap::new) // mutable copy
                .orElseGet(() -> {
                    HashMap<String, Object> emptyMap = new HashMap<>();
                    emptyMap.put("sector", sector);
                    emptyMap.put("totalCount", 0L);
                    emptyMap.put("positiveCount", 0L);
                    emptyMap.put("negativeCount", 0L);
                    emptyMap.put("neutralCount", 0L);
                    emptyMap.put("averageScore", 0.0);
                    return emptyMap;
                });
            
            // Convert null averageScore (average of empty set) to 0.0
            if (sectorStat.get("averageScore") == null) {
                sectorStat.put("averageScore", 0.0);
            }
            finalStats.add(sectorStat);
        }
        
        return finalStats;
    }

    // 2. Get Headlines List (rolling 72 hours)
    @GetMapping("/headlines")
    public List<NewsArticle> getHeadlines() {
        return repository.findAllByOrderByPublishedDateDesc();
    }

    // 3. Get NLP Service and Pipeline Status
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("nlpState", nlpAnalyzer.getState().toString());
        status.put("initTimeMs", nlpAnalyzer.getInitTimeMs());
        status.put("failureReason", nlpAnalyzer.getInitFailureReason());
        status.put("totalRequests", sentimentService.getTotalRequests());
        status.put("fallbackRequests", sentimentService.getFallbackRequests());
        status.put("averageLatencyMs", sentimentService.getAverageLatencyMs());
        status.put("pipelineLogs", newsPipeline.getLogs());
        return status;
    }

    // 4. Custom Sentiment Analysis Workbench (Side-by-side comparison)
    @PostMapping("/analyze")
    public Map<String, Object> analyzeText(@RequestBody Map<String, String> payload) {
        String text = payload.get("text");
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text content is required.");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("text", text);

        // CoreNLP or Fallback execution
        long startNlp = System.currentTimeMillis();
        SentimentService.AnalysisResult nlpResult = sentimentService.analyze(text);
        long nlpTime = System.currentTimeMillis() - startNlp;

        Map<String, Object> nlpData = new HashMap<>();
        nlpData.put("sentiment", nlpResult.getCategory().toString());
        nlpData.put("score", nlpResult.getScore());
        nlpData.put("engineUsed", nlpResult.getEngine().toString());
        nlpData.put("latencyMs", nlpTime);
        response.put("serviceResult", nlpData);

        // Pure Regex Execution
        long startRegex = System.currentTimeMillis();
        RegexSentimentAnalyzer.SentimentResult regexResult = regexAnalyzer.analyze(text);
        long regexTime = System.currentTimeMillis() - startRegex;

        Map<String, Object> regexData = new HashMap<>();
        regexData.put("sentiment", regexResult.getCategory().toString());
        regexData.put("score", regexResult.getScore());
        regexData.put("engineUsed", AnalyzerEngine.REGEX_FALLBACK.toString());
        regexData.put("latencyMs", regexTime);
        response.put("regexResult", regexData);

        return response;
    }

    // 5. Toggle Stanford CoreNLP Engine (Enabled / Disabled)
    @PostMapping("/toggle")
    public Map<String, Object> toggleNlp(@RequestBody Map<String, Boolean> payload) {
        Boolean enabled = payload.get("enabled");
        if (enabled == null) {
            throw new IllegalArgumentException("Enabled field is required.");
        }

        sentimentService.toggleCoreNlp(enabled);
        
        // Log log message in pipeline
        newsPipeline.logToStream(String.format("CoreNLP status changed. Enabled: %b (State: %s)", enabled, nlpAnalyzer.getState()));
        
        // Broadcast change
        sseBroadcaster.broadcast("status-change", Map.of(
            "nlpState", nlpAnalyzer.getState().toString(),
            "message", "CoreNLP configuration toggled."
        ));

        return Map.of("success", true, "nlpState", nlpAnalyzer.getState().toString());
    }

    // 6. Manually Trigger Scraper Ingestion Pipeline
    @PostMapping("/ingest")
    public Map<String, Object> runIngest() {
        // Run asynchronously in a background thread to prevent client timeouts
        Thread pipelineThread = new Thread(newsPipeline::execute);
        pipelineThread.setName("Manual-Pipeline-Thread");
        pipelineThread.start();
        
        return Map.of("status", "triggered", "message", "Pipeline execution started in background.");
    }

    // 7. Seed initial mock JSE investor data
    @PostMapping("/seed")
    public Map<String, Object> seedData() {
        logger.info("Manual seed requested. Purging database and generating seed JSE headlines...");
        repository.deleteAll();
        
        List<NewsArticle> seeds = ingestionStage.getSeedData();
        int savedCount = 0;
        
        for (NewsArticle seed : seeds) {
            try {
                // Run cleaning
                String cleaned = seed.getTitle() + ". " + seed.getDescription();
                cleaned = cleaned.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
                seed.setCleanText(cleaned);
                
                // Classify sector is already populated in seed, but if not we run it
                if (seed.getSector() == null) {
                    seed.setSector("General Business");
                }
                
                // Sentiment analysis
                SentimentService.AnalysisResult result = sentimentService.analyze(seed.getCleanText());
                seed.setSentiment(result.getCategory());
                seed.setSentimentScore(result.getScore());
                seed.setAnalyzerEngine(result.getEngine());
                seed.setProcessingTimeMs(result.getProcessingTimeMs());
                
                repository.save(seed);
                savedCount++;
            } catch (Exception e) {
                logger.error("Failed to seed article '{}': {}", seed.getTitle(), e.getMessage());
            }
        }
        
        newsPipeline.logToStream(String.format("Database seeded with %d default JSE articles.", savedCount));
        
        // Broadcast update
        sseBroadcaster.broadcast("news-update", Map.of(
            "timestamp", LocalDateTime.now().toString(),
            "newArticlesCount", savedCount,
            "message", "Database was reset and seeded with default JSE headlines."
        ));
        
        return Map.of("success", true, "count", savedCount);
    }

    // 8. Server-Sent Events (SSE) stream for real-time dashboard updates
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents() {
        // Create emitter with 30 minute timeout
        SseEmitter emitter = new SseEmitter(1800000L);
        sseBroadcaster.addEmitter(emitter);
        
        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("message", "SSE stream established. Ready for live updates.")));
        } catch (Exception e) {
            logger.error("Failed to send connection message to SSE emitter: {}", e.getMessage());
        }
        
        return emitter;
    }
}
