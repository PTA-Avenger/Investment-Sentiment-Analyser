package com.jse.sentiment.pipeline.stages;

import com.jse.sentiment.model.NewsArticle;
import com.jse.sentiment.pipeline.PipelineStage;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class CleaningStage implements PipelineStage<List<NewsArticle>, List<NewsArticle>> {

    private static final Pattern HTML_TAGS = Pattern.compile("<[^>]*>");

    @Override
    public List<NewsArticle> process(List<NewsArticle> articles) throws Exception {
        if (articles == null) return List.of();

        for (NewsArticle article : articles) {
            String title = article.getTitle() != null ? article.getTitle() : "";
            String desc = article.getDescription() != null ? article.getDescription() : "";

            // 1. Combine title and description for deep NLP analysis
            String combined = title + ". " + desc;

            // 2. Remove HTML tags
            String cleaned = HTML_TAGS.matcher(combined).replaceAll("");

            // 3. Resolve HTML entities
            cleaned = cleaned
                    .replace("&amp;", "&")
                    .replace("&quot;", "\"")
                    .replace("&apos;", "'")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&nbsp;", " ")
                    .replace("&#8217;", "'")
                    .replace("&#8220;", "\"")
                    .replace("&#8221;", "\"")
                    .replace("&#8211;", "-");

            // 4. Normalize multiple whitespaces and trim
            cleaned = cleaned.replaceAll("\\s+", " ").trim();

            article.setCleanText(cleaned);
        }

        return articles;
    }
}
