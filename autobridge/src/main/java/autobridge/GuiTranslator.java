package autobridge;

import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.Form;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GuiTranslator — Converts complex mod GUIs into Bedrock Cumulus forms.
 * 
 * When a Java player opens a mod GUI (like AE2's ME Terminal), this module
 * intercepts the interaction and presents an equivalent interface to Bedrock
 * players using Geyser's Cumulus form system.
 * 
 * This is the most challenging part of the translation pipeline because:
 * - Mod GUIs can have dynamic content (hundreds of items in slots)
 * - Bedrock forms have limitations (max buttons, no custom layouts)
 * - Complex interactions (drag-and-drop, shift-click) need workarounds
 * 
 * Strategy: Generate simplified but functional forms that capture the core
 * functionality of each GUI type. Start with read-only views, then add
 * write support iteratively.
 */
public class GuiTranslator {

    private static final Logger LOGGER = LoggerFactory.getLogger("GuiTranslator");
    
    // Bedrock form limitations
    private static final int MAX_FORM_BUTTONS = 15; // Practical limit for usability
    private static final int MAX_FORM_ELEMENTS = 20; // Total interactive elements

    private final AutoBridge bridge;

    public GuiTranslator(AutoBridge bridge) {
        this.bridge = bridge;
    }

    /**
     * Translate a mod GUI opening event into a Bedrock-compatible form.
     * Called when a Bedrock player interacts with a block that has a custom GUI.
     * 
     * @param playerId The Bedrock player's connection ID
     * @param guiType The type of GUI being opened (e.g., "me_terminal", "crafting_cpu")
     * @param context Additional context about the GUI state
     * @return The generated Form object, or null if translation isn't possible
     */
    public Form translateGui(int playerId, String guiType, GuiContext context) {
        LOGGER.debug("AutoBridge/GUI: Translating {} GUI for player {}", guiType, playerId);
        
        return switch (guiType.toLowerCase()) {
            case "me_terminal" -> translateMeTerminal(playerId, context);
            case "crafting_cpu" -> translateCraftingCpu(playerId, context);
            case "pattern_encoder" -> translatePatternEncoder(playerId, context);
            default -> generateGenericInventoryGui(playerId, guiType, context);
        };
    }

    /**
     * Translate AE2's ME Terminal into a Bedrock CustomForm.
     * 
     * AE2 Terminal features:
     * - Search bar
     * - Item grid (up to 27 columns × rows depending on upgrade)
     * - Storage details panel
     * - Crafting options
     * 
     * Bedrock translation strategy:
     * - Use a CustomForm with dropdown menus for navigation
     * - Paginate item lists (15 items per page max due to form limits)
     * - Provide quick-access buttons for common actions
     */
    private Form translateMeTerminal(int playerId, GuiContext context) {
        // Build pagination-aware form
        int currentPage = context.pageOrDefault(0);
        int pageSize = 10; // Conservative for form button limits
        
        // Get items for current page from context
        var items = context.items();
        int totalPages = (int) Math.ceil((double) items.size() / pageSize);
        
        CustomForm.Builder builder = CustomForm.builder()
            .title("ME Terminal")
            .label("Page " + (currentPage + 1) + " of " + totalPages);
        
        // Add navigation buttons
        int btnIndex = 0;
        
        if (currentPage > 0) {
            builder.button("< Previous Page");
            btnIndex++;
        }
        
        // Add item buttons (up to remaining capacity)
        int remainingCapacity = MAX_FORM_BUTTONS - btnIndex - 1; // Leave room for next page button
        int startIndex = currentPage * pageSize;
        int endIndex = Math.min(startIndex + remainingCapacity, items.size());
        
        for (int i = startIndex; i < endIndex && btnIndex < MAX_FORM_BUTTONS; i++) {
            var item = items.get(i);
            builder.button(item.name());
            btnIndex++;
        }
        
        if (currentPage < totalPages - 1) {
            builder.button("Next Page >");
        }
        
        Form form = builder.build();
        LOGGER.debug("AutoBridge/GUI: Generated ME Terminal form (page {}/{}, {} items)", 
            currentPage + 1, totalPages, endIndex - startIndex);
        
        return form;
    }

    /**
     * Translate AE2's Crafting CPU interface.
     * 
     * Features:
     * - Recipe list
     * - Crafting queue
     * - Priority settings
     * 
     * Translation: Simplified form with recipe selection and quantity input.
     */
    private Form translateCraftingCpu(int playerId, GuiContext context) {
        CustomForm.Builder builder = CustomForm.builder()
            .title("Crafting CPU")
            .label("Available Recipes: " + context.items().size());
        
        int btnIndex = 0;
        for (var item : context.items()) {
            if (btnIndex >= MAX_FORM_BUTTONS) break;
            builder.button(item.name());
            btnIndex++;
        }
        
        if (btnIndex == 0) {
            builder.label("No recipes available");
        }
        
        return builder.build();
    }

    /**
     * Translate AE2's Pattern Encoder.
     * Allows converting items into crafting patterns.
     */
    private Form translatePatternEncoder(int playerId, GuiContext context) {
        CustomForm.Builder builder = CustomForm.builder()
            .title("Pattern Encoder")
            .label("Encode crafting patterns");
        
        // Input field for pattern name
        builder.input("Pattern Name", "Enter pattern name");
        
        // Display available items
        for (var item : context.items()) {
            if (builder.elements().size() >= MAX_FORM_ELEMENTS) break;
            builder.button(item.name());
        }
        
        return builder.build();
    }

    /**
     * Generic fallback for unknown GUI types.
     * Creates a simple inventory-style form.
     */
    private Form generateGenericInventoryGui(int playerId, String guiType, GuiContext context) {
        CustomForm.Builder builder = CustomForm.builder()
            .title(guiType)
            .label("Mod Interface");
        
        int btnIndex = 0;
        for (var item : context.items()) {
            if (btnIndex >= MAX_FORM_BUTTONS) break;
            builder.button(item.name());
            btnIndex++;
        }
        
        if (btnIndex == 0) {
            builder.label("Empty interface");
        }
        
        return builder.build();
    }

    /**
     * Context object carrying GUI state data from the Java side.
     * Populated by the ModScanner when it detects GUI-related blocks/items.
     */
    public record GuiContext(
        String guiType,
        java.util.List<GuiItem> items,
        int currentPage,
        int totalPages
    ) {
        public int pageOrDefault(int def) {
            return currentPage <= 0 ? def : currentPage;
        }

        /**
         * A single item within a GUI context.
         */
        public record GuiItem(
            String name,
            String texturePath,
            boolean clickable,
            java.util.Map<String, Object> metadata
        ) {
            public GuiItem(String name, String texturePath) {
                this(name, texturePath, true, java.util.Map.of());
            }
        }
    }
}
