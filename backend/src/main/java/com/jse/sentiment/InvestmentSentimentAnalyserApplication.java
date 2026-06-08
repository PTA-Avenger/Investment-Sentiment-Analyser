package com.jse.sentiment;

import com.jse.sentiment.model.NewsArticle;
import com.jse.sentiment.pipeline.NewsPipeline;
import com.jse.sentiment.pipeline.stages.IngestionStage;
import com.jse.sentiment.repository.NewsArticleRepository;
import com.jse.sentiment.service.SentimentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.util.List;

@SpringBootApplication
@EnableScheduling
public class InvestmentSentimentAnalyserApplication {

    private static final Logger logger = LoggerFactory.getLogger(InvestmentSentimentAnalyserApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(InvestmentSentimentAnalyserApplication.class, args);
    }

    @Bean
    public CommandLineRunner startupSeeder(
            NewsArticleRepository repository, 
            IngestionStage ingestionStage,
            SentimentService sentimentService,
            NewsPipeline newsPipeline) {
        return args -> {
            logger.info("Application started. Checking database status...");
            long count = repository.count();
            
            if (count == 0) {
                logger.info("In-memory database is empty. Seeding JSE default data...");
                List<NewsArticle> seeds = ingestionStage.getSeedData();
                int seeded = 0;
                
                for (NewsArticle article : seeds) {
                    try {
                        // Clean
                        String combined = article.getTitle() + ". " + article.getDescription();
                        String cleaned = combined.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
                        article.setCleanText(cleaned);
                        
                        // Score
                        SentimentService.AnalysisResult result = sentimentService.analyze(article.getCleanText());
                        article.setSentiment(result.getCategory());
                        article.setSentimentScore(result.getScore());
                        article.setAnalyzerEngine(result.getEngine());
                        article.setProcessingTimeMs(result.getProcessingTimeMs());
                        
                        repository.save(article);
                        seeded++;
                    } catch (Exception e) {
                        logger.error("Failed to seed article '{}': {}", article.getTitle(), e.getMessage());
                    }
                }
                newsPipeline.logToStream(String.format("Auto-seeded database on startup with %d JSE articles.", seeded));
                logger.info("Successfully seeded database with {} articles.", seeded);
            } else {
                logger.info("Database already contains {} articles. Skipping seeder.", count);
            }
            
            // Trigger first news scraping run in the background after startup
            Thread initialScrapeThread = new Thread(() -> {
                try {
                    // Stagger slightly to allow CoreNLP background initialization
                    Thread.sleep(5000);
                    logger.info("Executing initial news scrape pipeline...");
                    newsPipeline.execute();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            initialScrapeThread.setName("Startup-Scrape-Thread");
            initialScrapeThread.start();
        };
    }
}

