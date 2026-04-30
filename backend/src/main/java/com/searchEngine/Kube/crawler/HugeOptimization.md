# Crawler Optimization Report

### Issues in Old Implementation & Fixes in New Architecture

---

## Overview

This document summarizes:

*  Problems faced in the **old crawler**
How those issues were diagnosed
*  Solutions implemented in the **new crawler**
* earlier i made a wrong assumption that visited set is growing faster which was consuming the most of our RAM but my this assumption was wrong
  because the DOM object created by JSoup was taking the most of the memory even though function was ended the DOM object was not getting destroyed it was being accumulated in RAM and was filling up RAM.
* This documentation helps you understand how we managed that and what changes we made.
* We not only managed the memory but also we increase throughput significantly (3x).
* 

This reflects the **actual debugging journey and architectural evolution**.

---
## EveryThing Summarized

* We not only managed to increase throughput but reduce the RAM consumption by using Hashing based url uniqueness checking limiting ourself to how many urls we need.

* Secondly, our RAM was still getting filled, means our assumption about visited set was wrong, our main culprit was JSoup DOM it was not getting destroyed at all, it was getting accumulated and was eating up RAM.
* we handled it by using batch flush, we store to DB -> flush it.
* This freed up RAM a lot.
* coming to ThroughPut it was less, about 8-9 pages per second
* DB batch system actually fixed it, since earlier we were storing one page per transaction, now we have batch
* Our biggest culprit for throughput was JSoup connection we replaced with okhttp because it is faster than JSoup.
* by making all these changes we achieved max throughput of 32 pages per second on a 4G network.
* changed db from sqlite to postgre to test on real db
---
# 1. Initial Problem Statement

### Observed Behavior:

* Frontier growing fast (7000+ URLs in minutes)
* But crawling speed **very low**
* System felt **stuck / slow**

---

##  Key Realization

> The crawler was **not slow at discovering URLs**,
> it was slow at **processing (fetching + downstream pipeline)**

---

#  2. Major Bottlenecks in Old System

---

##  2.1 Fetcher Bottleneck (Primary Issue)

### Problem:

* Used **JSoup for HTTP fetching**
* Each request:

    * new TCP connection
    * blocking call
    * no connection reuse

### Impact:

```text
High latency per request → low throughput
```

### Fix:

* Switched to **OkHttp**
* Enabled:

    * connection pooling
    * faster HTTP handling

---

##  2.2 Low Concurrency

### Problem:

```java
MAX_THREAD = 20
```

### Impact:

* System is I/O bound, not CPU bound
* Threads were underutilized

### Fix:

```java
MAX_THREAD = 100 → 200
```

---

##  2.3 Single Request Per Host

### Problem:

```java
activeHosts.contains(host)
```

* Only 1 request per host at a time

### Combined with:

```java
HOST_DELAY = 1000ms
```

### Impact:

```text
1 host → max 1 req/sec
Few hosts → global throughput capped (~20–30 pps)
```

### Fix:

* Introduced **Semaphore per host**

```java
MAX_PER_HOST = 3 → 5
```

---

## 2.4 Strict Host Delay

### Problem:

```java
HOST_DELAY = 1000ms
```

### Impact:

* Artificial throttling
* Threads idle most of the time

### Fix:

```java
HOST_DELAY = 50ms → 20ms
```

---

##  2.5 Inefficient Dispatcher (O(N) Scan)

### Problem:

```java
for (host : hostQueue.keySet())
```

* Repeated scanning of all hosts

### Impact:

```text
O(N) scheduling → CPU waste + latency
```

### Fix:

* Replaced with **DelayQueue**

```text
Only ready hosts are processed
```

---

##  2.6 Synchronous DB Writes (Critical Bottleneck)

### Problem:

```java
dbManager.savePage()
```

* Blocking DB call inside worker thread

### Impact:

```text
Fetch → WAIT (DB) → next
```

* Entire pipeline slowed down

---

### Fix:

* Introduced **dbQueue**
* Separate **DB writer thread**
* Batched writes

---

##  2.7 DB Queue Backpressure (New Bottleneck Discovered)

### Problem:

```java
LinkedBlockingQueue<>(100)
offer(timeout = 2s)
```

### After improving fetch:

* DB queue filled quickly
* Threads blocked for 2 seconds

### Impact:

```text
Throughput collapse (30 pps → 2 pps)
```

---

### Fix:

* Increased queue size:

```java
100 → 1000
```

* Removed blocking:

```java
offer() instead of offer(timeout)
```

* Increased batch size:

```java
50 → 200
```

---

## 2.8 Pipeline Coupling

### Problem:

```text
Fetch → Parse → DB → Extract
(all in same thread)
```

### Impact:

* No parallelism across stages
* Slowest stage blocks everything

---

### Fix:

```text
Fetcher → Queue → Parser → Queue → DB
```

* Decoupled pipeline


##  2.10 Per-Host Queue Too Small

### Problem:

```java
LinkedBlockingQueue<>(50)
```

### Impact:

* URLs dropped early
* scheduler starvation

---

### Fix:

```java
50 → 200 (or higher)
```

---

#  3. Debugging Journey (Step-by-Step Evolution)

---

## Stage 1: Initial Observation

* Low throughput
* suspected fetcher

---

## Stage 2: Fetcher Fix

* Switched JSoup → OkHttp
* throughput improved slightly

---

## Stage 3: Scheduler Bottleneck Identified

* HOST_DELAY + activeHosts limiting parallelism

---

## Stage 4: Introduced Per-Host Concurrency

* semaphore added
* throughput improved (25 → 30 pps)

---

## Stage 5: Sudden Performance Collapse

* throughput dropped to 2 pps

---

## Stage 6: Root Cause Found

* DB queue blocking threads

---

## Stage 7: Pipeline Fix

* async DB
* batching
* non-blocking queue

---

## Stage 8: Advanced Optimization

* increased host concurrency
* reduced delay
* improved queue sizing

---

# 4. Key Learnings

---

##  1. Bottlenecks shift

Fixing one layer exposes another:

```text
Fetcher → Scheduler → DB → Pipeline
```

---

##  2. Throughput = weakest stage

```text
Pipeline speed = min(stage speeds)
```

---

##  3. Concurrency must match workload

* Network → needs high concurrency
* DB → needs batching

---

##  4. Scheduling matters as much as fetching

* Host distribution controls parallelism
* Not just number of threads

---

##  5. Async is not always better

* Over-async can hurt
* Controlled threading is often faster

---

#  6. Final Conclusion

Our crawler evolved from:

```text
Basic → Working → Optimized → Scalable
```

We solved:

* network inefficiency
* scheduling bottlenecks
* pipeline blocking
* DB backpressure
* concurrency limitations

---

---

