# Inverted Indexing Implementation

## What is Inverted Indexing?

Inverted indexing is a data structure used in search engines to map words to the documents or Urls in which they appear

Instead of storing data like:
docs → words

we reverse the relationship to:
word → list of docs

### Example:

suppose we have:

* doc1: "java concurrency thread"
* doc2: "java thread pool"

The inverted index becomes:

* java → [doc1, doc2]
* thread → [doc1, doc2]
* concurrency → [doc1]
* pool → [doc2]

this allows fast lookup of documents based on search queries, since we can search for docs which contains a specific word

---

## Why to Chose Inverted Indexing

after building the crawler and extracting clean text, the next challenge was:

how do we search efficiently across many docs?

A brute force approach would be:
* Scan every document for every search query (extremely slow and inefficient)

Inverted indexing solves this by:

* Preprocessing data once
* Enabling fast lookup during search

### Advantages:

* fast search (no full scan needed)
* supports ranking (based on frequency)
* scales well as data grows
---

## What the current code does (till this commit)

### 1. Loads Clean Data from Database

* reads url and clean_text from the database
* only processes pages that have extracted text

---

### 2. Text Preprocessing

for each doc:

* converts text to lowercase
* removes special characters
* splits text into tokens (words)

---

### 3. Stop Word Removal

Common words like:
`the, is, and, of, to...`

are removed because they don’t add meaningful search value.
used NLTK's list of stop words for this. 
---

### 4. Builds the Inverted Index

Data structure needed/used:

```java
Map<String, Map<String, Integer>>
```

breakdown:

* word → (URL → freq count)

Example:

```
java → {url1: 3, url2: 1}
```

This helps in ranking results later. (we are gonna use this algo later, either tf-idf or bm25)

---

### 5. Indexing Logic

For each word:

* Ignore short/irrelevant words
* Add/update frequency count for that URL

---

### 6. Search Functionality (basic for testing)

Current implementation supports:

* Searching a word or query (does not support multi-word)
* Returning matching URLs
* Ranking based on frequency (higher count = more relevant)

but there is an issue with high freq = relevant url.
Which is if a doc conntains word "java" 5000 times it will be more relevant in anybody searches
Java in our engine. which certainly is not correct.

---

## Current Scenario

* Builds index from real crawled data
* Removes stop words
* Supports basic ranked search

---

## Limitations (Current Version)

* In-memory index (not persisted yet, if pages gonna reach around 10k my cpu and RAM will melt)
* Basic ranking (only frequency-based, highly inefficient)
* No phrase search or advanced queries (single word search)

---

## Future Improvements

* TF-IDF or BM-25 ranking for better relevance
* Store index in database for scalability
* Support multi-word and phrase queries
* Build search API or UI

---

## Summary

This step transforms the project from:

Crawler → Data Collector

into:

Crawler → Indexer → Search Engine

This is a key milestone towards building a complete search system. Still basic but we Progress soon.
