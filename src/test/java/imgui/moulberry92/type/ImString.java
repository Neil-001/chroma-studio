package imgui.moulberry92.type;

public final class ImString {
    private String text;

    public ImString(String text, int length) { this.text = text; }

    public String get() { return text; }

    public void set(String text) { this.text = text; }

    public void set(Object text) { this.text = String.valueOf(text); }
}
