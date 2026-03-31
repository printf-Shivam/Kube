# Kube Search Engine

A custom-built, full-stack search engine featuring a multi-threaded web crawler, a custom TF-IDF ranking algorithm, and a modern React web interface. 

## ✨ Features

* **Multi-Threaded Web Crawler:** High-performance, concurrent crawling using Java's `ExecutorService`.
* **Smart Parsing:** Utilizes `Jsoup` for standard HTML parsing, with a fallback to `Playwright` (Chromium) to accurately render and extract content from JavaScript-heavy Single Page Applications (SPAs).
* **Custom Ranking Engine:** Implements TF-IDF (Term Frequency-Inverse Document Frequency) from scratch to score and rank search results based on relevance.
* **Persistent Storage:** Lightweight, fast data storage using SQLite with Write-Ahead Logging (WAL) for safe, concurrent database writes.
* **Modern Web UI:** A lightning-fast, responsive frontend built with React, Vite, and Tailwind CSS v4.

## 🛠️ Tech Stack

**Backend (Spring Boot API & Crawler)**
* Java 17+
* Spring Boot (Web)
* SQLite (JDBC)
* Jsoup (HTML Parsing)
* Microsoft Playwright (Headless Browser rendering)
* Maven

**Frontend (User Interface)**
* React.js
* Vite
* Tailwind CSS v4

---

## 🚀 Getting Started

### Prerequisites
* Java Development Kit (JDK) 17 or higher
* Node.js & npm
* Maven

### 1. Backend Setup (Spring Boot)
1. Clone the repository:
   `git clone https://github.com/printf-Shivam/Kube.git`
   `cd Kube`
2. Download the required dependencies and install Playwright browsers:
   `mvn clean install`
   `mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install"`
3. Start the Spring Boot server:
   `mvn spring-boot:run`
   *(The backend REST API will start on http://localhost:8080)*

### 2. Frontend Setup (React/Vite)
1. Open a new terminal and navigate to the frontend directory:
   `cd frontend`
2. Install the Node dependencies:
   `npm install`
3. Start the Vite development server:
   `npm run dev`
   *(The UI will be available at http://localhost:5173)*

---

## 🧠 How It Works

### 1. The Crawling Phase
The `CrawlerEngine` is initialized with a list of seed URLs. It dispatches multiple threads to visit these pages. It respects `robots.txt` rules and stores visited URLs in a memory-efficient frontier. If a page blocks basic scraping, the engine seamlessly switches to a Chromium browser instance via Playwright to bypass the block and render the DOM.

### 2. The Indexing Phase
HTML content is stripped of tags and normalized. Words are processed (stop-word removal, stemming) and stored in an inverted index within the SQLite database.

### 3. The Search Phase
When a user enters a query in the React UI, the Spring Boot API receives the request. The `QueryEngine` calculates the TF-IDF scores for the query terms against the indexed documents, sorts them by relevance, and returns the top results as a JSON response to the frontend.

---
