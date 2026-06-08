package com.jse.sentiment.service;

import com.jse.sentiment.model.AnalyzerEngine;
import com.jse.sentiment.model.SentimentCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SentimentService {

    private static final Logger logger = LoggerFactory.getLogger(SentimentService.class);

    private final CoreNlpSentimentAnalyzer nlpAnalyzer;
    private final RegexSentimentAnalyzer regexAnalyzer;
    private final ExecutorService nlpExecutor;

    @Value("${nlp.corenlp.timeout-ms:1500}")
    private long timeoutMs;

    // Operational Metrics
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong fallbackRequests = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);

    public SentimentService(CoreNlpSentimentAnalyzer nlpAnalyzer, RegexSentimentAnalyzer regexAnalyzer) {
        this.nlpAnalyzer = nlpAnalyzer;
        this.regexAnalyzer = regexAnalyzer;
        
        // Custom thread pool for Stanford CoreNLP operations
        this.nlpExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread thread = new Thread(r);
            thread.setName("CoreNLP-Worker-Thread");
            thread.setDaemon(true);
            return thread;
        });
    }

    public AnalysisResult analyze(String text) {
        long startTime = System.currentTimeMillis();
        totalRequests.incrementAndGet();

        // 1. Try Stanford CoreNLP if fully initialized and ready
        if (nlpAnalyzer.getState() == CoreNlpSentimentAnalyzer.NlpState.READY) {
            Future<CoreNlpSentimentAnalyzer.CoreNlpResult> future = nlpExecutor.submit(() -> nlpAnalyzer.analyze(text));
            try {
                CoreNlpSentimentAnalyzer.CoreNlpResult nlpResult = future.get(timeoutMs, TimeUnit.MILLISECONDS);
                long elapsed = System.currentTimeMillis() - startTime;
                totalProcessingTimeMs.addAndGet(elapsed);
                
                return new AnalysisResult(
                    nlpResult.getCategory(),
                    nlpResult.getScore(),
                    AnalyzerEngine.CORENLP,
                    elapsed
                );
            } catch (TimeoutException e) {
                fallbackRequests.incrementAndGet();
                future.cancel(true); // Cancel task, interrupting the worker thread if running
                logger.warn("CoreNLP sentiment analysis timed out (limit: {}ms) for text. Falling back to Regex.", timeoutMs);
            } catch (InterruptedException e) {
                fallbackRequests.incrementAndGet();
                Thread.currentThread().interrupt();
                logger.warn("CoreNLP sentiment analysis interrupted. Falling back to Regex.");
            } catch (ExecutionException e) {
                fallbackRequests.incrementAndGet();
                logger.warn("CoreNLP sentiment analysis failed during execution. Falling back to Regex. Error: {}", e.getCause().getMessage());
            }
        } else {
            // Not ready (still LOADING, disabled, or FAILED to load model)
            fallbackRequests.incrementAndGet();
        }

        // 2. Graceful Degradation: Fallback to Heuristic Regex
        long regexStartTime = System.currentTimeMillis();
        RegexSentimentAnalyzer.SentimentResult regexResult = regexAnalyzer.analyze(text);
        long elapsed = System.currentTimeMillis() - regexStartTime;
        totalProcessingTimeMs.addAndGet(elapsed);
        
        return new AnalysisResult(
            regexResult.getCategory(),
            regexResult.getScore(),
            AnalyzerEngine.REGEX_FALLBACK,
            elapsed
        );
    }

    public void toggleCoreNlp(boolean enabled) {
        if (enabled) {
            if (nlpAnalyzer.getState() == CoreNlpSentimentAnalyzer.NlpState.DISABLED) {
                nlpAnalyzer.init(); // Boot it up
            }
        } else {
            nlpAnalyzer.setState(CoreNlpSentimentAnalyzer.NlpState.DISABLED);
            logger.info("Stanford CoreNLP was manually disabled via SentimentService.");
        }
    }

    public long getTotalRequests() {
        return totalRequests.get();
    }

    public long getFallbackRequests() {
        return fallbackRequests.get();
    }

    public double getAverageLatencyMs() {
        long reqs = totalRequests.get();
        return reqs == 0 ? 0.0 : (double) totalProcessingTimeMs.get() / reqs;
    }

    public static class AnalysisResult {
        private final SentimentCategory category;
        private final double score;
        private final AnalyzerEngine engine;
        private final long processingTimeMs;

        public AnalysisResult(SentimentCategory category, double score, AnalyzerEngine engine, long processingTimeMs) {
            this.category = category;
            this.score = score;
            this.engine = engine;
            this.processingTimeMs = processingTimeMs;
        }

        public SentimentCategory getCategory() {
            return category;
        }

        public double getScore() {
            return score;
        }

        public AnalyzerEngine getEngine() {
            return engine;
        }

        public long getProcessingTimeMs() {
            return processingTimeMs;
        }
    }
}
