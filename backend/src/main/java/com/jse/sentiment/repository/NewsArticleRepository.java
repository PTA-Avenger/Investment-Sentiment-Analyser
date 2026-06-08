package com.jse.sentiment.repository;

import com.jse.sentiment.model.NewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {
    
    List<NewsArticle> findByPublishedDateAfterOrderByPublishedDateDesc(LocalDateTime date);
    
    List<NewsArticle> findAllByOrderByPublishedDateDesc();
    
    boolean existsByTitle(String title);
    
    void deleteByPublishedDateBefore(LocalDateTime date);

    @Query("SELECT n.sector as sector, " +
           "COUNT(n) as totalCount, " +
           "SUM(CASE WHEN n.sentiment = 'POSITIVE' THEN 1 ELSE 0 END) as positiveCount, " +
           "SUM(CASE WHEN n.sentiment = 'NEGATIVE' THEN 1 ELSE 0 END) as negativeCount, " +
           "SUM(CASE WHEN n.sentiment = 'NEUTRAL' THEN 1 ELSE 0 END) as neutralCount, " +
           "AVG(n.sentimentScore) as averageScore " +
           "FROM NewsArticle n " +
           "WHERE n.publishedDate > :since " +
           "GROUP BY n.sector")
    List<Map<String, Object>> getSectorStats(@Param("since") LocalDateTime since);
}
