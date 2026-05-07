package afg.achat.afgApprovAchat.configuration;

import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.core.WhitespaceTokenizerFactory;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilterFactory;
import org.apache.lucene.analysis.snowball.SnowballPorterFilterFactory;
import org.apache.lucene.analysis.standard.StandardTokenizerFactory;
import org.hibernate.search.backend.lucene.analysis.LuceneAnalysisConfigurationContext;
import org.hibernate.search.backend.lucene.analysis.LuceneAnalysisConfigurer;
import org.springframework.stereotype.Component;

@Component("luceneAnalysisConfigurer")
public class LuceneAnalysisConfig implements LuceneAnalysisConfigurer {

    @Override
    public void configure(LuceneAnalysisConfigurationContext context) {

        // Analyzer "french" — gère les accents, la casse, et les racines françaises
        context.analyzer("french").custom()
                .tokenizer(StandardTokenizerFactory.class)
                .tokenFilter(LowerCaseFilterFactory.class)        // minuscules
                .tokenFilter(ASCIIFoldingFilterFactory.class)     // é→e, à→a, etc.
                .tokenFilter(SnowballPorterFilterFactory.class)   // racine des mots
                .param("language", "French");

        // Normalizer "lowercase" — pour les KeywordField (pas de tokenisation)
        context.normalizer("lowercase").custom()
                .tokenFilter(LowerCaseFilterFactory.class)
                .tokenFilter(ASCIIFoldingFilterFactory.class);    // accents aussi
    }
}