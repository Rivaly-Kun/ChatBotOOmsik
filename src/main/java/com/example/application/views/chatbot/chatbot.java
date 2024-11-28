package com.example.application.views.chatbot;

import com.example.application.api.GoogleSearchResponse;
import com.example.application.entity.Medicine;
import com.example.application.repository.MedicineRepository;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@PageTitle("Chatbot")
@Route(value = "Chatbot") // Keep the "login" route for continuity
@PermitAll
public class chatbot extends VerticalLayout {
    private void updateGrid(Grid<Medicine> grid) {
        // Fetch the updated list of medicines from the repository and set them to the grid
        List<Medicine> updatedMedicines = medicineRepository.findAll();
        grid.setItems(updatedMedicines);
    }
    
    private final MedicineRepository medicineRepository;

    // Google API constants
    private static final String GOOGLE_SEARCH_API_KEY = "AIzaSyB4o_VOwtLO3Y8v9iKJiVLuMF6OJ4IUEHc";
    private static final String GOOGLE_SEARCH_CX = "707f38f4889f44545";

    @Autowired
    public chatbot(MedicineRepository medicineRepository) {
        this.medicineRepository = medicineRepository;

        setSpacing(false);

        // Fetch data from the database
        List<Medicine> medicines = medicineRepository.findAll();

        // Create a Vaadin Grid to display the data
        Grid<Medicine> grid = new Grid<>(Medicine.class, false);
        grid.addColumn(Medicine::getId).setHeader("ID");
        grid.addColumn(Medicine::getBrandName).setHeader("Brand Name");
        grid.addColumn(Medicine::getActiveIngredient).setHeader("Active Ingredient");
        grid.addColumn(Medicine::getPrice).setHeader("Price");

        // Set data to the grid
        grid.setItems(medicines);

        // Add the grid to the layout
        add(grid);

        // Add Chatbot functionality below the data display
        Div chatArea = new Div();
        chatArea.setHeight("300px");
        chatArea.setWidth("100%");
        chatArea.getStyle().set("overflow-y", "auto");
        chatArea.getStyle().set("border", "1px solid #ccc");
        chatArea.getStyle().set("padding", "10px");
        chatArea.getStyle().set("margin-top", "20px");

        TextField userInput = new TextField();
        userInput.setPlaceholder("Ask a medicine-related question...");

        Button sendButton = new Button("Send", event -> {
            String userMessage = userInput.getValue();
            if (!userMessage.trim().isEmpty()) {
                // Display user message
                Div userMessageDiv = new Div();
                userMessageDiv.setText("User: " + userMessage);
                chatArea.add(userMessageDiv); // Add user message to chat area
        
                userInput.clear();
        
                // Get chatbot response
                String botResponse = getChatbotResponse(userMessage, grid); // Pass the grid here
                Div botResponseDiv = new Div();
                botResponseDiv.setText("Chatbot: " + botResponse);
                chatArea.add(botResponseDiv); // Add chatbot response to chat area
        
                // Scroll to the bottom
                chatArea.getElement().executeJs("this.scrollTop = this.scrollHeight;");
            }
        });
        
        // Add chatbot components to layout
        add(chatArea, userInput, sendButton);

        setSizeFull();
    }
    private String searchGoogleForMedicine(String query) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl("https://www.googleapis.com/customsearch/v1")
                    .queryParam("key", GOOGLE_SEARCH_API_KEY)
                    .queryParam("cx", GOOGLE_SEARCH_CX)
                    .queryParam("q", query)
                    .toUriString();
    
            RestTemplate restTemplate = new RestTemplate();
            GoogleSearchResponse response = restTemplate.getForObject(url, GoogleSearchResponse.class);
    
