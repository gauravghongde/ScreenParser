package com.scrollcapture.prompt

object PromptBuilder {

    /**
     * Build a complete AI prompt by combining the template instruction with captured text.
     */
    fun buildPrompt(
        template: PromptTemplate,
        customInstruction: String? = null,
        capturedText: String
    ): String {
        val instruction = when (template) {
            PromptTemplate.CUSTOM -> customInstruction?.trim() ?: "Analyze the following text:"
            else -> template.instruction
        }

        return buildString {
            appendLine(instruction)
            appendLine()
            appendLine("---")
            appendLine()
            append(capturedText)
        }
    }

    /**
     * Get a short preview of the prompt (first N characters).
     */
    fun buildPromptPreview(
        template: PromptTemplate,
        customInstruction: String? = null,
        capturedText: String,
        maxLength: Int = 500
    ): String {
        val full = buildPrompt(template, customInstruction, capturedText)
        return if (full.length <= maxLength) full
        else full.take(maxLength) + "…"
    }
}
