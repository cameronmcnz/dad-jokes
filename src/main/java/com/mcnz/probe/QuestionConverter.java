package com.mcnz.probe;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class QuestionConverter {

    static class Option {
        public String text;
        public boolean correct;
        public boolean selected = false;

        public Option(String text, boolean correct) {
            this.text = text;
            this.correct = correct;
        }
    }

    static class ConvertedQuestion {
        public int id;
        public String query;
        public String answer;
        public int marked = 0;
        public int timespent = 0;
        public List<Option> options;
        public List<String> objectives = new ArrayList<>();

        public ConvertedQuestion(int id, String query, String answer, List<Option> options) {
            this.id = id;
            this.query = query;
            this.answer = answer;
            this.options = options;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class InputQuestion {
        public String answer;
        public List<String> originalOptions;
        public String originalQuery;
        public String originalExplanation;
    }

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // Read input JSON (replace with your actual file path)
        List<InputQuestion> inputQuestions = mapper.readValue(
                new File("questions-input.json"),
                new TypeReference<List<InputQuestion>>() {}
        );

        List<ConvertedQuestion> convertedList = new ArrayList<>();
        int idCounter = 1;

        for (InputQuestion input : inputQuestions) {
            Set<String> correctLetters = new HashSet<>(Arrays.asList(input.answer.replace(",", " ").split("\\s+")));
            List<Option> convertedOptions = new ArrayList<>();

            for (String opt : input.originalOptions) {
                String[] parts = opt.split("\\. ", 2);
                if (parts.length < 2) continue;

                String letter = parts[0].trim().toUpperCase();
                String text = parts[1].trim();

                boolean isCorrect = correctLetters.contains(letter);
                convertedOptions.add(new Option(text, isCorrect));
            }

            ConvertedQuestion converted = new ConvertedQuestion(
                    idCounter++,
                    input.originalQuery,
                    input.originalExplanation,
                    convertedOptions
            );
            convertedList.add(converted);
        }

        // Write output to JSON file
        mapper.writerWithDefaultPrettyPrinter().writeValue(
                new File("questions-converted.json"),
                convertedList
        );

        System.out.println("Conversion complete. Output written to questions-converted.json");
    }
}
