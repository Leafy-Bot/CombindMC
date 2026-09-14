package autobridge;

import java.util.List;
import java.util.Map;

/**
 * GuiTranslator — Generates form descriptions for mod GUIs.
 *
 * In production (inside Geyser), these would be sent as Cumulus CustomForms.
 * For testing, this produces a serializable description that can be verified.
 */
public class GuiTranslator {

    private static final int MAX_FORM_BUTTONS = 15;

    /**
     * Generate a form description for a mod GUI.
     */
    public FormDescription translateGui(String guiType, GuiContext context) {
        return switch (guiType.toLowerCase()) {
            case "me_terminal" -> buildPaginatedForm("ME Terminal", context);
            case "crafting_cpu" -> buildSimpleListForm("Crafting CPU", context);
            case "pattern_encoder" -> buildInputForm("Pattern Encoder", context);
            default -> buildSimpleListForm(guiType, context);
        };
    }

    private FormDescription buildPaginatedForm(String title, GuiContext context) {
        int page = context.page();
        int pageSize = 10;
        int totalPages = Math.max(1, (int) Math.ceil((double) context.items().size() / pageSize));

        FormDescription form = new FormDescription(title);
        form.addLabel("Page " + (page + 1) + " of " + totalPages);

        int start = page * pageSize;
        int end = Math.min(start + MAX_FORM_BUTTONS - 2, context.items().size());

        if (page > 0) form.addButton("< Previous Page");
        for (int i = start; i < end; i++) {
            form.addButton(context.items().get(i).name());
        }
        if (page < totalPages - 1) form.addButton("Next Page >");

        return form;
    }

    private FormDescription buildSimpleListForm(String title, GuiContext context) {
        FormDescription form = new FormDescription(title);
        int count = 0;
        for (GuiItem item : context.items()) {
            if (count >= MAX_FORM_BUTTONS) break;
            form.addButton(item.name());
            count++;
        }
        if (count == 0) form.addLabel("Empty");
        return form;
    }

    private FormDescription buildInputForm(String title, GuiContext context) {
        FormDescription form = new FormDescription(title);
        form.addInput("Pattern Name", "Enter pattern name");
        int count = 0;
        for (GuiItem item : context.items()) {
            if (count >= MAX_FORM_BUTTONS - 1) break;
            form.addButton(item.name());
            count++;
        }
        return form;
    }

    // ==================== Data classes ====================

    public record GuiContext(String guiType, List<GuiItem> items, int page) {
        public GuiContext(String guiType, List<GuiItem> items) { this(guiType, items, 0); }
    }

    public record GuiItem(String name, String texturePath) {
        public GuiItem(String name) { this(name, ""); }
    }

    /**
     * Serializable form description for testing.
     */
    public static class FormDescription {
        private final String title;
        private final java.util.List<String> elements = new java.util.ArrayList<>();

        public FormDescription(String title) { this.title = title; }
        public void addLabel(String text) { elements.add("label:" + text); }
        public void addButton(String text) { elements.add("button:" + text); }
        public void addInput(String label, String placeholder) { elements.add("input:" + label + "|" + placeholder); }

        public String title() { return title; }
        public java.util.List<String> elements() { return java.util.List.copyOf(elements); }

        @Override
        public String toString() {
            return "Form[" + title + ", elements=" + elements.size() + "]";
        }
    }
}
