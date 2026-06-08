package com.jse.sentiment.pipeline.stages;

import com.jse.sentiment.model.NewsArticle;
import com.jse.sentiment.pipeline.PipelineStage;
import com.jse.sentiment.repository.NewsArticleRepository;
import com.jse.sentiment.service.SseBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class StorageAndBroadcastStage implements PipelineStage<List<NewsArticle>, List<NewsArticle>> {

    private static final Logger logger = LoggerFactory.getLogger(StorageAndBroadcastStage.class);

    private final NewsArticleRepository repository;
    private final SseBroadcaster sseBroadcaster;

    @Value("${news.db.prune-hours:72}")
    private int pruneHours;

    public StorageAndBroadcastStage(NewsArticleRepository repository, SseBroadcaster sseBroadcaster) {
        this.repository = repository;
        this.sseBroadcaster = sseBroadcaster;
    }

    @Override
    @Transactional
    public List<NewsArticle> process(List<NewsArticle> articles) throws Exception {
        if (articles == null || articles.isEmpty()) {
            return List.of();
        }

        List<NewsArticle> savedArticles = new ArrayList<>();
        int duplicateCount = 0;

        for (NewsArticle article : articles) {
            // Check for duplicate in H2 db
            if (repository.existsByTitle(article.getTitle())) {
                duplicateCount++;
                continue;
            }
            
            // Save to database
            NewsArticle saved = repository.save(article);
            savedArticles.add(saved);
        }

        logger.info("Saved {} new articles. Skipped {} duplicates.", savedArticles.size(), duplicateCount);

        // Delete old articles (older than pruneHours) to optimize database footprint if configured
        if (pruneHours > 0) {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(pruneHours);
            repository.deleteByPublishedDateBefore(cutoff);
            logger.info("Pruned JSE news database records older than {} hours.", pruneHours);
        } else {
            logger.info("Database pruning is disabled (pruneHours <= 0). Keeping full history.");
        }

        // Broadcast to live listeners if any new articles were successfully ingested
        if (!savedArticles.isEmpty()) {
            Map<String, Object> eventData = Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "newArticlesCount", savedArticles.size(),
                "message", "Database updated with new JSE news sentiment data."
            );
            sseBroadcaster.broadcast("news-update", eventData);
        }

        return savedArticles;
    }
}
