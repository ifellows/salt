---
title: Glossary
description: Definitions of key terms used in SALT documentation.
---

## ACASI

**Audio Computer-Assisted Self-Interview.** A method of administering surveys where the participant listens to audio recordings of questions and enters responses directly on a device, without a human interviewer reading the questions. ACASI reduces interviewer bias and improves honest reporting on sensitive topics.

## API key

A secret token used by a tablet to authenticate itself to the management server. Each facility has a unique API key. API keys are generated automatically when a facility is created and are distributed to tablets during the setup process.

## BBS

**Bio-Behavioral Survey.** A periodic survey combining biological testing (e.g. HIV, syphilis) with behavioural data collection in key populations. Traditional BBS surveys use RDS and are conducted every 2–5 years. SALT is designed to replace or complement BBS surveys with continuous data collection.

## Coupon

A physical card or code given to each enrolled participant to distribute to members of their social network. When a network member presents a coupon at the facility, it starts a new interview and records the recruitment link. Coupons are the mechanism by which SALT traces social network chains.

## JEXL

**JavaScript Expression Language.** The expression language used for skip logic, validation scripts, skip-to expressions, and eligibility scripts in SALT survey configuration. JEXL expressions are short conditions such as `age >= 18` or `sex == 1 && age >= 18`. Full reference: <https://commons.apache.org/proper/commons-jexl/reference/syntax.html>.

## Key population (KP)

Groups that are disproportionately affected by HIV and that SALT surveys are designed to monitor, including men who have sex with men (MSM), people who inject drugs (PWID), sex workers (SW), and transgender women (TGW).

## Link-tracing survey

A survey design in which each participant recruits members of their social network, creating traceable chains of referral. Link-tracing designs include Respondent-Driven Sampling (RDS) and variants such as SALT's continuous adaptation.

## RDS

**Respondent-Driven Sampling.** A peer-referral sampling method in which seeds recruit peers using coupons, those peers recruit further peers, and so on. RDS estimators weight responses by network degree to produce population-level estimates. SALT uses coupon-based recruitment compatible with RDS and applies RDS estimators (RDS-II, RDS-A) to rolling time windows of data.

## Re-enrollment period

The number of days after a participant's initial enrolment during which the fingerprint scanner will flag them as a duplicate if they attempt to enrol again. After this period, the same person may be enrolled again. Configured per survey in the management server.

## SALT

**System Assisted Link Tracing.** The platform described in this documentation. SALT implements a continuous, facility-based link-tracing survey design supported by an Android tablet app and a web-based management server.

## Seed participant

A participant who is enrolled without presenting a coupon from a previous participant. Seeds are used to start new recruitment chains when the existing chains have exhausted their referrals. Whether seeds are accepted is configured per facility.

## Short Name

The unique identifier for a survey question, used in skip logic expressions and as the column name in data exports. Short Names are case-sensitive. Examples: `age`, `sex`, `hivtest`. The Short Name `value` is reserved for use in validation scripts.

## SQLCipher

An open-source extension to SQLite that provides transparent 256-bit AES encryption of the entire database file. SALT uses SQLCipher to encrypt the tablet's local database, protecting participant data if the device is lost or stolen.

## Survey Staff (SURVEY_STAFF)

A tablet user role that grants access to the survey interview workflow (Start New Survey, Recruitment Payment) but not to administrative functions. Survey data collection must be done by SURVEY_STAFF users; administrators cannot conduct surveys directly.