            if (response != null && response.getItems() != null && !response.getItems().isEmpty()) {
                String[] keywords = query.toLowerCase().split("\\s+"); // Split query into keywords
                String primaryKeyword = keywords[0]; // First word as main keyword (e.g., "ibuprofen")
                List<String> snippets = new ArrayList<>();
    
                // Collect all snippets for logging/debugging
                for (GoogleSearchResponse.Item item : response.getItems()) {
                    snippets.add(item.getSnippet());
                }
                System.out.println("All Snippets: " + snippets);
    
                // Step 1: Directly relevant snippets (contain the primary keyword as the main subject)
                for (GoogleSearchResponse.Item item : response.getItems()) {
                    String snippet = item.getSnippet();
                    if (isKeywordInFocus(snippet, primaryKeyword)) {
                        System.out.println("Matched snippet (keyword in focus): " + snippet);
                        return formatSnippetWithUrl(snippet, item.getLink());
                    }
                }
    
                // Step 2: Broad keyword matches (snippet contains the primary keyword)
                for (GoogleSearchResponse.Item item : response.getItems()) {
                    String snippet = item.getSnippet().toLowerCase();
                    if (snippet.contains(primaryKeyword)) {
                        System.out.println("Fallback matched snippet (keyword found): " + snippet);
                        return formatSnippetWithUrl(item.getSnippet(), item.getLink());
                    }
                }
    
                // Step 3: Absolute fallback to the first snippet
                GoogleSearchResponse.Item firstItem = response.getItems().get(0);
                return formatSnippetWithUrl(firstItem.getSnippet(), firstItem.getLink());
            } else {
                return "I couldn't find an answer to your question. Please try rephrasing it.";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "There was an error fetching the answer. Please try again later.";
        }
    }
    
    private String formatSnippetWithUrl(String snippet, String url) {
        return snippet + "\n\n" + "URL:\n" + url;
    }
    
    
    
    private boolean isKeywordInFocus(String snippet, String keyword) {
        // Normalize strings to lowercase for comparison
        snippet = snippet.toLowerCase();
        keyword = keyword.toLowerCase();
    
        // Check if the snippet starts with or strongly emphasizes the keyword
        if (snippet.startsWith(keyword) || snippet.contains(keyword + " is") || snippet.contains(keyword + " are")) {
            return true;
        }
    
        // Ensure the keyword appears as the subject or main topic
        String[] focusIndicators = {"is a", "is an", "is used", "is often", "is typically", "is commonly"};
        for (String indicator : focusIndicators) {
            if (snippet.contains(keyword + " " + indicator)) {
                return true;
            }
        }
    
        return false;
    }
    
    
    
    
    
    private String getChatbotResponse(String query, Grid<Medicine> grid) {
        if (query.startsWith("+")) {
            // Add a new medicine
            return handleAddMedicine(query, grid);
        } else if (query.startsWith("~")) {
            // Update an existing medicine
            return handleUpdateMedicine(query, grid);
        } else if (query.startsWith("-")) {
            // Delete a medicine
            return handleDeleteMedicine(query, grid);
        }
    
        // Default behavior: search Google for the query
        return searchGoogleForMedicine(query);
    }
    
    
    private String handleAddMedicine(String query, Grid<Medicine> grid) {
        try {
            // Remove the "+" and trim the query
            String command = query.substring(1).trim();
    
            // Parse the new medicine details (format: "BrandName active=ActiveIngredient price=Price")
            String[] parts = command.split(" ");
            String brandName = parts[0].trim();
    
            String activeIngredient = null;
            Double price = null;
    
            for (String part : parts) {
                if (part.startsWith("active=")) {
                    activeIngredient = part.substring("active=".length()).trim();
                } else if (part.startsWith("price=")) {
                    price = Double.parseDouble(part.substring("price=".length()).trim());
                }
            }
    
            if (brandName.isEmpty() || activeIngredient == null || price == null) {
                return "Invalid add command. Use the format: + BrandName active=ActiveIngredient price=Price";
            }
    
            // Create and save the new medicine
            Medicine newMedicine = new Medicine();
            newMedicine.setBrandName(brandName);
            newMedicine.setActiveIngredient(activeIngredient);
            newMedicine.setPrice(price);
    
            medicineRepository.save(newMedicine);
    
            // Update the grid after adding the new medicine
            updateGrid(grid);
    
            return "New medicine '" + brandName + "' added successfully.";
        } catch (Exception e) {
            e.printStackTrace();
            return "An error occurred while adding the medicine.";
        }
    }
    
    
    private String handleUpdateMedicine(String query, Grid<Medicine> grid) {
        try {
            // Remove the "~" and trim the query
            String command = query.substring(1).trim();
    
            // Parse the command for update operations
            if (command.contains("brandname to")) {
                String[] parts = command.split("brandname to");
                String oldBrandName = parts[0].trim();
                String newBrandName = parts[1].trim();
    
                Medicine medicine = medicineRepository.findByBrandName(oldBrandName);
                if (medicine == null) {
                    return "Medicine with brand name '" + oldBrandName + "' not found.";
                }
    
                medicine.setBrandName(newBrandName);
                medicineRepository.save(medicine);
                updateGrid(grid);
                return "Brand name updated from '" + oldBrandName + "' to '" + newBrandName + "'.";
            } else if (command.contains("active to")) {
                String[] parts = command.split("active to");
                String brandName = parts[0].trim();
                String newActiveIngredient = parts[1].trim();
    
                Medicine medicine = medicineRepository.findByBrandName(brandName);
                if (medicine == null) {
                    return "Medicine with brand name '" + brandName + "' not found.";
                }
    
                medicine.setActiveIngredient(newActiveIngredient);
                medicineRepository.save(medicine);
                
                return "Active ingredient for '" + brandName + "' updated to '" + newActiveIngredient + "'.";
            }
    
            return "Invalid update command. Use the format: ~ BrandName brandname to NewBrandName or ~ BrandName active to NewActiveIngredient";
        } catch (Exception e) {
            e.printStackTrace();
            return "An error occurred while updating the medicine.";
        }
    }
    private String handleDeleteMedicine(String query, Grid<Medicine> grid) {
        try {
            // Remove the "-" and trim the query
            String brandName = query.substring(1).trim();
    
            // Find and delete the medicine
            Medicine medicine = medicineRepository.findByBrandName(brandName);
            if (medicine == null) {
                return "Medicine with brand name '" + brandName + "' not found.";
            }
    
            medicineRepository.delete(medicine);
    
            // Update the grid after deleting the medicine
            updateGrid(grid);
    
            return "Medicine with brand name '" + brandName + "' deleted successfully.";
        } catch (Exception e) {
            e.printStackTrace();
            return "An error occurred while deleting the medicine.";
        }
    }
    
    
    
}
