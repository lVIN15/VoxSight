# VoxSight — Comprehensive Business Model & Commercialization Plan

**Project Title:** VoxSight: A Mobile Application for Independent Choral Sight-Reading Practice  
**Document Version:** 1.0  
**Target Audience:** Capstone Defense Panelists, Academic Evaluators, Seed Investors, and Project Stakeholders  
**Document Context:** Capstone and Research Project (IT332)  

---

## Table of Contents
1. [Executive Summary](#1-executive-summary)
2. [Market Analysis & Customer Segmentation](#2-market-analysis--customer-segmentation)
3. [Value Proposition & Competitive Moat](#3-value-proposition--competitive-moat)
4. [Packaging & Tiered Pricing Architecture](#4-packaging--tiered-pricing-architecture)
5. [Technical Cost Structure & Unit Economics](#5-technical-cost-structure--unit-economics)
6. [Go-To-Market (GTM) & Distribution Strategy](#6-go-to-market-gtm--distribution-strategy)
7. [Legal, Copyright & Risk Mitigation](#7-legal-copyright--risk-mitigation)
8. [Capstone Defense & Investor Pitch Blueprint](#8-capstone-defense--investor-pitch-blueprint)
9. [Financial Roadmap & Milestones (3-Year Horizon)](#9-financial-roadmap--milestones-3-year-horizon)

---

## 1. Executive Summary

### 1.1 The Vision
VoxSight is an integrated mobile and cloud-based software platform engineered to eliminate the **rehearsal bottleneck** in amateur and community choral ensembles. By merging **Optical Music Recognition (OMR)**, **SATB voice-part isolation**, **human-modeled reference tone synthesis**, and **real-time on-device pitch verification**, VoxSight empowers non-sight-reading choristers to master their vocal parts independently before stepping into group rehearsals.

### 1.2 The Core Business Problem
Traditional rehearsal pedagogy forces conductors to spend up to **75% of rehearsal time** mechanically "pounding out" notes section-by-section (Soprano, Alto, Tenor, Bass) on a piano. This listen-and-repeat rote instruction wastes collective rehearsal hours, leaves other sections idle, and stalls artistic interpretation. Outside rehearsal, singers lack tools that both ingest custom physical repertoire and provide real-time vocal feedback.

### 1.3 Strategic Business Opportunity
VoxSight capitalizes on a hybrid **B2B2C SaaS model**. Rather than incurring high individual customer acquisition costs (CAC), VoxSight acquires entire choirs through their conductors and music directors. When one director adopts VoxSight, **20 to 50 active singers** are simultaneously onboarded onto the platform, driving virality, high retention, and predictable recurring revenue.

```
       +-------------------------------------------------------------+
       |               Choir Director / Church Parish                |
       |        (Adopts VoxSight to save 50%+ Rehearsal Time)        |
       +------------------------------+------------------------------+
                                      |
                       Distributes Repertoire Link
                                      |
              +-----------------------+-----------------------+
              |                       |                       |
              v                       v                       v
     [Soprano Section]          [Alto Section]        [Tenor/Bass Section]
     (5-15 Choristers)        (5-15 Choristers)        (5-15 Choristers)
              |                       |                       |
              +-----------------------+-----------------------+
                                      |
                      Individual Pro Upgrades & Word-
                       of-Mouth to Secondary Ensembles
```

---

## 2. Market Analysis & Customer Segmentation

### 2.1 Target Personas

| Persona Category | Primary Stakeholder | Core Pain Points | Willingness to Pay & Decision Factors |
| :--- | :--- | :--- | :--- |
| **B2C: Individual Amateur Chorister** | Community choir members, church choir volunteers, student singers. | Anxious about singing wrong notes in front of peers; cannot sight-read; no piano at home; guide recordings lack pitch validation. | **Moderate-Low individually:** Price-sensitive; prefers micro-subscriptions (cost of a cup of coffee) or freemium tiers. |
| **B2B: Choir Director / Conductor** | Parish music coordinators, community choir artistic directors. | Exhausted from note-teaching; rehearsals feel like drills rather than musical art; high member absenteeism and slow repertoire turnover. | **High:** Strongly incentivized to allocate choir/ensemble operational funds to speed up concert readiness. |
| **B2B: Religious & Civic Organizations** | Church councils, parish worship ministries, civic cultural groups. | Fragmented music distribution; budget spent on sheet music printing that members cannot read. | **High:** Annual operating budget allocated for liturgical and music resources. |
| **B2B: Academic & Music Schools** | High school choral departments, university chorales, private academies. | Difficult to grade or monitor at-home sight-singing practice; closed tools (SmartMusic) forbid custom choir repertoire. | **High:** Enterprise/academic budget cycles; requires roster management and student practice metrics. |

### 2.2 Market Sizing (TAM, SAM, SOM)

* **Total Addressable Market (TAM):** Global amateur choral market (~54.5 million choral singers in North America and Europe alone, plus an estimated 100,000+ church and community choirs across Southeast Asia including the Philippines).
* **Serviceable Available Market (SAM):** Tech-enabled amateur and community choirs with mobile access practicing Western polyphonic or SATB liturgical choral music (~1.2 million active choirs globally).
* **Serviceable Obtainable Market (SOM - Years 1–3):** Church choirs, community chorales, and school vocal programs in the domestic market (Philippines: ~15,000 active church/school choirs) expanding into regional Southeast Asian and online choral director networks (Target: 350 active ensemble subscriptions by Year 2).

---

## 3. Value Proposition & Competitive Moat

### 3.1 Competitive Landscape Matrix

| Feature / Capability | Generic Sheet Music Scanners *(e.g., What's My Note, Sheet Music Scanner)* | Academic Assessment Tools *(e.g., SmartMusic / MakeMusic Cloud)* | Solo Vocal Pitch Tuners *(e.g., Yousician, Vanido, EFun)* | **VoxSight** |
| :--- | :---: | :---: | :---: | :---: |
| **Custom Sheet Music Ingestion (OMR)** | Yes (Standard) | **No** (Strict walled-garden library) | **No** (Preset pop vocal exercises) | **Yes** (Audiveris-powered SATB OMR) |
| **SATB Polyphonic Part Separation** | Partial (MIDI channels) | Yes (Catalog only) | **No** (Monophonic only) | **Yes** (Automated Soprano/Alto/Tenor/Bass isolation) |
| **Audio-Visual Selective Dimming** | **No** | Limited | **No** | **Yes** (Assigned part 100%, background lines &le;20%) |
| **Human-Tone Vocal Synthesis** | **No** (Harsh piano MIDI) | Yes (Sampled instruments) | Synthesized vocals | **Yes** (Sustained formant reference tones) |
| **Real-Time Mic Pitch Evaluation** | **No** (Audio output only) | Yes | Yes | **Yes** (On-device latency &le;0.5s, color feedback) |
| **Affordability for Amateur Ensembles** | Low one-time cost | Expensive school license (\$40+/student) | \$10–\$15 / user / month | **Reasonable Tiered Micro-Pricing** |

### 3.2 Key Competitive Moats
1. **The Custom Score Ingestion Advantage:** While major platforms restrict users to licensed song libraries, VoxSight lets any local choir sing their own customized arrangements, regional hymns, or self-printed scores.
2. **Pedagogical Alignment:** Designed explicitly around vocal pedagogy literature (Frampton, 2010; Mishra, 2014) emphasizing continuous sustained tones over percussive piano hammer attacks.
3. **Edge Processing Architecture:** Zero-latency, zero-server-cost microphone pitch detection done entirely in mobile Kotlin/C++ algorithms, protecting user privacy and slashing cloud infrastructure expenses.

---

## 4. Packaging & Tiered Pricing Architecture

To ensure financial accessibility for local amateur and church choirs (in Philippine Pesos - PHP) while remaining globally viable in US Dollars (USD), VoxSight employs a **3-tier hybrid monetization model**.

### 4.1 Tier Breakdown

```
+--------------------------------------------------------------------------------------------------+
|                                    VOXSIGHT PACKAGING TIERS                                      |
+------------------------------------+-----------------------------+-------------------------------+
| Tier 1: Free Community             | Tier 2: Chorister Pro       | Tier 3: Ensemble Master Pass  |
| "The Accessible Essential"         | "The Dedicated Vocalist"    | "The Complete Choir Solution" |
+------------------------------------+-----------------------------+-------------------------------+
| • 2 OMR score scans per month      | • Unlimited OMR scans       | • 1 Director + up to 35 choir |
| • Basic SATB visual dimming        | • Real-time pitch feedback  |   members included            |
| • Vocal synthesis playback         | • Accuracy scorecards & logs| • 1-Click repertoire push to  |
| • Single-part practice             | • Transpose & tempo control |   all member devices          |
| • Ad-supported banner units        | • Offline practice mode     | • Sectional practice metrics  |
|                                    | • Ad-free experience        | • Priority OMR cloud queue    |
+------------------------------------+-----------------------------+-------------------------------+
| FREE FOREVER                       | ₱149 / month ($2.99 / mo)   | ₱999 / month ($19.99 / mo)    |
|                                    | ₱1,199 / yr ($24.99 / yr)   | ₱7,999 / yr ($159.00 / yr)    |
|                                    |                             | *(Equals ~₱28 or $0.57 per    |
|                                    |                             |   member per month!)*         |
+------------------------------------+-----------------------------+-------------------------------+
```

### 4.2 Why This Pricing Is "Reasonable" and Defensible

1. **For the Individual Singer (₱149 / \$2.99 per month):**
   * Priced below a standard single fast-food meal or coffee. 
   * Provides immediate personal value for singers preparing for solos, auditions, or high-stakes choral performances without requiring approval from choir leadership.
2. **For the Ensemble (₱999 / \$19.99 per month for 35 members):**
   * If an ensemble pools money or charges its 30 members nominal rehearsal dues, it costs each singer **less than ₱35 (\$0.70) per month**.
   * Compare this with hiring an accompanist or sectional vocal coach for rehearsals, which commonly costs **₱1,500 – ₱3,000 (\$50 – \$100) per single rehearsal**. VoxSight pays for itself in less than one session.
3. **For the Academic / High School Segment:**
   * An annual institutional tier of **₱15,000 / year (\$299 / year)** per choral department covers up to 100 students, including teacher dashboard grade exports.

---

## 5. Technical Cost Structure & Unit Economics

A key test of commercial viability during a capstone defense is showing that operational expenses do not outpace revenue as user numbers grow.

### 5.1 Infrastructure Cost Breakdown (Monthly)

| Expense Item | Provider / Specification | Estimated Cost (PHP) | Estimated Cost (USD) | Scaling Behavior |
| :--- | :--- | :--- | :--- | :--- |
| **Backend API & OMR Server** | Hetzner / DigitalOcean (4 vCPU, 8GB RAM, 160GB NVMe) | ₱1,400 / mo | \$25.00 / mo | Fixed cost up to ~1,000 daily active scans. |
| **Database & Object Storage** | PostgreSQL + AWS S3 / Cloudflare R2 (Score storage) | ₱560 / mo | \$10.00 / mo | Scales linearly with stored MusicXML & sheet scans (very low byte footprints). |
| **Pitch Detection Engine** | **Client-Side Mobile (Kotlin/NDK)** | **₱0.00** | **\$0.00** | **100% offloaded to user device.** Zero server cost. |
| **App Store Developer Fees** | Google Play Developer (\$25 one-time) | ₱120 / mo (amort.) | \$2.10 / mo | Fixed annual or one-time fee. |
| **Domain & SSL Maintenance** | Cloudflare / Namecheap | ₱85 / mo | \$1.50 / mo | Fixed cost. |
| **Total Base Monthly Infrastructure** | — | **~₱2,165 / month** | **~\$38.60 / month** | Covers early production operations. |

### 5.2 Unit Economics & Gross Margin

```
Revenue from 1 Ensemble Subscription:     ₱999.00 / month  ($19.99)
OMR Compute Cost (100 scans @ ~₱0.30):    -₱30.00 / month  (-$0.55)
Storage & Bandwidth allocation:            -₱15.00 / month  (-$0.25)
Payment Gateway Fee (3.5% + ₱15):          -₱50.00 / month  (-$0.90)
--------------------------------------------------------------------
Net Contribution Margin per Choir:        +₱904.00 / month  (+$18.29)
Gross Margin Percentage:                  ~90.5%
```

### 5.3 Breakeven Threshold
* **Breakeven Volume:** At an operational cost of ~₱2,165/month (\$38.60), VoxSight requires only **3 active Ensemble subscriptions** (or **15 individual Chorister Pro users**) to become completely self-funding.
* Every subscription beyond the 3rd directly funds ongoing development, support, and infrastructure scaling.

---

## 6. Go-To-Market (GTM) & Distribution Strategy

Consumer music apps typically suffer from steep Customer Acquisition Costs (CAC). VoxSight circumvents this using a **B2B2C Grassroots Choral Loop**.

### 6.1 The 4-Stage GTM Playbook

```
[ Stage 1: Local Church & Collegiate Beta ]
  • Seed 10 local church choirs & university chorales with free 1-season Ensemble Passes.
  • Gather qualitative feedback on rehearsal time saved and OMR accuracy.
                    |
                    v
[ Stage 2: Conductor Association Outreach ]
  • Demo at choral workshops (e.g., Philippine Choral Directors Association, liturgical music seminars).
  • Offer conductors "Free Personal Director Accounts" if they trial it with their choir.
                    |
                    v
[ Stage 3: The Multi-Choir Viral Multiplier ]
  • 40% of amateur singers sing in more than one choir (e.g., church choir + alumni ensemble).
  • Choristers introduce VoxSight to their secondary conductors, driving organic cross-pollination.
                    |
                    v
[ Stage 4: Freemium Community Growth ]
  • Free tier allows individual singers to scan 2 scores/month, creating an open gateway for 
    independent vocalists across the globe via organic App Store search (ASO).
```

### 6.2 Key Growth Metrics (KPIs)
* **Rehearsal Time Saved Ratio:** Target &ge;40% reduction in conductor note-pounding time (survey-validated).
* **Viral Coefficient (K-Factor):** Target K &ge; 1.3 (each adopting director brings an average of 25–35 active mobile users).
* **30-Day Repertoire Retention:** Percentage of choir members who practice the weekly score at least twice prior to rehearsal.

---

## 7. Legal, Copyright & Risk Mitigation

Panelists frequently scrutinize music software regarding copyright infringement for uploaded scores. VoxSight establishes strict legal and operational safeguards.

### 7.1 Copyright Defense (Private Cloud Locker Model)
* **The "Personal Scanning Utility" Principle:** VoxSight does not distribute, sell, or host a public catalog of copyrighted sheet music. It functions strictly as an individual, private digital conversion tool (comparable to a personal flatbed scanner or private cloud drive).
* **Private Sandboxed Storage:** User-uploaded scores and parsed MusicXML files are isolated within authenticated, private directories. They cannot be browsed, searched, or downloaded by third parties.
* **Fair Use & Educational Exemption:** Under Section 185 of the Philippine Intellectual Property Code (RA 8293) and US Title 17 &sect; 107, reproduction of copyrighted works by an individual exclusively for personal study, research, or private practice falls under fair dealing/fair use doctrine.
* **Terms of Service (ToS) Enforcement:** Users must agree during score upload that they hold a legal physical copy of the score or that the repertoire is in the public domain.

### 7.2 Technical & Input Failure Safeguards
* **OMR Fallback Handling:** If a user uploads a degraded or blurry score, the system enforces pre-validation checks (e.g., DPI check, contrast verification) to fail gracefully with actionable advice (*"Lighting too dark, please capture in bright light"*) rather than generating corrupt practice files.
* **Biometric Voice Privacy:** Audio recorded through the microphone for pitch verification is processed in volatile memory (RAM) and immediately discarded once frequency extraction is calculated. **No raw voice recordings are stored or transmitted to the cloud**, guaranteeing complete user privacy.

---

## 8. Capstone Defense & Investor Pitch Blueprint

When presenting VoxSight's business viability to panelists or investors, follow this structured pitch framework.

### 8.1 The 90-Second Elevator Pitch

> *"Good afternoon, members of the panel. Consider the typical 2-hour amateur choir rehearsal. Up to 75% of that rehearsal time is not spent making music—it is spent with the conductor sitting at a piano, mechanically pounding out the Soprano notes, then the Alto notes, then Tenor, then Bass. While one section struggles, the other three sections sit completely idle.*
> 
> *Amateur singers want to practice at home, but they face a technological dead-end: apps like SmartMusic provide pitch evaluation but strictly forbid uploading your own choir repertoire. Apps like What's My Note let you scan music, but only play mechanical piano MIDI with zero vocal feedback.*
> 
> *VoxSight bridges this critical gap. Our mobile app digitizes physical choral sheet music using Audiveris OMR, isolates SATB voice parts, dims non-assigned staves to reduce cognitive overload, and provides real-time microphone pitch feedback using synthesized vocal tones.*
> 
> *Best of all, our business model is sustainable: by targeting choir directors at an ensemble rate of just ₱999 ($19.99) per month for 35 singers—which works out to under ₱30 per singer per month—we achieve 90% software margins while saving choirs over 50% of their precious rehearsal time. With just 3 ensemble subscriptions, our entire cloud server infrastructure is fully funded. VoxSight transforms rehearsals from repetitive note-teaching drills into true musical art."*

### 8.2 Anticipated Defense Questions & Model Answers

```
+---------------------------------------------------------------------------------------------------+
| QUESTION 1: "Why would a church choir pay for this when they can just record the piano accompaniment|
|              on their phone and share it on Messenger?"                                            |
+---------------------------------------------------------------------------------------------------+
| ANSWER: "Audio recordings shared on Messenger provide only passive listening. Research in vocal   |
| pedagogy (Mishra, 2014; Hao & Simeon, 2023) proves that novice singers cannot accurately self-     |
| assess their pitch through passive listening alone—they unknowingly ingrain pitch errors into muscle|
| memory. VoxSight delivers active visuo-motor learning: as they sing, the app provides real-time,   |
| color-coded visual verification against the score. It replaces passive guessing with definitive,   |
| self-correcting feedback."                                                                        |
+---------------------------------------------------------------------------------------------------+

+---------------------------------------------------------------------------------------------------+
| QUESTION 2: "Audiveris OMR is computationally heavy. Won't your cloud hosting costs spiral out of  |
|              control if thousands of users upload music?"                                         |
+---------------------------------------------------------------------------------------------------+
| ANSWER: "No, due to two deliberate architectural decisions: First, pitch detection—which runs      |
| continuously throughout a practice session—is 100% computed on the mobile device (client-side),   |
| requiring zero server bandwidth or CPU. Second, sheet music is only processed through OMR once;   |
| in an ensemble of 35 members, the director uploads the score once and all 35 members download the |
| lightweight cached MusicXML representation. Our breakeven point is exceptionally low: just 3       |
| subscribing choirs cover our entire monthly server cost."                                         |
+---------------------------------------------------------------------------------------------------+

+---------------------------------------------------------------------------------------------------+
| QUESTION 3: "Isn't ₱149/month or ₱999/month too high for amateur or church volunteers?"           |
+---------------------------------------------------------------------------------------------------+
| ANSWER: "On an individual basis, ₱149 per month is less than the cost of a single fast-food meal   |
| or coffee. At the ensemble level, ₱999 divided across a 30-to-35 member choir amounts to less than |
| ₱30 per singer per month—a tiny fraction of typical monthly choir dues. When compared to hiring a   |
| vocal coach or rehearsal accompanist at ₱1,500 to ₱3,000 per rehearsal, VoxSight provides an      |
| overwhelming return on investment for the ensemble."                                              |
+---------------------------------------------------------------------------------------------------+
```

---

## 9. Financial Roadmap & Milestones (3-Year Horizon)

### 9.1 Projections Overview

```
                                 YEAR 1             YEAR 2             YEAR 3
                          (Local Validation)   (National Scale)   (Regional / Global)
Active Ensembles (B2B):           25                 120                450
Individual Pro Users (B2C):       150                750               3,200
Monthly Recurring Revenue:    ₱47,325 (~$845)    ₱231,630 (~$4,130)  ₱926,300 (~$16,540)
Annual Recurring Revenue:     ₱567,900 (~$10.1k) ₱2,779,560 (~$49.6k) ₱11,115,600 (~$198k)
Est. Annual Server Overhead:  ₱35,000 (~$625)    ₱110,000 (~$1,960)  ₱380,000 (~$6,785)
Estimated Gross Margin:             93.8%              96.0%              96.5%
```

### 9.2 Key Strategic Milestones

* **Milestone 1 (Months 1–6): Prototype Defense & Pilot Validation**
  * Complete Capstone Defense with working Android MVP and Spring Boot / Audiveris backend.
  * Onboard 5 pilot choirs (parish and university ensembles) under free trial licenses to measure rehearsal time reduction and compile user satisfaction case studies.
* **Milestone 2 (Months 7–18): Commercial Launch & Local Expansion**
  * Deploy automated payment gateway integration (GCash, Maya, Stripe/Credit Card).
  * Formal presentation and workshop sponsorship at the Philippine Choral Directors Association (PCDA) conventions.
  * Reach 50 paying ensembles and achieve operational self-sufficiency.
* **Milestone 3 (Months 19–36): Feature Scaling & Global Export**
  * Introduce iOS client application to address wider international demographic.
  * Launch web-based Director Portal with detailed section rehearsal analytics and attendance tracking.
  * Expand partnerships with international choral federations (e.g., American Choral Directors Association - ACDA, Europa Cantat).

---

## 10. Conclusion

VoxSight is not merely a technical novelty; it is a **commercially viable, pedagogical solution** to a universal problem in vocal music. By offering an affordable, accessible pricing structure tailored to the realities of community and church ensembles, paired with a scalable cloud architecture that minimizes edge compute costs, VoxSight possesses both the **academic rigor** demanded by capstone evaluators and the **market viability** required for a real-world software enterprise.
