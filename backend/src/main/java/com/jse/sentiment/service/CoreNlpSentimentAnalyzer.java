package com.jse.sentiment.service;

import com.jse.sentiment.model.SentimentCategory;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Properties;

@Service
public class CoreNlpSentimentAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(CoreNlpSentimentAnalyzer.class);

    public enum NlpState {
        DISABLED,
        LOADING,
        READY,
        FAILED
    }

    private StanfordCoreNLP pipeline;
    private volatile NlpState state = NlpState.LOADING;
    private String initFailureReason = "";
    private long initTimeMs = 0;

    @Value("${nlp.corenlp.enabled:true}")
    private boolean enabled;

    @PostConstruct
    public void init() {
        if (!enabled) {
            state = NlpState.DISABLED;
            logger.info("Stanford CoreNLP is disabled by configuration.");
            return;
        }

        logger.info("Starting Stanford CoreNLP initialization in background thread...");
        Thread initThread = new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                Properties props = new Properties();
                // We load the minimal pipeline needed for sentiment analysis
                props.setProperty("annotators", "tokenize, ssplit, parse, sentiment");
                
                // StanfordCoreNLP load models from classpath (which includes the large models JAR)
                pipeline = new StanfordCoreNLP(props);
                
                initTimeMs = System.currentTimeMillis() - startTime;
                state = NlpState.READY;
                logger.info("Stanford CoreNLP initialized successfully in {} ms.", initTimeMs);
            } catch (Throwable e) {
                state = NlpState.FAILED;
                initFailureReason = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
                logger.error("Failed to initialize Stanford CoreNLP. Falling back to Regex. Error details: ", e);
            }
        });
        initThread.setName("CoreNLP-Init-Thread");
        initThread.setDaemon(true);
        initThread.start();
    }

    public NlpState getState() {
        return state;
    }

    public void setState(NlpState state) {
        this.state = state;
    }

    public String getInitFailureReason() {
        return initFailureReason;
    }

    public long getInitTimeMs() {
        return initTimeMs;
    }

    public CoreNlpResult analyze(String text) {
        if (state != NlpState.READY || pipeline == null) {
            throw new IllegalStateException("CoreNLP analyzer is not ready. Current state: " + state);
        }

        Annotation annotation = new Annotation(text);
        pipeline.annotate(annotation);

        double totalScore = 0;
        int sentenceCount = 0;

        for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
            String sentimentStr = sentence.get(SentimentCoreAnnotations.SentimentClass.class);
            double score = convertSentimentToScore(sentimentStr);
            totalScore += score;
            sentenceCount++;
        }

        double averageScore = sentenceCount == 0 ? 0.0 : totalScore / sentenceCount;
        SentimentCategory category;
        if (averageScore > 0.15) {
            category = SentimentCategory.POSITIVE;
        } else if (averageScore < -0.15) {
            category = SentimentCategory.NEGATIVE;
        } else {
            category = SentimentCategory.NEUTRAL;
        }

        return new CoreNlpResult(category, averageScore);
    }

    private double convertSentimentToScore(String sentimentStr) {
        if (sentimentStr == null) return 0.0;
        switch (sentimentStr.toLowerCase()) {
            case "very positive": return 1.0;
            case "positive": return 0.5;
            case "neutral": return 0.0;
            case "negative": return -0.5;
            case "very negative": return -1.0;
            default: return 0.0;
        }
    }

    public static class CoreNlpResult {
        private final SentimentCategory category;
        private final double score;

        public CoreNlpResult(SentimentCategory category, double score) {
            this.category = category;
            this.score = score;
        }

        public SentimentCategory getCategory() {
            return category;
        }

        public double getScore() {
            return score;
        }
    }
}
