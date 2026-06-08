package com.jse.sentiment.service;

import com.jse.sentiment.model.SentimentCategory;
import org.springframework.stereotype.Service;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RegexSentimentAnalyzer {

    private final Pattern positivePattern;
    private final Pattern negativePattern;

    public RegexSentimentAnalyzer() {
        // Broad lists of financial sentiment keywords with word boundaries
        String posWords = "\\b(growth|profit|surged?|dividend|upbeat|bullish|gains?|rises?|strong|recovery|expansion|investments?|optimistic|upgrade|buy|outperformed|beats?|rallied|rally|success|soars?|recovers?)\\b";
        String negWords = "\\b(loss|decline|slumped|downbeat|bearish|plunges?|drops?|weak|contraction|debt|deficit|warns?|missed?|sell-off|downgrade|crisis|recession|falls?|collapse|risks?|investigation|probe|restructurings?|halved?|falls?)\\b";
        
        this.positivePattern = Pattern.compile(posWords, Pattern.CASE_INSENSITIVE);
        this.negativePattern = Pattern.compile(negWords, Pattern.CASE_INSENSITIVE);
    }

    public SentimentResult analyze(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new SentimentResult(SentimentCategory.NEUTRAL, 0.0);
        }

        int posCount = 0;
        int negCount = 0;

        Matcher posMatcher = positivePattern.matcher(text);
        while (posMatcher.find()) {
            posCount++;
        }

        Matcher negMatcher = negativePattern.matcher(text);
        while (negMatcher.find()) {
            negCount++;
        }

        double score;
        SentimentCategory category;

        if (posCount == 0 && negCount == 0) {
            score = 0.0;
            category = SentimentCategory.NEUTRAL;
        } else {
            score = (double) (posCount - negCount) / (posCount + negCount);
            // Dynamic classification boundaries
            if (score > 0.15) {
                category = SentimentCategory.POSITIVE;
            } else if (score < -0.15) {
                category = SentimentCategory.NEGATIVE;
            } else {
                category = SentimentCategory.NEUTRAL;
            }
        }

        return new SentimentResult(category, score);
    }

    public static class SentimentResult {
        private final SentimentCategory category;
        private final double score;

        public SentimentResult(SentimentCategory category, double score) {
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
