package com.turbotikects.turbotikectsserver.dto.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmResponse {
    private List<Choice> choices;

    public List<Choice> getChoices() { return choices; }
    public void setChoices(List<Choice> choices) { this.choices = choices; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private Message message;

        public Message getMessage() { return message; }
        public void setMessage(Message message) { this.message = message; }
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        private String content;
        private String role;

        public void setContent(String content) { this.content = content; }

        public void setRole(String role) { this.role = role; }
    }
}
