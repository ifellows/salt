---
title: System Messages
description: Customising the ineligibility message and other participant-facing system text, with multi-language audio support.
---

System messages are participant-facing texts that SALT displays at fixed points in the interview workflow. They are configured per survey so you can tailor them to your study population and local language.

![System Messages tab showing ineligibility message editor](../../../assets/screenshots/admin-surveys-system-messages.png)

## Available message types

| Message key | When it is shown |
|-------------|-----------------|
| **Ineligibility Message** | Shown to participants who do not meet the eligibility criteria defined in the Eligibility Script |

Additional system message keys may be added in future versions.

## Editing a message

1. Open the survey editor and click the **System Messages** tab
2. Select the message you want to edit
3. Type the message text in the text area
4. Click **Record Audio** to record an audio version of the message in the browser
5. Save the message

## Multi-language messages

If your survey has multiple languages configured (see [Languages](/management/languages/)), each language has its own text field and **Record Audio** button on the System Messages tab. Participants hear the audio for the language selected on their tablet.

## Audio playback

On the tablet, system messages with recorded audio play automatically when the relevant screen appears. A **Replay** button allows staff to play the audio again if needed.

## Relationship to eligibility

The Ineligibility Message is displayed when the **Eligibility Script** (on the Eligibility tab) evaluates to `false`. See [Eligibility](/management/eligibility/) and [Survey Logic](/management/survey-logic/) for details on writing eligibility conditions.
