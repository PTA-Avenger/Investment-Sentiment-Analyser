package com.jse.sentiment.pipeline.stages;

import com.jse.sentiment.model.NewsArticle;
import com.jse.sentiment.pipeline.PipelineStage;
import com.jse.sentiment.service.SentimentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class SentimentAnalysisStage implements PipelineStage<List<NewsArticle>, List<NewsArticle>> {

    private static final Logger logger = LoggerFactory.getLogger(SentimentAnalysisStage.class);
    private final SentimentService sentimentService;

    public SentimentAnalysisStage(SentimentService sentimentService) {
        this.sentimentService = sentimentService;
    }

    @Override
    public List<NewsArticle> process(List<NewsArticle> articles) throws Exception {
        if (articles == null) return List.of();

        logger.info("Executing sentiment analysis on {} articles...", articles.size());
        for (NewsArticle article : articles) {
            // Check if sentiment is already populated (e.g. mock seed data)
            if (article.getSentiment() != null) {
                continue;
            }

            try {
                SentimentService.AnalysisResult result = sentimentService.analyze(article.getCleanText());
                
                article.setSentiment(result.getCategory());
                article.setSentimentScore(result.getScore());
                article.setAnalyzerEngine(result.getEngine());
                article.setProcessingTimeMs(result.getProcessingTimeMs());
                
            } catch (Exception e) {
                logger.error("Failed to analyze sentiment for article '{}': {}", article.getTitle(), e.getMessage());
                // Fallback directly to neutral Regex analyzer here in code block if service crashes
                article.setSentiment(com.jse.sentiment.model.SentimentCategory.NEUTRAL);
                article.setSentimentScore(0.0);
                article.setAnalyzerEngine(com.jse.sentiment.model.AnalyzerEngine.REGEX_FALLBACK);
                article.setProcessingTimeMs(0L);
            }
        }

        return articles;
    }
}
