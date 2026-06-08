package com.jse.sentiment.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "news_articles")
public class NewsArticle {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000)
    private String title;

    @Column(length = 4000)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String cleanText;

    @Column(length = 2048)
    private String url;

    @Column(length = 100)
    private String source;

    @Column(length = 100)
    private String sector;

    @Enumerated(EnumType.STRING)
    private SentimentCategory sentiment;

    private Double sentimentScore; // -1.0 (Negative), 0.0 (Neutral), 1.0 (Positive)

    @Enumerated(EnumType.STRING)
    private AnalyzerEngine analyzerEngine;

    private Long processingTimeMs;

    private LocalDateTime publishedDate;

    private LocalDateTime processedAt;

    public NewsArticle() {
        this.processedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCleanText() {
        return cleanText;
    }

    public void setCleanText(String cleanText) {
        this.cleanText = cleanText;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public SentimentCategory getSentiment() {
        return sentiment;
    }

    public void setSentiment(SentimentCategory sentiment) {
        this.sentiment = sentiment;
    }

    public Double getSentimentScore() {
        return sentimentScore;
    }

    public void setSentimentScore(Double sentimentScore) {
        this.sentimentScore = sentimentScore;
    }

    public AnalyzerEngine getAnalyzerEngine() {
        return analyzerEngine;
    }

    public void setAnalyzerEngine(AnalyzerEngine analyzerEngine) {
        this.analyzerEngine = analyzerEngine;
    }

    public Long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(Long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public LocalDateTime getPublishedDate() {
        return publishedDate;
    }

    public void setPublishedDate(LocalDateTime publishedDate) {
        this.publishedDate = publishedDate;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
}
