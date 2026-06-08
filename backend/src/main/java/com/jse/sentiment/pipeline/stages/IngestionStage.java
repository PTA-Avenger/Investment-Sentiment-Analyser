package com.jse.sentiment.pipeline.stages;

import com.jse.sentiment.model.NewsArticle;
import com.jse.sentiment.pipeline.PipelineStage;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Component
public class IngestionStage implements PipelineStage<Void, List<NewsArticle>> {

    private static final Logger logger = LoggerFactory.getLogger(IngestionStage.class);

    private final List<String> feedUrls = Arrays.asList(
            "https://www.moneyweb.co.za/feed",
            "https://businesstech.co.za/news/feed/"
    );

    @Override
    public List<NewsArticle> process(Void input) throws Exception {
        List<NewsArticle> articles = new ArrayList<>();
        
        for (String urlStr : feedUrls) {
            try {
                logger.info("Scraping RSS feed: {}", urlStr);
                URL feedUrl = URI.create(urlStr).toURL();
                SyndFeedInput feedInput = new SyndFeedInput();
                SyndFeed feed = feedInput.build(new XmlReader(feedUrl));

                String sourceName = feed.getTitle() != null ? feed.getTitle() : "JSE News Source";

                for (SyndEntry entry : feed.getEntries()) {
                    NewsArticle article = new NewsArticle();
                    article.setTitle(entry.getTitle());
                    
                    String desc = "";
                    if (entry.getDescription() != null) {
                        desc = entry.getDescription().getValue();
                    } else if (entry.getContents() != null && !entry.getContents().isEmpty()) {
                        desc = entry.getContents().get(0).getValue();
                    }
                    article.setDescription(desc);
                    article.setUrl(entry.getLink());
                    article.setSource(sourceName);

                    Date pubDate = entry.getPublishedDate();
                    if (pubDate != null) {
                        article.setPublishedDate(LocalDateTime.ofInstant(pubDate.toInstant(), ZoneId.systemDefault()));
                    } else {
                        article.setPublishedDate(LocalDateTime.now());
                    }

                    articles.add(article);
                }
            } catch (Exception e) {
                logger.error("Failed to scrape RSS feed {}: {}. This feed is currently unavailable.", urlStr, e.getMessage());
                // We do not rethrow, to let other feeds work or fallback to seed data
            }
        }

        return articles;
    }

    public List<NewsArticle> getSeedData() {
        List<NewsArticle> seedList = new ArrayList<>();
        
        // Define realistic JSE investor headlines with descriptions and publish dates
        Object[][] rawSeed = {
            {"Gold Fields reports 15% increase in quarterly gold production, shares rally", 
             "Gold Fields Group delivered a stellar performance in Q2, with gold production up 15% year-on-year. The group maintained its full-year guidance and announced an increased interim dividend payout.",
             "Mining", "Moneyweb", 2},
            
            {"Standard Bank Group delivers record earnings as interest rates remain high", 
             "Standard Bank has reported a 22% surge in headline earnings. High interest rates in South Africa and strong growth in its corporate investment banking division drove net interest income.",
             "Financials", "Moneyweb", 4},
            
            {"Shoprite earnings growth slows down due to power supply disruptions and load shedding costs", 
             "Africa's largest retailer, Shoprite, reported that its operating margin squeezed due to spending over R500 million on diesel to run generators during extensive power cuts.",
             "Retail", "BusinessTech", 6},
            
            {"Anglo American Platinum halts dividend payout amidst falling metal prices and operational restructuring", 
             "Amplats announced it will suspend its interim dividend to preserve cash. The PGMs producer is facing severe margin compression as platinum and palladium prices continue to slump.",
             "Mining", "Moneyweb", 8},
            
            {"MTN Group announces expansion of 5G network across South Africa, investing R10bn", 
             "Telecommunications giant MTN has committed R10 billion in capital expenditure to enhance network coverage, roll out 5G, and secure backup power systems across South African towers.",
             "Technology & Telecoms", "BusinessTech", 10},
            
            {"Pick n Pay announces recapitalization plan and stores restructuring after significant loss", 
             "Pick n Pay is planning a rights raise of R4 billion and the listing of its Boxer business to address its mounting debt burden and clean up underperforming corporate supermarkets.",
             "Retail", "BusinessTech", 12},
            
            {"Absa Group reports stable performance, flags rising impairment charges", 
             "Absa reported a 4% growth in operating income but cautioned that retail customers are struggling to service loans, leading to a rise in credit impairment charges.",
             "Financials", "Moneyweb", 14},
            
            {"Sibanye-Stillwater warns of restructuring at its platinum operations, placing 3,000 jobs at risk", 
             "Sibanye-Stillwater has entered Section 189 consultations. The diversified miner cited unprofitable shafts driven by elevated production costs and weak metal markets.",
             "Mining", "Moneyweb", 16},
            
            {"Woolworths retail sales rise 5.4% but food division outpaces fashion and home", 
             "Woolworths Holdings released its sales update showing strong food sales growth of 8.4%, while its clothing and home business grew at a more modest rate of 2.1%.",
             "Retail", "BusinessTech", 18},
            
            {"Capitec Bank surpasses 22 million active clients, earnings beat market expectations", 
             "Capitec reported a 17% increase in headline earnings, driven by massive client acquisition and higher transactional volumes via its digital banking app.",
             "Financials", "Moneyweb", 20},
             
            {"Naspers shares jump 4% as Tencent reports strong gaming revenue recovery", 
             "Shares in Naspers and its European subsidiary Prosus rose in Johannesburg today, mirroring a surge in Tencent Holdings after the Chinese tech company beat quarterly projections.",
             "Technology & Telecoms", "Moneyweb", 22},
             
            {"Sasol reports net loss due to severe asset impairments at Secunda operations", 
             "Chemicals and energy giant Sasol booked a massive impairment charge on its Secunda liquid fuels refinery, citing high coal feed costs and carbon tax projections.",
             "Energy & Industrials", "Moneyweb", 24}
        };

        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < rawSeed.length; i++) {
            Object[] data = rawSeed[i];
            NewsArticle article = new NewsArticle();
            article.setTitle((String) data[0]);
            article.setDescription((String) data[1]);
            article.setSector((String) data[2]);
            article.setSource((String) data[3]);
            article.setUrl("https://mock-jse-news.co.za/articles/" + i);
            // Stagger publish dates back in hours
            article.setPublishedDate(now.minusHours((int) data[4]));
            seedList.add(article);
        }

        return seedList;
    }
}
