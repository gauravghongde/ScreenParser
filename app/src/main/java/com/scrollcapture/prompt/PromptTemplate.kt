package com.scrollcapture.prompt

enum class PromptTemplate(
    val emoji: String,
    val label: String,
    val instruction: String
) {
    QUICK_SUMMARY(
        emoji = "📝",
        label = "Quick Summary",
        instruction = "Summarize the following text in 2–3 concise sentences:"
    ),
    ELI5(
        emoji = "🧒",
        label = "ELI5",
        instruction = "Explain the following text like I'm 5 years old, using simple words and short sentences:"
    ),
    DETAILED_SUMMARY(
        emoji = "📋",
        label = "Detailed Summary",
        instruction = "Give a detailed summary (approximately 100 lines) of the following text, organized with key points and section headers:"
    ),
    CONVERSATION_RECAP(
        emoji = "💬",
        label = "Conversation Recap",
        instruction = "This is a chat conversation. Summarize who said what, the key decisions made, any action items or follow-ups, and the overall tone:"
    ),
    KEY_TAKEAWAYS(
        emoji = "🔍",
        label = "Key Takeaways",
        instruction = "List the top 10 most important points and takeaways from the following text:"
    ),
    CUSTOM(
        emoji = "✍️",
        label = "Custom",
        instruction = ""
    );
}
