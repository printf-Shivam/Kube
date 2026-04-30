# CrawlerEngine – Detailed Technical Documentation (Old Version)

---

## Overview

This document explains the architecture, execution flow, and internal behavior of the Crawler.

The crawler is a **multi-threaded web crawler** that:

* Starts from seed URLs
* Organizes URLs per host
* Fetches pages (JSoup + browser fallback)
* Extracts links
* Stores results in a database

---

# 🧩 1. High-Level Architecture

```
Seed URLs
   ↓
URL Normalization (canonicalize)
   ↓
Visited Check
   ↓
Per-Host Queues (hostQueue)
   ↓
Dispatcher Thread (round-robin scan)
   ↓
Thread Pool (ExecutorService)
   ↓
processUrl()
   ↓
Fetcher (JSoup → Browser fallback)
   ↓
Parser (JSoup DOM)
   ↓
DB Write (synchronous)
   ↓
Link Extraction
   ↓
addURL()
```

---

# ⚙️ 2. Core Components

## 2.1 Frontier (URL Management)

### Data Structure:

```java
ConcurrentHashMap<String, BlockingQueue<String>> hostQueue
```

### Purpose:

* Groups URLs by host
* Enables per-host politeness
* Ensures structured crawling

---

## 2.2 Visited Set

```java
Set<String> visited = ConcurrentHashMap.newKeySet();
```

### Purpose:

* Prevent duplicate crawling
* In-memory tracking

---

## 2.3 Host State Tracking

```java
Set<String> activeHosts
ConcurrentHashMap<String, Long> hostLastAccess
ConcurrentHashMap<String, Integer> hostPageCount
```

### Responsibilities:

| Structure      | Purpose                           |
| -------------- | --------------------------------- |
| activeHosts    | Ensures only one request per host |
| hostLastAccess | Enforces delay between requests   |
| hostPageCount  | Limits pages per host             |

---

## 2.4 Thread Pool

```java
ExecutorService executor = Executors.newFixedThreadPool(20);
```

* Fixed 20 worker threads
* Executes `processUrl()`

---

## 2.5 Dispatcher Thread

A dedicated thread continuously schedules work.

---

# 3. Execution Flow (Step-by-Step)

---

##  Step 1: Initialization

### Constructor performs:

1. Initializes browser (Playwright)
2. Loads visited URLs from DB
3. Loads saved frontier (resume capability)
4. Adds seed URLs
5. Registers shutdown hook

---

##  Step 2: Start Crawling

```java
crawler.start();
```

### Inside `start()`:

1. Start dispatcher thread
2. Loop until crawl completes
3. Shutdown executor
4. Save frontier
5. Close DB + browser

---

##  Step 3: Dispatcher Loop

```java
for (String host : hostQueue.keySet())
```

### Logic:

For each host:

1. Check if host is NOT active
2. Check delay:

   ```java
   currentTime - lastAccess >= HOST_DELAY
   ```
3. Check crawl limit
4. Fetch URL from queue
5. Submit task to thread pool

---

### Important Behavior:

* **Round-robin over all hosts**
* **Busy polling**
* Sleeps if no work dispatched

---

## Step 4: URL Processing

```java
processUrl(host, url)
```

### Steps:

1. Extract path
2. Check robots.txt
3. Fetch page
4. Save to DB
5. Extract links
6. Add new URLs

---

## Step 5: Fetching Strategy

### 1. Primary: JSoup

```java
Jsoup.connect(url).get()
```

* Blocking HTTP request
* Parses HTML into DOM

---

### 2. Fallback: Browser (Playwright)

Triggered when:

* Page is too small
* JS required
* Bot protection detected

```java
fetchWithBrowser()
```

### Browser Flow:

1. Acquire semaphore (max 3 browsers)
2. Open new page
3. Navigate
4. Wait for load
5. Simulate interaction
6. Extract HTML
7. Close page

---

##  Step 6: Parsing & Extraction

```java
doc.select("a[href]")
```

* Extracts links using DOM traversal
* Adds them back to frontier

---

##  Step 7: Database Write

```java
dbManager.savePage(url, html)
```

* Happens inside worker thread
* Blocking operation

---

##  Step 8: URL Addition

```java
addURL(url)
```

### Steps:

1. Canonicalize URL
2. Check visited set
3. Extract host
4. Check host limits
5. Add to host queue

---

#  4. Execution Model

---

## Threading Model

| Component  | Threads          |
| ---------- | ---------------- |
| Dispatcher | 1                |
| Workers    | 20               |
| Browser    | max 3 concurrent |

---

## Concurrency Behavior

* One request per host at a time
* Global concurrency limited to 20 threads
* Browser heavily restricted

---

#  5. Control Mechanisms

---

## 5.1 Politeness Policy

```java
HOST_DELAY = 1000ms
```

* Minimum 1 second between requests per host

---

## 5.2 Per-Host Limit

```java
MAX_PAGES = 100
```

* Stops crawling a host after limit

---

## 5.3 Browser Limiter

```java
Semaphore browserLimiter = new Semaphore(3);
```

* Prevents excessive browser usage

---

#  6. Crawl Completion Logic

```java
isCrawlComplete()
```

Crawler stops when:

* No active hosts
* All host queues are empty

---

#  7. Fault Tolerance

---

## Shutdown Hook

On termination:

1. Saves frontier
2. Closes DB
3. Closes browser

---

## Resume Capability  (will discard this completely)

On restart:

* Loads previous frontier
* Loads visited URLs

---

# 8. Key Characteristics

---

## Limitations (Observed Behavior)

* Single-request-per-host restriction
* Blocking DB writes
* Blocking HTTP fetch
* Inefficient dispatcher loop
* Low concurrency (20 threads)
* Heavy browser overhead
* No batching
* No pipeline separation

---

# 9. End-to-End Flow Example

```
1. Seed URL added
2. Goes into hostQueue["example.com"]
3. Dispatcher picks host
4. Submits task to executor
5. Worker thread:
   → checks robots
   → fetches page
   → saves to DB
   → extracts links
6. Links added to respective host queues
7. Cycle repeats
```

---

This crawler follows a **monolithic execution model**, where:

* Fetching, parsing, and storage happen in the same thread
* Scheduling is centralized and scan-based
* Politeness is strictly enforced per host
* Concurrency is limited and conservative

---

