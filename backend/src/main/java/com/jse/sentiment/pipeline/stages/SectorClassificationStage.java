package com.jse.sentiment.pipeline.stages;

import com.jse.sentiment.model.NewsArticle;
import com.jse.sentiment.pipeline.PipelineStage;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class SectorClassificationStage implements PipelineStage<List<NewsArticle>, List<NewsArticle>> {

    // Sector compiled matching patterns (case-insensitive)
    private final Pattern miningPattern = Pattern.compile(
            "\\b(gold|platinum|mining|amplats|sibanye|anglo|bhp|glencore|kumba|impala|implats|fields?|coal|metal|miner|pgm|iron ore|exxaro|sasol mining|harmony gold|pan african)\\b", 
            Pattern.CASE_INSENSITIVE
    );

    private final Pattern financialsPattern = Pattern.compile(
            "\\b(banks?|banking|standard bank|nedbank|absa|capitec|firstrand|discovery|insurance|investment|lender|financial|sanlam|coronation|ninety one|old mutual|growthpoint|reit|shares?|rand|interest rates?|jse|ticker)\\b", 
            Pattern.CASE_INSENSITIVE
    );

    private final Pattern retailPattern = Pattern.compile(
            "\\b(shoprite|woolworths|pick n pay|retailer|retail|stores|supermarket|clothing|food|boxer|mr price|foschini|tfg|pepkor|clicks|dis-chem|truworths|spar|shopping|mall|consumers?)\\b", 
            Pattern.CASE_INSENSITIVE
    );

    private final Pattern techTelecomPattern = Pattern.compile(
            "\\b(mtn|vodacom|telkom|naspers|prosus|tech|software|telecom|network|cellular|5g|data|digital|it|tencent|e-commerce|cyber|telecommunications|cloud|altron|bytes)\\b", 
            Pattern.CASE_INSENSITIVE
    );

    private final Pattern energyIndustrialsPattern = Pattern.compile(
            "\\b(sasol|eskom|energy|industrial|power|fuel|chemical|factory|builder|construction|logistics|transnet|transport|bidvest|barloworld|ppc|petrol|oil|refinery|electricity|load shedding|loadshedding|aviation|safair|saa)\\b", 
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public List<NewsArticle> process(List<NewsArticle> articles) throws Exception {
        if (articles == null) return List.of();

        for (NewsArticle article : articles) {
            // Check if sector is already manually seeded (e.g. from seed data)
            if (article.getSector() != null && !article.getSector().isEmpty()) {
                continue;
            }

            String searchField = (article.getTitle() + " " + article.getDescription()).toLowerCase();
            String sector = classifySector(searchField);
            article.setSector(sector);
        }

        return articles;
    }

    private String classifySector(String text) {
        if (miningPattern.matcher(text).find()) {
            return "Mining";
        } else if (financialsPattern.matcher(text).find()) {
            return "Financials";
        } else if (retailPattern.matcher(text).find()) {
            return "Retail";
        } else if (techTelecomPattern.matcher(text).find()) {
            return "Technology & Telecoms";
        } else if (energyIndustrialsPattern.matcher(text).find()) {
            return "Energy & Industrials";
        } else {
            return "General Business";
        }
    }
}
