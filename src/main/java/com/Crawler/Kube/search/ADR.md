# Architecture Decision Records (ADR)

This document tracks the major architectural decisions, trade-offs, and design patterns implemented in the Search Engine pipeline.

## ADR 1: Text Normalization & Dependency Management

**Context:** To improve the search engine's recall, document text and user queries needed to be normalized. Variations of words (e.g., "vulnerability", "vulnerabilities", "vulnerable") were being treated as distinct entities, fragmenting the indexing logic and reducing search accuracy.

**Decision:** Integrated **Apache Lucene (`lucene-analysis-common`)** specifically to utilize its maintained `EnglishStemmer` (Porter2 algorithm). However, the system explicitly avoids using Lucene's full `EnglishAnalyzer` pipeline, opting instead to retain a custom tokenization and NLTK-based stop-word filtering pipeline.

**Rationale:**
1. **Stemming over Lemmatization:** Lemmatization requires heavy NLP frameworks (like Stanford CoreNLP) that consume gigabytes of RAM. Algorithmic stemming provides the necessary search recall improvements with microsecond execution and virtually zero memory overhead.
2. **Memory & Pipeline Control:** By writing a custom tokenization and filtering pipeline rather than relying on Lucene's `TokenStream` API, the system maintains strict control over its memory footprint and keeps the indexing architecture highly readable and decoupled from third-party black boxes.

---

## ADR 2: Query Processing & System Decoupling

**Context:** The original `SearchIndex` class was functioning as a monolith, handling both the write-heavy background indexing and the read-heavy real-time query processing. Additionally, the engine needed to support multi-word queries (e.g., "java security") rather than single-word dictionary lookups.

**Decision:** 1. **Separation of Concerns:** Split the monolith into two distinct modules: `SearchIndex` (handles database reading and NLP normalization) and `QueryEngine` (handles user input and ranking). They currently operate via Shared Memory (passing the Inverted Index map via constructor).
2. **Boolean Intersection:** Implemented a logical `AND` intersection for multi-word queries using Java's highly optimized `Set.retainAll()` method.
3. **Major addition:** Added multi-word search query processing. earlier we were working with single word search.

**Rationale:**
1. **Scalability:** Decoupling the Indexer from the Query Engine perfectly positions the application for its next phase: moving the Inverted Index to persistent SQLite storage and wrapping the `QueryEngine` in a Spring Boot REST API.
2. **Performance:** Using native hash-set intersections (`retainAll()`) allows the engine to instantly discard irrelevant documents during multi-word searches. By using the first query term as a baseline and progressively filtering, the time complexity of complex multi-word phrase matching is drastically reduced.